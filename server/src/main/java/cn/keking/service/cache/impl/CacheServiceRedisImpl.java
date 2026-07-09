package cn.keking.service.cache.impl;

import cn.keking.model.collaboration.Annotation;
import cn.keking.model.collaboration.OnlineUser;
import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.cache.CacheService;
import org.redisson.api.RBucket;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @auther: chenjh
 * @time: 2019/4/2 18:02
 * @description
 */
@ConditionalOnExpression("'${cache.type:default}'.equals('redis')")
@Service
public class CacheServiceRedisImpl implements CacheService {

    private final RedissonClient redissonClient;

    // 直接注入 Spring 容器中的 RedissonClient Bean
    public CacheServiceRedisImpl(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void initPDFCachePool(Integer capacity) { }
    @Override
    public void initIMGCachePool(Integer capacity) { }
    @Override
    public void initPdfImagesCachePool(Integer capacity) { }

    @Override
    public void initMediaConvertCachePool(Integer capacity) {

    }

    @Override
    public void putPDFCache(String key, String value) {
        RMapCache<String, String> convertedList = redissonClient.getMapCache(FILE_PREVIEW_PDF_KEY);
        convertedList.fastPut(key, value);
    }

    @Override
    public void putImgCache(String key, List<String> value) {
        RMapCache<String, List<String>> convertedList = redissonClient.getMapCache(FILE_PREVIEW_IMGS_KEY);
        convertedList.fastPut(key, value);
    }

    @Override
    public Map<String, String> getPDFCache() {
        return redissonClient.getMapCache(FILE_PREVIEW_PDF_KEY);
    }

    @Override
    public String getPDFCache(String key) {
        RMapCache<String, String> convertedList = redissonClient.getMapCache(FILE_PREVIEW_PDF_KEY);
        return convertedList.get(key);
    }

    @Override
    public Map<String, List<String>> getImgCache() {
        return redissonClient.getMapCache(FILE_PREVIEW_IMGS_KEY);
    }

    @Override
    public List<String> getImgCache(String key) {
        RMapCache<String, List<String>> convertedList = redissonClient.getMapCache(FILE_PREVIEW_IMGS_KEY);
        return convertedList.get(key);
    }

    @Override
    public Integer getPdfImageCache(String key) {
        RMapCache<String, Integer> convertedList = redissonClient.getMapCache(FILE_PREVIEW_PDF_IMGS_KEY);
        return convertedList.get(key);
    }

    @Override
    public void putPdfImageCache(String pdfFilePath, int num) {
        RMapCache<String, Integer> convertedList = redissonClient.getMapCache(FILE_PREVIEW_PDF_IMGS_KEY);
        convertedList.fastPut(pdfFilePath, num);
    }

    @Override
    public Map<String, String> getMediaConvertCache() {
        return redissonClient.getMapCache(FILE_PREVIEW_MEDIA_CONVERT_KEY);
    }

    @Override
    public void putMediaConvertCache(String key, String value) {
        RMapCache<String, String> convertedList = redissonClient.getMapCache(FILE_PREVIEW_MEDIA_CONVERT_KEY);
        convertedList.fastPut(key, value);
    }

    @Override
    public String getMediaConvertCache(String key) {
        RMapCache<String, String> convertedList = redissonClient.getMapCache(FILE_PREVIEW_MEDIA_CONVERT_KEY);
        return convertedList.get(key);
    }

    @Override
    public void cleanCache() {
        cleanPdfCache();
        cleanImgCache();
        cleanPdfImgCache();
        cleanMediaConvertCache();
    }

    @Override
    public void addQueueTask(String url) {
        RBlockingQueue<String> queue = redissonClient.getBlockingQueue(TASK_QUEUE_NAME);
        queue.addAsync(url);
    }

    @Override
    public String takeQueueTask() throws InterruptedException {
        RBlockingQueue<String> queue = redissonClient.getBlockingQueue(TASK_QUEUE_NAME);
        return queue.take();
    }

    private void cleanPdfCache() {
        RMapCache<String, String> pdfCache = redissonClient.getMapCache(FILE_PREVIEW_PDF_KEY);
        pdfCache.clear();
    }

    private void cleanImgCache() {
        RMapCache<String, List<String>> imgCache = redissonClient.getMapCache(FILE_PREVIEW_IMGS_KEY);
        imgCache.clear();
    }

    private void cleanPdfImgCache() {
        RMapCache<String, Integer> pdfImg = redissonClient.getMapCache(FILE_PREVIEW_PDF_IMGS_KEY);
        pdfImg.clear();
    }

    private void cleanMediaConvertCache() {
        RMapCache<String, Integer> mediaConvertCache = redissonClient.getMapCache(FILE_PREVIEW_MEDIA_CONVERT_KEY);
        mediaConvertCache.clear();
    }

    @Override
    public void addShareLink(String token, ShareLink link) {
        RMapCache<String, ShareLink> shareLinks = redissonClient.getMapCache(COLLAB_SHARE_KEY);
        long ttlSeconds = link.getExpireAt() != null
                ? java.time.Duration.between(java.time.Instant.now(), link.getExpireAt()).getSeconds()
                : 0;
        if (ttlSeconds > 0) {
            shareLinks.put(token, link, ttlSeconds, TimeUnit.SECONDS);
        } else {
            shareLinks.put(token, link);
        }
    }

    @Override
    public ShareLink getShareLink(String token) {
        RMapCache<String, ShareLink> shareLinks = redissonClient.getMapCache(COLLAB_SHARE_KEY);
        return shareLinks.get(token);
    }

    @Override
    public void removeShareLink(String token) {
        RMapCache<String, ShareLink> shareLinks = redissonClient.getMapCache(COLLAB_SHARE_KEY);
        shareLinks.remove(token);
    }

    @Override
    public Map<String, ShareLink> getAllShareLinks() {
        return redissonClient.getMapCache(COLLAB_SHARE_KEY);
    }

    @Override
    public void putAnnotations(String fileKey, List<Annotation> annotations) {
        RMapCache<String, List<Annotation>> annotationsMap = redissonClient.getMapCache(COLLAB_ANNOTATION_KEY);
        annotationsMap.put(fileKey, annotations);
    }

    @Override
    public List<Annotation> getAnnotations(String fileKey) {
        RMapCache<String, List<Annotation>> annotationsMap = redissonClient.getMapCache(COLLAB_ANNOTATION_KEY);
        List<Annotation> result = annotationsMap.get(fileKey);
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public void removeAnnotations(String fileKey) {
        RMapCache<String, List<Annotation>> annotationsMap = redissonClient.getMapCache(COLLAB_ANNOTATION_KEY);
        annotationsMap.remove(fileKey);
    }

    @Override
    public void addOnlineUser(String sessionId, OnlineUser user) {
        RSet<OnlineUser> users = redissonClient.getSet(COLLAB_ONLINE_USER_KEY + ":" + sessionId);
        users.add(user);
    }

    @Override
    public void removeOnlineUser(String sessionId, String userSessionId) {
        RSet<OnlineUser> users = redissonClient.getSet(COLLAB_ONLINE_USER_KEY + ":" + sessionId);
        users.removeIf(u -> userSessionId.equals(u.getSessionId()));
    }

    @Override
    public Set<OnlineUser> getOnlineUsers(String sessionId) {
        RSet<OnlineUser> users = redissonClient.getSet(COLLAB_ONLINE_USER_KEY + ":" + sessionId);
        return users.readAll();
    }

    @Override
    public boolean tryLockDocument(String fileKey, String lockOwner, long ttlSeconds) {
        RBucket<String> lockBucket = redissonClient.getBucket(COLLAB_DOC_LOCK_KEY + ":" + fileKey);
        String currentOwner = lockBucket.get();
        if (currentOwner == null || currentOwner.equals(lockOwner)) {
            lockBucket.set(lockOwner, ttlSeconds, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    @Override
    public void unlockDocument(String fileKey, String lockOwner) {
        RBucket<String> lockBucket = redissonClient.getBucket(COLLAB_DOC_LOCK_KEY + ":" + fileKey);
        String currentOwner = lockBucket.get();
        if (lockOwner.equals(currentOwner)) {
            lockBucket.delete();
        }
    }

    @Override
    public String getDocumentLockOwner(String fileKey) {
        RBucket<String> lockBucket = redissonClient.getBucket(COLLAB_DOC_LOCK_KEY + ":" + fileKey);
        return lockBucket.get();
    }
}
