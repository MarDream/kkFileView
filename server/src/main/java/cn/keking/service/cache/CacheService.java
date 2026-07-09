package cn.keking.service.cache;

import cn.keking.model.collaboration.Annotation;
import cn.keking.model.collaboration.OnlineUser;
import cn.keking.model.collaboration.ShareLink;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author: chenjh
 * @since: 2019/4/2 16:45
 */
public interface CacheService {

    String FILE_PREVIEW_PDF_KEY = "converted-preview-pdf-file";
    String FILE_PREVIEW_IMGS_KEY = "converted-preview-imgs-file";//压缩包内图片文件集合
    String FILE_PREVIEW_PDF_IMGS_KEY = "converted-preview-pdfimgs-file";
    String FILE_PREVIEW_MEDIA_CONVERT_KEY = "converted-preview-media-file";
    String TASK_QUEUE_NAME = "convert-task";

    // 协作功能缓存池
    String COLLAB_SHARE_KEY = "collab-share-link";
    String COLLAB_ANNOTATION_KEY = "collab-annotation";
    String COLLAB_ONLINE_USER_KEY = "collab-online-user";
    String COLLAB_DOC_LOCK_KEY = "collab-doc-lock";

    Integer DEFAULT_PDF_CAPACITY = 500000;
    Integer DEFAULT_IMG_CAPACITY = 500000;
    Integer DEFAULT_PDFIMG_CAPACITY = 500000;
    Integer DEFAULT_MEDIACONVERT_CAPACITY = 500000;
    Integer DEFAULT_COLLAB_CAPACITY = 100000;

    void initPDFCachePool(Integer capacity);
    void initIMGCachePool(Integer capacity);
    void initPdfImagesCachePool(Integer capacity);
    void initMediaConvertCachePool(Integer capacity);
    void putPDFCache(String key, String value);
    void putImgCache(String key, List<String> value);
    Map<String, String> getPDFCache();
    String getPDFCache(String key);
    Map<String, List<String>> getImgCache();
    List<String> getImgCache(String key);
    Integer getPdfImageCache(String key);
    void putPdfImageCache(String pdfFilePath, int num);
    Map<String, String> getMediaConvertCache();
    void putMediaConvertCache(String key, String value);
    String getMediaConvertCache(String key);
    void cleanCache();
    void addQueueTask(String url);
    String takeQueueTask() throws InterruptedException;

    // 共享链接缓存
    void addShareLink(String token, ShareLink link);
    ShareLink getShareLink(String token);
    void removeShareLink(String token);
    Map<String, ShareLink> getAllShareLinks();

    // 批注缓存
    void putAnnotations(String fileKey, List<Annotation> annotations);
    List<Annotation> getAnnotations(String fileKey);
    void removeAnnotations(String fileKey);

    // 在线用户缓存
    void addOnlineUser(String sessionId, OnlineUser user);
    void removeOnlineUser(String sessionId, String userSessionId);
    Set<OnlineUser> getOnlineUsers(String sessionId);

    // 文档锁
    boolean tryLockDocument(String fileKey, String lockOwner, long ttlSeconds);
    void unlockDocument(String fileKey, String lockOwner);
    String getDocumentLockOwner(String fileKey);
}
