package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * 分享服务
 *
 * @author Claude Code
 */
@Service
public class ShareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareService.class);

    private final CacheService cacheService;

    public ShareService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 创建分享链接
     */
    public ShareLink createShareLink(String fileUrl, String fileName, String password, Integer ttlHours) {
        String token = UUID.randomUUID().toString().replace("-", "");

        ShareLink link = new ShareLink();
        link.setToken(token);
        link.setFileUrl(fileUrl);
        link.setFileName(fileName);
        link.setPassword(password);

        int ttl = ttlHours != null ? ttlHours : ConfigConstants.getShareDefaultTtl();
        link.setExpireAt(Instant.now().plus(ttl, ChronoUnit.HOURS));

        cacheService.addShareLink(token, link);

        LOGGER.info("创建分享链接: token={}, fileName={}, ttl={}h", token, fileName, ttl);
        return link;
    }

    /**
     * 获取分享链接
     */
    public ShareLink getShareLink(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        ShareLink link = cacheService.getShareLink(token);
        if (link == null) {
            LOGGER.warn("分享链接不存在: token={}", token);
            return null;
        }

        if (link.isExpired()) {
            LOGGER.info("分享链接已过期: token={}", token);
            cacheService.removeShareLink(token);
            return null;
        }

        return link;
    }

    /**
     * 验证分享密码
     */
    public boolean verifyPassword(String token, String password) {
        ShareLink link = getShareLink(token);
        if (link == null) {
            return false;
        }

        if (!link.hasPassword()) {
            return true;
        }

        return link.getPassword().equals(password);
    }

    /**
     * 删除分享链接
     */
    public void deleteShareLink(String token) {
        cacheService.removeShareLink(token);
        LOGGER.info("删除分享链接: token={}", token);
    }

    /**
     * 获取所有分享链接
     */
    public Map<String, ShareLink> getAllShareLinks() {
        return cacheService.getAllShareLinks();
    }
}