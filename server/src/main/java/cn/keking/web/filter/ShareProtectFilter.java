package cn.keking.web.filter;

import cn.keking.config.ConfigConstants;
import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.ShareService;
import cn.keking.utils.KkFileUtils;
import cn.keking.utils.WebUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 带密码分享的预览防护过滤器
 * 背景：分享密码原本仅由 {@link ShareAccessFilter} 拦截 /share/{token} 入口，
 * 若访客绕过分享页、直接拿到文件原始 URL 访问 /onlinePreview?url=...，则可完全绕过密码。
 * 本过滤器拦截 /onlinePreview 与 /picturesPreview：当被访问的文件 URL 存在
 * "设置了密码且未过期"的分享链接、且当前 session 未通过该链接的密码验证
 * （session 属性 "share_verified_" + token，与 ShareController 写入的键一致）时，
 * 重定向到 /share-page/{token} 密码验证页补齐验证。
 * 注意：由 WebConfig 以 FilterRegistrationBean 注册（不加 @Component，避免默认过滤器链重复注册）。
 * 整体 try/catch 兜底放行：防护逻辑自身失败时不能阻断正常预览
 *
 * @author Claude Code
 */
public class ShareProtectFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareProtectFilter.class);

    private final ShareService shareService;

    public ShareProtectFilter(ShareService shareService) {
        this.shareService = shareService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 协作分享开关关闭时不做防护，保持关闭状态下的既有行为
            if (ConfigConstants.isCollaborationShareEnabled()) {
                String token = resolveRequiredToken(httpRequest);
                if (token != null) {
                    httpResponse.sendRedirect(httpRequest.getContextPath() + "/share-page/" + token);
                    return;
                }
            }
        } catch (Exception e) {
            // 防护失败兜底放行，不能因本过滤器异常阻断正常预览
            LOGGER.warn("分享预览防护过滤异常，兜底放行: {}", e.getMessage());
        }
        chain.doFilter(request, response);
    }

    /**
     * 判断当前请求是否需要跳转密码验证页，需要则返回分享 token，否则返回 null（放行）
     */
    private String resolveRequiredToken(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String rawUrl;
        // encryption 参数来源与 OnlinePreviewController 一致（均可选 query 参数）
        String encryption = request.getParameter("encryption");
        String fileUrl;
        if ("/picturesPreview".equals(path)) {
            // 镜像 OnlinePreviewController#picturesPreview 的解码方式：decodeUrl 后做 HTML 转义
            rawUrl = request.getParameter("urls");
            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                return null;
            }
            fileUrl = KkFileUtils.htmlEscape(WebUtils.decodeUrl(rawUrl, encryption));
        } else {
            // 镜像 OnlinePreviewController#onlinePreview 的解码方式
            rawUrl = request.getParameter("url");
            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                return null;
            }
            fileUrl = WebUtils.decodeUrl(rawUrl, encryption);
        }

        ShareLink link = shareService.findProtectedShareLink(fileUrl);
        if (link == null) {
            // 该文件不存在带密码的分享链接，无需防护
            return null;
        }
        // 校验 session 中的验证标记（键与 ShareController#verifyPassword 写入的一致）
        String verified = (String) request.getSession().getAttribute("share_verified_" + link.getToken());
        if ("true".equals(verified)) {
            return null;
        }
        return link.getToken();
    }
}
