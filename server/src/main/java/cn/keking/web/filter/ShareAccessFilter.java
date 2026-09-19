package cn.keking.web.filter;

import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.ShareService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 分享访问过滤器
 * 拦截 /share/{token} 路径，校验分享链接的有效性：
 * - 链接不存在或已过期 → 404
 * - 带密码且未验证 → 重定向到 /share-page/{token} 密码页
 *   （不用 forward：forward 会导致页面相对路径 404 且绕过过滤器链）
 * - 放行 → 重定向到 /onlinePreview?url=...
 *
 * @author Claude Code
 */
public class ShareAccessFilter implements Filter {

    private final ShareService shareService;

    public ShareAccessFilter(ShareService shareService) {
        this.shareService = shareService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();

        // 只处理 /share/{token} 路径
        if (!requestUri.startsWith(contextPath + "/share/")) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(requestUri, contextPath + "/share/");
        if (token == null || token.trim().isEmpty()) {
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "分享令牌不能为空");
            return;
        }

        ShareLink link = shareService.getShareLink(token);
        if (link == null) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "分享链接不存在或已过期");
            return;
        }

        // 带密码且未验证 → 重定向到密码验证页
        if (link.hasPassword() && !isVerified(httpRequest, token)) {
            httpResponse.sendRedirect(contextPath + "/share-page/" + token);
            return;
        }

        // 验证通过（或无密码）→ 重定向到在线预览
        // url 参数必须按 OnlinePreviewController 的约定做 Base64 编码（WebUtils.decodeUrl 解码），
        // 普通 URL 编码会被当作 Base64 解析失败而进入错误页
        String encodedUrl = java.util.Base64.getEncoder()
                .encodeToString(link.getFileUrl().getBytes(StandardCharsets.UTF_8));
        String targetUrl = contextPath + "/onlinePreview?url="
                + URLEncoder.encode(encodedUrl, StandardCharsets.UTF_8);
        httpResponse.sendRedirect(targetUrl);
    }

    /**
     * 提取令牌（调用方已保证 requestUri 以 prefix 开头）
     */
    private String extractToken(String requestUri, String prefix) {
        String remaining = requestUri.substring(prefix.length());
        // 去掉可能存在的查询参数和路径分隔符
        int slashIndex = remaining.indexOf('/');
        if (slashIndex > 0) {
            remaining = remaining.substring(0, slashIndex);
        }
        return remaining;
    }

    private boolean isVerified(HttpServletRequest request, String token) {
        String verified = (String) request.getSession().getAttribute("share_verified_" + token);
        return "true".equals(verified);
    }
}
