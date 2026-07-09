package cn.keking.web.controller;

import cn.keking.config.ConfigConstants;
import cn.keking.model.collaboration.CollabOperation;
import cn.keking.model.collaboration.OnlineUser;
import cn.keking.service.CollabSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 协作控制器（STOMP 消息处理 + REST 端点）
 *
 * @author Claude Code
 */
@RestController
@RequestMapping("/api/collab")
public class CollabController {

    @Autowired
    private CollabSessionService collabSessionService;

    /**
     * 创建或复用协作会话（REST）
     * 客户端调用 /api/collab/session?fileKey=...
     */
    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> createSession(@RequestParam String fileKey) {
        if (!ConfigConstants.isCollaborationEditEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "协作编辑功能未启用"));
        }
        String sessionId = collabSessionService.getOrCreateSession(fileKey);
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("fileKey", fileKey);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取在线用户列表（REST）
     */
    @GetMapping("/session/{sessionId}/users")
    public ResponseEntity<Set<OnlineUser>> getOnlineUsers(@PathVariable String sessionId) {
        if (!ConfigConstants.isCollaborationEditEnabled()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(collabSessionService.getActiveUsers(sessionId));
    }

    /**
     * 用户加入会话
     * 客户端发送到 /app/join/{sessionId}
     */
    @MessageMapping("/join/{sessionId}")
    @SendTo("/topic/presence/{sessionId}")
    public Map<String, Object> joinSession(@DestinationVariable String sessionId,
                                            Map<String, String> payload,
                                            SimpMessageHeaderAccessor headerAccessor) {
        String nickname = payload.getOrDefault("nickname", "匿名用户");
        String color = payload.getOrDefault("color", "#3498db");

        OnlineUser user = collabSessionService.joinSession(sessionId, nickname, color);
        headerAccessor.getSessionAttributes().put("collabSessionId", sessionId);
        headerAccessor.getSessionAttributes().put("userSessionId", user.getSessionId());

        return Map.of(
                "type", "join",
                "user", user,
                "onlineUsers", collabSessionService.getActiveUsers(sessionId)
        );
    }

    /**
     * 用户离开会话
     */
    @MessageMapping("/leave/{sessionId}")
    @SendTo("/topic/presence/{sessionId}")
    public Map<String, Object> leaveSession(@DestinationVariable String sessionId,
                                             Map<String, String> payload) {
        String userSessionId = payload.get("userSessionId");
        if (userSessionId != null) {
            collabSessionService.leaveSession(sessionId, userSessionId);
        }

        return Map.of(
                "type", "leave",
                "userSessionId", userSessionId,
                "onlineUsers", collabSessionService.getActiveUsers(sessionId)
        );
    }

    /**
     * 发送文档操作（编辑、光标）
     */
    @MessageMapping("/operation/{sessionId}")
    @SendTo("/topic/session/{sessionId}")
    public CollabOperation applyOperation(@DestinationVariable String sessionId,
                                          CollabOperation operation) {
        return collabSessionService.applyOperation(sessionId, operation);
    }

    /**
     * 光标位置更新
     */
    @MessageMapping("/cursor/{sessionId}")
    @SendTo("/topic/cursor/{sessionId}")
    public Map<String, Object> updateCursor(@DestinationVariable String sessionId,
                                            Map<String, Object> cursor) {
        return cursor;
    }
}