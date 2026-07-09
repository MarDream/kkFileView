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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

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
            if (db.get(COLLAB_DOC_LOCK_KEY.getBytes()) == null) {
                Map<String, String> initDocLockCache = new HashMap<>();
                db.put(COLLAB_DOC_LOCK_KEY.getBytes(), toByteArray(initDocLockCache));
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
            "cn.keking.model.collaboration.Annotation$AnnotationReply",
            "cn.keking.model.collaboration.OnlineUser",
            "cn.keking.model.collaboration.OnlineUser$CursorPosition",
            "cn.keking.model.collaboration.CollabOperation",
            "cn.keking.model.collaboration.CollabOperation$OperationType",
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

    @SuppressWarnings("unchecked")
    @Override
    public void addShareLink(String token, ShareLink link) {
        try {
            Map<String, ShareLink> shareLinks = (Map<String, ShareLink>) toObject(db.get(COLLAB_SHARE_KEY.getBytes()));
            if (shareLinks == null) {
                shareLinks = new HashMap<>();
            }
            shareLinks.put(token, link);
            db.put(COLLAB_SHARE_KEY.getBytes(), toByteArray(shareLinks));
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Put share link into RocksDB Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ShareLink getShareLink(String token) {
        try {
            Map<String, ShareLink> shareLinks = (Map<String, ShareLink>) toObject(db.get(COLLAB_SHARE_KEY.getBytes()));
            return shareLinks == null ? null : shareLinks.get(token);
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get share link from RocksDB Exception", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void removeShareLink(String token) {
        try {
            Map<String, ShareLink> shareLinks = (Map<String, ShareLink>) toObject(db.get(COLLAB_SHARE_KEY.getBytes()));
            if (shareLinks != null) {
                shareLinks.remove(token);
                db.put(COLLAB_SHARE_KEY.getBytes(), toByteArray(shareLinks));
            }
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Remove share link from RocksDB Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, ShareLink> getAllShareLinks() {
        try {
            Map<String, ShareLink> shareLinks = (Map<String, ShareLink>) toObject(db.get(COLLAB_SHARE_KEY.getBytes()));
            return shareLinks == null ? new HashMap<>() : shareLinks;
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get all share links from RocksDB Exception", e);
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void putAnnotations(String fileKey, List<Annotation> annotations) {
        try {
            Map<String, List<Annotation>> annotationsMap = (Map<String, List<Annotation>>) toObject(db.get(COLLAB_ANNOTATION_KEY.getBytes()));
            if (annotationsMap == null) {
                annotationsMap = new HashMap<>();
            }
            annotationsMap.put(fileKey, annotations);
            db.put(COLLAB_ANNOTATION_KEY.getBytes(), toByteArray(annotationsMap));
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Put annotations into RocksDB Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Annotation> getAnnotations(String fileKey) {
        try {
            Map<String, List<Annotation>> annotationsMap = (Map<String, List<Annotation>>) toObject(db.get(COLLAB_ANNOTATION_KEY.getBytes()));
            if (annotationsMap == null) {
                return Collections.emptyList();
            }
            List<Annotation> result = annotationsMap.get(fileKey);
            return result == null ? Collections.emptyList() : result;
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get annotations from RocksDB Exception", e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void removeAnnotations(String fileKey) {
        try {
            Map<String, List<Annotation>> annotationsMap = (Map<String, List<Annotation>>) toObject(db.get(COLLAB_ANNOTATION_KEY.getBytes()));
            if (annotationsMap != null) {
                annotationsMap.remove(fileKey);
                db.put(COLLAB_ANNOTATION_KEY.getBytes(), toByteArray(annotationsMap));
            }
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Remove annotations from RocksDB Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addOnlineUser(String sessionId, OnlineUser user) {
        try {
            Map<String, Set<OnlineUser>> onlineUserMap = (Map<String, Set<OnlineUser>>) toObject(db.get(COLLAB_ONLINE_USER_KEY.getBytes()));
            if (onlineUserMap == null) {
                onlineUserMap = new HashMap<>();
            }
            onlineUserMap.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(user);
            db.put(COLLAB_ONLINE_USER_KEY.getBytes(), toByteArray(onlineUserMap));
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Add online user into RocksDB Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void removeOnlineUser(String sessionId, String userSessionId) {
        try {
            Map<String, Set<OnlineUser>> onlineUserMap = (Map<String, Set<OnlineUser>>) toObject(db.get(COLLAB_ONLINE_USER_KEY.getBytes()));
            if (onlineUserMap != null) {
                Set<OnlineUser> users = onlineUserMap.get(sessionId);
                if (users != null) {
                    users.removeIf(u -> userSessionId.equals(u.getSessionId()));
                }
                db.put(COLLAB_ONLINE_USER_KEY.getBytes(), toByteArray(onlineUserMap));
            }
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Remove online user from RocksDB Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<OnlineUser> getOnlineUsers(String sessionId) {
        try {
            Map<String, Set<OnlineUser>> onlineUserMap = (Map<String, Set<OnlineUser>>) toObject(db.get(COLLAB_ONLINE_USER_KEY.getBytes()));
            if (onlineUserMap == null) {
                return Collections.emptySet();
            }
            Set<OnlineUser> users = onlineUserMap.get(sessionId);
            return users == null ? Collections.emptySet() : users;
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get online users from RocksDB Exception", e);
            return Collections.emptySet();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean tryLockDocument(String fileKey, String lockOwner, long ttlSeconds) {
        try {
            Map<String, String> docLockMap = (Map<String, String>) toObject(db.get(COLLAB_DOC_LOCK_KEY.getBytes()));
            if (docLockMap == null) {
                docLockMap = new HashMap<>();
            }
            String currentOwner = docLockMap.get(fileKey);
            if (currentOwner == null || currentOwner.equals(lockOwner)) {
                docLockMap.put(fileKey, lockOwner);
                db.put(COLLAB_DOC_LOCK_KEY.getBytes(), toByteArray(docLockMap));
                return true;
            }
            return false;
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Try lock document in RocksDB Exception", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void unlockDocument(String fileKey, String lockOwner) {
        try {
            Map<String, String> docLockMap = (Map<String, String>) toObject(db.get(COLLAB_DOC_LOCK_KEY.getBytes()));
            if (docLockMap != null && lockOwner.equals(docLockMap.get(fileKey))) {
                docLockMap.remove(fileKey);
                db.put(COLLAB_DOC_LOCK_KEY.getBytes(), toByteArray(docLockMap));
            }
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Unlock document in RocksDB Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public String getDocumentLockOwner(String fileKey) {
        try {
            Map<String, String> docLockMap = (Map<String, String>) toObject(db.get(COLLAB_DOC_LOCK_KEY.getBytes()));
            return docLockMap == null ? null : docLockMap.get(fileKey);
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOGGER.error("Get document lock owner from RocksDB Exception", e);
            return null;
        }
    }
}
