package cn.keking.service;

import cn.keking.model.collaboration.CollabOperation;
import cn.keking.model.collaboration.OnlineUser;
import cn.keking.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协作会话管理服务
 *
 * @author Claude Code
 */
@Service
public class CollabSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollabSessionService.class);

    // 会话 -> 文件Key 映射
    private static final ConcurrentHashMap<String, String> sessionFileKeyMap = new ConcurrentHashMap<>();

    private final CacheService cacheService;

    public CollabSessionService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 创建协作会话
     */
    public String createSession(String fileKey, String creatorNickname) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        sessionFileKeyMap.put(sessionId, fileKey);
        LOGGER.info("创建协作会话: sessionId={}, fileKey={}", sessionId, fileKey);
        return sessionId;
    }

    /**
     * 根据文件Key获取会话ID（已存在则复用）
     */
    public String getOrCreateSession(String fileKey) {
        for (var entry : sessionFileKeyMap.entrySet()) {
            if (fileKey.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return createSession(fileKey, null);
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
     * 应用操作（last-write-wins 策略）
     */
    public CollabOperation applyOperation(String sessionId, CollabOperation operation) {
        operation.setTimestamp(java.time.Instant.now());
        LOGGER.debug("应用操作: sessionId={}, type={}, userId={}", sessionId, operation.getType(), operation.getUserId());
        return operation;
    }

    /**
     * 获取文件Key
     */
    public String getFileKey(String sessionId) {
        return sessionFileKeyMap.get(sessionId);
    }

    /**
     * 删除会话
     */
    public void deleteSession(String sessionId) {
        sessionFileKeyMap.remove(sessionId);
    }
}