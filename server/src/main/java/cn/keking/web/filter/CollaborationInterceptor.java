package cn.keking.web.filter;

import cn.keking.config.ConfigConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 在线协作功能拦截器
 * 按请求路径前缀统一校验对应协作子功能开关，未启用时返回 403 与 JSON 错误信息，
 * 替代各协作 Controller 中分散的开关守卫代码。
 * 注意：/share-page/{token} 密码页路由不在此拦截范围内。
 *
 * @author Claude Code
 */
public class CollaborationInterceptor implements HandlerInterceptor {

    /** JSON 响应内容类型 */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        String disabledMessage = null;
        if (uri.startsWith("/api/annotation/")) {
            if (!ConfigConstants.isCollaborationAnnotationEnabled()) {
                disabledMessage = "批注功能未启用";
            }
        } else if (uri.startsWith("/api/share/")) {
            if (!ConfigConstants.isCollaborationShareEnabled()) {
                disabledMessage = "分享功能未启用";
            }
        } else if (uri.startsWith("/api/collab/")) {
            if (!ConfigConstants.isCollaborationEditEnabled()) {
                disabledMessage = "协作编辑功能未启用";
            }
        }

        if (disabledMessage == null) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", disabledMessage)));
        return false;
    }
}
