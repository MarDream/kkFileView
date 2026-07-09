package cn.keking.web.controller;

import cn.keking.config.ConfigConstants;
import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.ShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * 创建分享链接
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createShare(@RequestBody Map<String, Object> request) {
        if (!ConfigConstants.isCollaborationShareEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "分享功能未启用"));
        }

        String fileUrl = (String) request.get("url");
        String fileName = (String) request.get("fileName");
        String password = (String) request.get("password");
        Integer ttlHours = null;
        Object ttlObj = request.get("ttlHours");
        if (ttlObj instanceof Number) {
            ttlHours = ((Number) ttlObj).intValue();
        }

        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件URL不能为空"));
        }

        ShareLink link = shareService.createShareLink(fileUrl, fileName, password, ttlHours);

        Map<String, Object> response = new HashMap<>();
        response.put("token", link.getToken());
        response.put("shareUrl", "/share/" + link.getToken());
        response.put("expireAt", link.getExpireAt() != null ? link.getExpireAt().toString() : null);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取分享链接信息
     */
    @GetMapping("/info/{token}")
    public ResponseEntity<Map<String, Object>> getShareInfo(@PathVariable String token) {
        if (!ConfigConstants.isCollaborationShareEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "分享功能未启用"));
        }

        ShareLink link = shareService.getShareLink(token);
        if (link == null) {
            return ResponseEntity.status(404).body(Map.of("error", "分享链接不存在或已过期"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("fileName", link.getFileName());
        response.put("hasPassword", link.hasPassword());
        response.put("expireAt", link.getExpireAt() != null ? link.getExpireAt().toString() : null);

        return ResponseEntity.ok(response);
    }

    /**
     * 验证分享密码
     */
    @PostMapping("/verify/{token}")
    public ResponseEntity<Map<String, Object>> verifyPassword(
            @PathVariable String token,
            @RequestBody Map<String, String> request) {

        if (!ConfigConstants.isCollaborationShareEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "分享功能未启用"));
        }

        String password = request.get("password");
        boolean valid = shareService.verifyPassword(token, password);

        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("error", "密码不正确"));
        }

        ShareLink link = shareService.getShareLink(token);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("url", link.getFileUrl());

        return ResponseEntity.ok(response);
    }

    /**
     * 删除分享链接
     */
    @DeleteMapping("/delete/{token}")
    public ResponseEntity<Map<String, Object>> deleteShare(@PathVariable String token) {
        if (!ConfigConstants.isCollaborationShareEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "分享功能未启用"));
        }

        shareService.deleteShareLink(token);
        return ResponseEntity.ok(Map.of("success", true));
    }
}