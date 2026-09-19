package cn.keking.model.collaboration;

import java.io.Serializable;
import java.time.Instant;

/**
 * 在线用户模型
 *
 * @author Claude Code
 */
public class OnlineUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String nickname;
    private String color;
    private Instant joinedAt;
    /** 最近活跃时间，用于超时清扫（断网/杀进程等未发 leave 消息场景下的幽灵用户判定） */
    private Instant lastActiveAt;

    public OnlineUser() {
        this.joinedAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
