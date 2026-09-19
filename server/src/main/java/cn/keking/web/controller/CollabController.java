package cn.keking.web.controller;

import cn.keking.model.collaboration.OnlineUser;
import cn.keking.service.CollabSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 协作控制器（STOMP 消息处理 + REST 端点）
 *
 * @author Claude Code
 */
@RestController
@RequestMapping("/api/collab")
public class CollabController {

    private final CollabSessionService collabSessionService;
    /**
     * 协作编辑开关关闭时 WebSocketConfig 不装配，容器中不存在 SimpMessagingTemplate，
     * 因此用 ObjectProvider 可选注入，避免应用启动失败
     */
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    public CollabController(CollabSessionService collabSessionService,
                            ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider) {
        this.collabSessionService = collabSessionService;
        this.messagingTemplateProvider = messagingTemplateProvider;
    }

    /**
     * 创建或复用协作会话（REST）
     * 客户端调用 /api/collab/session?fileKey=...
     */
    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> createSession(@RequestParam String fileKey) {
        String sessionId = collabSessionService.getOrCreateSession(fileKey);
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("fileKey", fileKey);
        return ResponseEntity.ok(response);
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
        // 写入 WebSocket session attributes，断连监听时据此清理在线用户
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

        // userSessionId 可能为 null（异常报文），HashMap 允许 null 值而 Map.of 不允许
        Map<String, Object> message = new HashMap<>();
        message.put("type", "leave");
        message.put("userSessionId", userSessionId);
        message.put("onlineUsers", collabSessionService.getActiveUsers(sessionId));
        return message;
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

    /**
     * 在线用户心跳：前端每 30s 发送一次，刷新该用户的最近活跃时间，
     * 供服务端超时清扫任务识别断网/杀进程等场景下的幽灵用户。
     * 刻意不加 @SendTo、不广播，心跳只刷服务端状态，尽量减少流量
     */
    @MessageMapping("/heartbeat/{sessionId}")
    public void heartbeat(@DestinationVariable String sessionId,
                          Map<String, String> payload) {
        String userSessionId = payload.get("userSessionId");
        if (userSessionId != null) {
            collabSessionService.heartbeat(sessionId, userSessionId);
        }
    }

    /**
     * WebSocket 断连监听：浏览器直接关闭等未发送 leave 消息的场景下，
     * 依据 joinSession 写入的 session attributes 清理在线用户并广播离开事件，防止在线列表泄漏
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(event.getMessage().getHeaders());
        if (attributes == null) {
            return;
        }
        String sessionId = (String) attributes.get("collabSessionId");
        String userSessionId = (String) attributes.get("userSessionId");
        // 未加入过协作会话的连接无需清理
        if (sessionId == null || userSessionId == null) {
            return;
        }

        collabSessionService.leaveSession(sessionId, userSessionId);
        Map<String, Object> message = Map.of(
                "type", "leave",
                "userSessionId", userSessionId,
                "onlineUsers", collabSessionService.getActiveUsers(sessionId)
        );
        // 协作编辑关闭时无 SimpMessagingTemplate，此时也不可能有 WebSocket 连接触发本监听
        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (messagingTemplate != null) {
            // cast 为 Object 避免与 convertAndSend(Object, Map headers) 重载产生二义性
            messagingTemplate.convertAndSend("/topic/presence/" + sessionId, (Object) message);
        }
    }
}
