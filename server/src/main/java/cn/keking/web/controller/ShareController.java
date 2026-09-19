package cn.keking.web.controller;

import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.ShareService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 分享控制器
 *
 * @author Claude Code
 */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * 创建分享链接请求体（字段名与前端契约保持一致）
     */
    record CreateShareRequest(String url, String fileName, String password, Integer ttlHours) {
    }

    /**
     * 创建分享链接
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createShare(@RequestBody CreateShareRequest request) {
        if (request.url() == null || request.url().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件URL不能为空"));
        }

        ShareLink link = shareService.createShareLink(request.url(), request.fileName(), request.password(), request.ttlHours());

        Map<String, Object> response = new HashMap<>();
        response.put("token", link.getToken());
        response.put("shareUrl", "/share/" + link.getToken());
        response.put("expireAt", link.getExpireAt() != null ? link.getExpireAt().toString() : null);

        return ResponseEntity.ok(response);
    }

    /**
     * 验证分享密码
     * 成功后在 session 写入验证标记（"share_verified_" + token），ShareAccessFilter 据此放行，
     * 否则带密码的分享链接会在密码页与预览页之间死循环
     */
    @PostMapping("/verify/{token}")
    public ResponseEntity<Map<String, Object>> verifyPassword(
            @PathVariable String token,
            @RequestBody Map<String, String> request,
            HttpSession httpSession) {

        // 只查一次缓存，密码校验复用已查得的 ShareLink
        ShareLink link = shareService.getShareLink(token);
        if (!shareService.verifyPassword(link, request.get("password"))) {
            return ResponseEntity.status(401).body(Map.of("error", "密码不正确"));
        }

        httpSession.setAttribute("share_verified_" + token, "true");
        return ResponseEntity.ok(Map.of("success", true, "url", link.getFileUrl()));
    }
}
