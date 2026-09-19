package cn.keking.service;

import cn.keking.model.collaboration.Annotation;
import cn.keking.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 批注服务
 *
 * @author Claude Code
 */
@Service
public class AnnotationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationService.class);

    private final CacheService cacheService;

    public AnnotationService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 获取文件的所有批注
     */
    public List<Annotation> getAnnotations(String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Annotation> annotations = cacheService.getAnnotations(fileKey);
        LOGGER.debug("获取批注: fileKey={}, count={}", fileKey, annotations.size());
        return annotations;
    }

    /**
     * 保存批注（支持批量）
     */
    public void saveAnnotations(String fileKey, List<Annotation> annotations) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            return;
        }

        // 为每个批注生成 ID
        for (Annotation annotation : annotations) {
            if (annotation.getId() == null || annotation.getId().isEmpty()) {
                annotation.setId(UUID.randomUUID().toString());
            }
        }

        cacheService.putAnnotations(fileKey, annotations);
        LOGGER.info("保存批注: fileKey={}, count={}", fileKey, annotations.size());
    }

    /**
     * 删除单条批注
     */
    public void deleteAnnotation(String fileKey, String annotationId) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            return;
        }

        List<Annotation> annotations = cacheService.getAnnotations(fileKey);
        if (annotations == null) {
            return;
        }

        annotations.removeIf(a -> annotationId.equals(a.getId()));
        cacheService.putAnnotations(fileKey, annotations);
        LOGGER.info("删除批注: fileKey={}, id={}", fileKey, annotationId);
    }
}
