package cn.keking.web.filter;

import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.ShareService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 分享访问过滤器
 * 拦截 /share/{token} 路径，校验分享链接的有效性
 *
 * @author Claude Code
 */
@Component
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

        // 如果设置了密码，跳转到密码验证页面
        if (link.hasPassword() && !isVerified(httpRequest, token)) {
            httpRequest.setAttribute("shareToken", token);
            httpRequest.getRequestDispatcher("/share.ftl").forward(request, response);
            return;
        }

        // 转发到在线预览
        String targetUrl = "/onlinePreview?url=" + java.net.URLEncoder.encode(link.getFileUrl(), "UTF-8");
        httpRequest.getRequestDispatcher(targetUrl).forward(request, response);
    }

    private String extractToken(String requestUri, String prefix) {
        if (!requestUri.startsWith(prefix)) {
            return null;
        }
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

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}