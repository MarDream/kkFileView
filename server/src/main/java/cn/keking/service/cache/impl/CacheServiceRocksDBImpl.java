package cn.keking.service.cache.impl;

import cn.keking.model.collaboration.Annotation;
import cn.keking.model.collaboration.OnlineUser;
import cn.keking.model.collaboration.ShareLink;
import cn.keking.service.cache.CacheService;
import cn.keking.utils.ConfigUtils;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @auther: chenjh
 * @time: 2019/4/22 11:02
 * @description
 */
@ConditionalOnExpression("'${cache.type:default}'.equals('default')")
@Service
public class CacheServiceRocksDBImpl implements CacheService {

    static {
        RocksDB.loadLibrary();
    }

    private static final String DB_PATH = ConfigUtils.getHomePath() + File.separator + "cache";
    private static final int QUEUE_SIZE = 500000;
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheServiceRocksDBImpl.class);
    private final BlockingQueue<String> blockingQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);

    /**
     * 协作缓存 read-modify-write 互斥锁，保证"读取-修改-写回"的原子性
     */
    private final ReentrantLock collabLock = new ReentrantLock();

    private RocksDB db;

    {
        try {
            db = RocksDB.open(DB_PATH);
            if (db.get(FILE_PREVIEW_PDF_KEY.getBytes()) == null) {
                Map<String, String> initPDFCache = new HashMap<>();
                db.put(FILE_PREVIEW_PDF_KEY.getBytes(), toByteArray(initPDFCache));
            }
            if (db.get(FILE_PREVIEW_IMGS_KEY.getBytes()) == null) {
                Map<String, List<String>> initIMGCache = new HashMap<>();
                db.put(FILE_PREVIEW_IMGS_KEY.getBytes(), toByteArray(initIMGCache));
            }
            if (db.get(FILE_PREVIEW_PDF_IMGS_KEY.getBytes()) == null) {
                Map<String, Integer> initPDFIMGCache = new HashMap<>();
                db.put(FILE_PREVIEW_PDF_IMGS_KEY.getBytes(), toByteArray(initPDFIMGCache));
            }
            if (db.get(COLLAB_SHARE_KEY.getBytes()) == null) {
                Map<String, ShareLink> initShareCache = new HashMap<>();
                db.put(COLLAB_SHARE_KEY.getBytes(), toByteArray(initShareCache));
            }
            if (db.get(COLLAB_ANNOTATION_KEY.getBytes()) == null) {
                Map<String, List<Annotation>> initAnnotationCache = new HashMap<>();
                db.put(COLLAB_ANNOTATION_KEY.getBytes(), toByteArray(initAnnotationCache));
            }
            if (db.get(COLLAB_ONLINE_USER_KEY.getBytes()) == null) {
                Map<String, Set<OnlineUser>> initOnlineUserCache = new HashMap<>();
                db.put(COLLAB_ONLINE_USER_KEY.getBytes(), toByteArray(initOnlineUserCache));
            }
        } catch (RocksDBException | IOException e) {
            LOGGER.error("Uable to init RocksDB" + e);
        }
    }


    @Override
    public void initPDFCachePool(Integer capacity) {

    }

    @Override
    public void initIMGCachePool(Integer capacity) {

    }

    @Override
    public void initPdfImagesCachePool(Integer capacity) {

    }

    @Override
    public void initMediaConvertCachePool(Integer capacity) {

    }

    @Override
    public void putPDFCache(String key, String value) {
        try {
            Map<String, String> pdfCacheItem = getPDFCache();
            pdfCacheItem.put(key, value);
            db.put(FILE_PREVIEW_PDF_KEY.getBytes(), toByteArray(pdfCacheItem));
        } catch (RocksDBException | IOException e) {
            LOGGER.error("Put into RocksDB Exception" + e);
        }
    }

    @Override
    public void putImgCache(String key, List<String> value) {
        try {
            Map<String, List<String>> imgCacheItem = getImgCache();
            imgCacheItem.put(key, value);
            db.put(FILE_PREVIEW_IMGS_KEY.getBytes(), toByteArray(imgCacheItem));
        } catch (RocksDBException | IOException e) {
            LOGGER.error("Put into RocksDB Exception" + e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getPDFCache() {
        Map<String, String> result = new HashMap<>();
        try {
            result = (Map<String, String>) toObject(db.get(FILE_PREVIEW_PDF_KEY.getBytes()));
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getPDFCache(String key) {
        String result = "";
        try {
            Map<String, String> map = (Map<String, String>) toObject(db.get(FILE_PREVIEW_PDF_KEY.getBytes()));
            result = map.get(key);
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getImgCache() {
        Map<String, List<String>> result = new HashMap<>();
        try {
            result = (Map<String, List<String>>) toObject(db.get(FILE_PREVIEW_IMGS_KEY.getBytes()));
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getImgCache(String key) {
        List<String> result = new ArrayList<>();
        Map<String, List<String>> map;
        try {
            map = (Map<String, List<String>>) toObject(db.get(FILE_PREVIEW_IMGS_KEY.getBytes()));
            result = map.get(key);
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Integer getPdfImageCache(String key) {
        Integer result = 0;
        Map<String, Integer> map;
        try {
            map = (Map<String, Integer>) toObject(db.get(FILE_PREVIEW_PDF_IMGS_KEY.getBytes()));
            result = map.get(key);
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return result;
    }

    @Override
    public void putPdfImageCache(String pdfFilePath, int num) {
        try {
            Map<String, Integer> pdfImageCacheItem = getPdfImageCaches();
            pdfImageCacheItem.put(pdfFilePath, num);
            db.put(FILE_PREVIEW_PDF_IMGS_KEY.getBytes(), toByteArray(pdfImageCacheItem));
        } catch (RocksDBException | IOException e) {
            LOGGER.error("Put into RocksDB Exception" + e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getMediaConvertCache() {
        Map<String, String> result = new HashMap<>();
        try {
            result = (Map<String, String>) toObject(db.get(FILE_PREVIEW_MEDIA_CONVERT_KEY.getBytes()));
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return result;
    }

    @Override
    public void putMediaConvertCache(String key, String value) {
        try {
            Map<String, String> mediaConvertCacheItem = getMediaConvertCache();
            mediaConvertCacheItem.put(key, value);
            db.put(FILE_PREVIEW_MEDIA_CONVERT_KEY.getBytes(), toByteArray(mediaConvertCacheItem));
        } catch (RocksDBException | IOException e) {
            LOGGER.error("Put into RocksDB Exception" + e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getMediaConvertCache(String key) {
        String result = "";
        try {
            Map<String, String> map = (Map<String, String>) toObject(db.get(FILE_PREVIEW_MEDIA_CONVERT_KEY.getBytes()));
            result = map.get(key);
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return result;
    }

    @Override
    public void cleanCache() {
        try {
            cleanPdfCache();
            cleanImgCache();
            cleanPdfImgCache();
            cleanMediaConvertCache();
            // 在线用户属易失数据，随定时清理一并清除；
            // 分享链接与批注为用户数据，不在此清理
            cleanOnlineUserCache();
        } catch (IOException | RocksDBException e) {
            LOGGER.error("Clean Cache Exception" + e);
        }
    }

    @Override
    public void addQueueTask(String url) {
        blockingQueue.add(url);
    }

    @Override
    public String takeQueueTask() throws InterruptedException {
        return blockingQueue.take();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> getPdfImageCaches() {
        Map<String, Integer> map = new HashMap<>();
        try {
            map = (Map<String, Integer>) toObject(db.get(FILE_PREVIEW_PDF_IMGS_KEY.getBytes()));
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get from RocksDB Exception" + e);
        }
        return map;
    }


    private byte[] toByteArray(Object obj) throws IOException {
        byte[] bytes;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.flush();
        bytes = bos.toByteArray();
        oos.close();
        bos.close();
        return bytes;
    }

    /**
     * 安全反序列化：添加类型白名单验证，防止反序列化攻击
     * 只允许预期的集合类型进行反序列化
     */
    private Object toObject(byte[] bytes) throws IOException, ClassNotFoundException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        Object obj;
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                // 类型白名单：只允许预期的集合类型
                String className = desc.getName();
                if (!ALLOWED_DESERIALIZATION_CLASSES.contains(className)) {
                    throw new InvalidClassException("Unauthorized deserialization attempt", className);
                }
                return super.resolveClass(desc);
            }
        };
        obj = ois.readObject();
        ois.close();
        bis.close();
        return obj;
    }

    /**
     * 允许反序列化的类型白名单
     * 包含项目中使用的集合类型及其内部类型
     */
    private static final Set<String> ALLOWED_DESERIALIZATION_CLASSES = Set.of(
            // 基本集合类型
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.concurrent.ConcurrentHashMap$KeySetView",
            // Map.Entry
            "java.util.HashMap$Node",
            "java.util.HashMap$Entry",
            // 内部存储类型
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "[Ljava.lang.String;",  // String数组
            // 可能的其他类型
            "java.util.HashSet",
            "java.util.TreeMap",
            "java.util.TreeSet",
            // 协作功能模型类
            "cn.keking.model.collaboration.ShareLink",
            "cn.keking.model.collaboration.Annotation",
            "cn.keking.model.collaboration.OnlineUser",
            "java.time.Instant"
    );

    private void cleanPdfCache() throws IOException, RocksDBException {
        Map<String, String> initPDFCache = new HashMap<>();
        db.put(FILE_PREVIEW_PDF_KEY.getBytes(), toByteArray(initPDFCache));
    }

    private void cleanImgCache() throws IOException, RocksDBException {
        Map<String, List<String>> initIMGCache = new HashMap<>();
        db.put(FILE_PREVIEW_IMGS_KEY.getBytes(), toByteArray(initIMGCache));
    }

    private void cleanPdfImgCache() throws IOException, RocksDBException {
        Map<String, Integer> initPDFIMGCache = new HashMap<>();
        db.put(FILE_PREVIEW_PDF_IMGS_KEY.getBytes(), toByteArray(initPDFIMGCache));
    }

    private void cleanMediaConvertCache() throws IOException, RocksDBException {
        Map<String, String> initMediaConvertCache = new HashMap<>();
        db.put(FILE_PREVIEW_MEDIA_CONVERT_KEY.getBytes(), toByteArray(initMediaConvertCache));
    }

    private void cleanOnlineUserCache() throws IOException, RocksDBException {
        Map<String, Set<OnlineUser>> emptyOnlineUserCache = new HashMap<>();
        db.put(COLLAB_ONLINE_USER_KEY.getBytes(), toByteArray(emptyOnlineUserCache));
    }

    /**
     * 从 RocksDB 读取一个 Map 结构（读取失败或不存在时返回空 Map，由调用方决定兜底语义）
     */
    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> readMap(String cacheKey) {
        try {
            Map<K, V> map = (Map<K, V>) toObject(db.get(cacheKey.getBytes()));
            return map == null ? new HashMap<>() : map;
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Read map from RocksDB Exception, key=" + cacheKey, e);
            return new HashMap<>();
        }
    }

    /**
     * 将 Map 结构写入 RocksDB（失败仅记录日志，不向上抛出）
     */
    private void writeMap(String cacheKey, Map<?, ?> map) {
        try {
            db.put(cacheKey.getBytes(), toByteArray(map));
        } catch (RocksDBException | IOException e) {
            LOGGER.error("Write map into RocksDB Exception, key=" + cacheKey, e);
        }
    }

    @Override
    public void addShareLink(String token, ShareLink link) {
        collabLock.lock();
        try {
            Map<String, ShareLink> shareLinks = readMap(COLLAB_SHARE_KEY);
            shareLinks.put(token, link);
            writeMap(COLLAB_SHARE_KEY, shareLinks);
        } finally {
            collabLock.unlock();
        }
    }

    @Override
    public ShareLink getShareLink(String token) {
        Map<String, ShareLink> shareLinks = readMap(COLLAB_SHARE_KEY);
        return shareLinks.get(token);
    }

    @Override
    public void removeShareLink(String token) {
        collabLock.lock();
        try {
            Map<String, ShareLink> shareLinks = readMap(COLLAB_SHARE_KEY);
            shareLinks.remove(token);
            writeMap(COLLAB_SHARE_KEY, shareLinks);
        } finally {
            collabLock.unlock();
        }
    }

    @Override
    public void putAnnotations(String fileKey, List<Annotation> annotations) {
        collabLock.lock();
        try {
            Map<String, List<Annotation>> annotationsMap = readMap(COLLAB_ANNOTATION_KEY);
            annotationsMap.put(fileKey, annotations);
            writeMap(COLLAB_ANNOTATION_KEY, annotationsMap);
        } finally {
            collabLock.unlock();
        }
    }

    @Override
    public List<Annotation> getAnnotations(String fileKey) {
        Map<String, List<Annotation>> annotationsMap = readMap(COLLAB_ANNOTATION_KEY);
        List<Annotation> result = annotationsMap.get(fileKey);
        return result == null ? Collections.emptyList() : result;
    }

    @Override
    public void addOnlineUser(String sessionId, OnlineUser user) {
        collabLock.lock();
        try {
            Map<String, Set<OnlineUser>> onlineUserMap = readMap(COLLAB_ONLINE_USER_KEY);
            onlineUserMap.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(user);
            writeMap(COLLAB_ONLINE_USER_KEY, onlineUserMap);
        } finally {
            collabLock.unlock();
        }
    }

    @Override
    public void removeOnlineUser(String sessionId, String userSessionId) {
        collabLock.lock();
        try {
            Map<String, Set<OnlineUser>> onlineUserMap = readMap(COLLAB_ONLINE_USER_KEY);
            Set<OnlineUser> users = onlineUserMap.get(sessionId);
            if (users != null) {
                users.removeIf(u -> userSessionId.equals(u.getSessionId()));
            }
            writeMap(COLLAB_ONLINE_USER_KEY, onlineUserMap);
        } finally {
            collabLock.unlock();
        }
    }

    @Override
    public Set<OnlineUser> getOnlineUsers(String sessionId) {
        Map<String, Set<OnlineUser>> onlineUserMap = readMap(COLLAB_ONLINE_USER_KEY);
        Set<OnlineUser> users = onlineUserMap.get(sessionId);
        return users == null ? Collections.emptySet() : users;
    }

    @Override
    public void refreshOnlineUser(String sessionId, String userSessionId) {
        collabLock.lock();
        try {
            Map<String, Set<OnlineUser>> onlineUserMap = readMap(COLLAB_ONLINE_USER_KEY);
            Set<OnlineUser> users = onlineUserMap.get(sessionId);
            if (users == null) {
                return;
            }
            boolean changed = false;
            for (OnlineUser user : users) {
                if (userSessionId.equals(user.getSessionId())) {
                    user.setLastActiveAt(java.time.Instant.now());
                    changed = true;
                    break;
                }
            }
            // 反序列化得到的是新对象，必须写回 RocksDB 才能持久化刷新结果
            if (changed) {
                writeMap(COLLAB_ONLINE_USER_KEY, onlineUserMap);
            }
        } finally {
            collabLock.unlock();
        }
    }

    @Override
    public ShareLink findShareLinkByFileUrl(String fileUrl) {
        // 分享链接数量级有限（每条分享一个 token），readMap 全量遍历可接受
        Map<String, ShareLink> shareLinks = readMap(COLLAB_SHARE_KEY);
        for (ShareLink link : shareLinks.values()) {
            if (!link.isExpired() && link.hasPassword()
                    && fileUrl.equals(link.getFileUrl())) {
                return link;
            }
        }
        return null;
    }
}
