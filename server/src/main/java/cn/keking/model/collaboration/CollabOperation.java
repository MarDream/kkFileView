package cn.keking.model.collaboration;

import java.time.Instant;

/**
 * 协作操作模型
 *
 * @author Claude Code
 */
public class CollabOperation {
    private OperationType type;
    private String userId;
    private int position;
    private String content;
    private Instant timestamp;

    public CollabOperation() {
        this.timestamp = Instant.now();
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        INSERT,
        DELETE,
        CURSOR,
        REPLACE
    }
}