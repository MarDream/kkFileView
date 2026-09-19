package cn.keking.service;

import cn.keking.model.collaboration.OnlineUser;
import cn.keking.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协作会话管理服务
 * 注：会话映射仅保存在本服务内存中（会话可随时再生成，无需持久化到 CacheService）
 *
 * @author Claude Code
 */
@Service
public class CollabSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollabSessionService.class);

    /** 心跳超时时间（秒）：超过该时长无心跳刷新的在线用户视为幽灵用户（断网/杀进程场景），由清扫任务移除 */
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 90;

    /** 心跳清扫任务执行间隔（毫秒），需小于心跳超时时间以保证及时清扫（前端按 30s 一次心跳） */
    private static final long HEARTBEAT_SWEEP_INTERVAL_MS = 30000;

    // 文件Key -> 会话ID 映射（实例字段，computeIfAbsent 保证同一文件的并发请求只创建一个会话）
    private final ConcurrentHashMap<String, String> fileKeySessionMap = new ConcurrentHashMap<>();

    private final CacheService cacheService;

    /**
     * 协作编辑开关关闭时 WebSocketConfig 不装配，容器中不存在 SimpMessagingTemplate，
     * 因此用 ObjectProvider 可选注入，避免应用启动失败（参照 CollabController 的写法）
     */
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    public CollabSessionService(CacheService cacheService,
                                ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider) {
        this.cacheService = cacheService;
        this.messagingTemplateProvider = messagingTemplateProvider;
    }

    /**
     * 创建协作会话
     */
    public String createSession(String fileKey) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LOGGER.info("创建协作会话: sessionId={}, fileKey={}", sessionId, fileKey);
        return sessionId;
    }

    /**
     * 根据文件Key获取会话ID（已存在则复用，原子性由 computeIfAbsent 保证）
     */
    public String getOrCreateSession(String fileKey) {
        return fileKeySessionMap.computeIfAbsent(fileKey, this::createSession);
    }

    /**
     * 加入会话
     */
    public OnlineUser joinSession(String sessionId, String nickname, String color) {
        String userSessionId = UUID.randomUUID().toString();
        OnlineUser user = new OnlineUser();
        user.setSessionId(userSessionId);
        user.setNickname(nickname);
        user.setColor(color);
        // 初始化最近活跃时间，避免刚加入即被超时清扫误删
        user.setLastActiveAt(Instant.now());

        cacheService.addOnlineUser(sessionId, user);
        LOGGER.info("用户加入会话: sessionId={}, nickname={}", sessionId, nickname);
        return user;
    }

    /**
     * 离开会话
     */
    public void leaveSession(String sessionId, String userSessionId) {
        cacheService.removeOnlineUser(sessionId, userSessionId);
        LOGGER.info("用户离开会话: sessionId={}, userSessionId={}", sessionId, userSessionId);
    }

    /**
     * 获取在线用户
     */
    public Set<OnlineUser> getActiveUsers(String sessionId) {
        return cacheService.getOnlineUsers(sessionId);
    }

    /**
     * 在线用户心跳：由 STOMP 端点 /heartbeat/{sessionId} 调用（前端每 30s 发送一次），
     * 刷新 lastActiveAt 供超时清扫判断
     */
    public void heartbeat(String sessionId, String userSessionId) {
        cacheService.refreshOnlineUser(sessionId, userSessionId);
    }

    /**
     * 幽灵用户超时清扫任务：解决断网、直接关闭/杀死浏览器进程等未发送 leave 消息场景下，
     * 在线列表残留幽灵用户的问题（WebSocket 断连事件只能覆盖连接层正常关闭的情况）。
     * 遍历所有会话，剔除 lastActiveAt 距今超过 {@link #HEARTBEAT_TIMEOUT_SECONDS} 的用户。
     * 说明：历史缓存中反序列化出的用户可能没有 lastActiveAt（字段为 null），此时跳过不清扫，避免误删
     */
    @Scheduled(fixedDelay = HEARTBEAT_SWEEP_INTERVAL_MS)
    public void sweepTimeoutUsers() {
        Instant threshold = Instant.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
        for (String sessionId : fileKeySessionMap.values()) {
            Set<OnlineUser> onlineUsers = cacheService.getOnlineUsers(sessionId);
            if (onlineUsers.isEmpty()) {
                continue;
            }
            // 先复制快照再逐个剔除，避免清扫过程中集合并发变更（JDK 实现返回的是活引用）
            List<OnlineUser> removedUsers = new ArrayList<>();
            for (OnlineUser user : new ArrayList<>(onlineUsers)) {
                Instant lastActiveAt = user.getLastActiveAt();
                if (lastActiveAt == null || lastActiveAt.isAfter(threshold)) {
                    continue;
                }
                cacheService.removeOnlineUser(sessionId, user.getSessionId());
                removedUsers.add(user);
            }
            if (!removedUsers.isEmpty()) {
                LOGGER.info("清扫超时用户: sessionId={}, count={}", sessionId, removedUsers.size());
                broadcastSweepLeave(sessionId, removedUsers);
            }
        }
    }

    /**
     * 向房间广播被清扫的幽灵用户离开事件。
     * 选择"逐个被剔除用户广播一次"而非合并为一次：
     * 保持与既有 leave 消息形状一致（单个 userSessionId），前端无需新增消息类型处理逻辑
     */
    private void broadcastSweepLeave(String sessionId, List<OnlineUser> removedUsers) {
        // 协作编辑关闭时无 SimpMessagingTemplate，此时也不可能有在线用户需要清扫
        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (messagingTemplate == null) {
            return;
        }
        Set<OnlineUser> onlineUsers = cacheService.getOnlineUsers(sessionId);
        for (OnlineUser removed : removedUsers) {
            // cast 为 Object 避免与 convertAndSend(Object, Map headers) 重载产生二义性
            messagingTemplate.convertAndSend("/topic/presence/" + sessionId,
                    (Object) Map.of(
                            "type", "leave",
                            "userSessionId", removed.getSessionId(),
                            "onlineUsers", onlineUsers));
        }
    }
}
