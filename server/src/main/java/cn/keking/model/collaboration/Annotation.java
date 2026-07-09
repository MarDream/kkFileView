package cn.keking.model.collaboration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 批注模型
 *
 * @author Claude Code
 */
public class Annotation {
    private String id;
    private String fileKey;
    private int page;
    private double x;
    private double y;
    private double width;
    private double height;
    private String content;
    private String authorName;
    private String color;
    private Instant createdAt;
    private List<AnnotationReply> replies;

    public Annotation() {
        this.createdAt = Instant.now();
        this.replies = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
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

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<AnnotationReply> getReplies() {
        return replies;
    }

    public void setReplies(List<AnnotationReply> replies) {
        this.replies = replies;
    }

    public void addReply(AnnotationReply reply) {
        this.replies.add(reply);
    }

    /**
     * 批注回复
     */
    public static class AnnotationReply {
        private String id;
        private String content;
        private String authorName;
        private Instant createdAt;

        public AnnotationReply() {
            this.createdAt = Instant.now();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }
}