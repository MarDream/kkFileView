package cn.keking.model.collaboration;

import java.time.Instant;

/**
 * 在线用户模型
 *
 * @author Claude Code
 */
public class OnlineUser {
    private String sessionId;
    private String nickname;
    private String color;
    private Instant joinedAt;
    private CursorPosition cursorPosition;

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

    public CursorPosition getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(CursorPosition cursorPosition) {
        this.cursorPosition = cursorPosition;
    }

    /**
     * 光标位置
     */
    public static class CursorPosition {
        private int page;
        private double x;
        private double y;

        public CursorPosition() {
        }

        public CursorPosition(int page, double x, double y) {
            this.page = page;
            this.x = x;
            this.y = y;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }
    }
}