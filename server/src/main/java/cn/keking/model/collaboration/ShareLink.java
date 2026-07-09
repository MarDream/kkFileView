package cn.keking.model.collaboration;

import java.time.Instant;

/**
 * 分享链接模型
 *
 * @author Claude Code
 */
public class ShareLink {
    private String token;
    private String fileUrl;
    private String fileName;
    private String password;
    private Instant createdAt;
    private Instant expireAt;
    private String creatorId;

    public ShareLink() {
        this.createdAt = Instant.now();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public boolean isExpired() {
        return expireAt != null && Instant.now().isAfter(expireAt);
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }
}