package cn.keking.web.controller;

import cn.keking.config.ConfigConstants;
import cn.keking.model.collaboration.Annotation;
import cn.keking.service.AnnotationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 批注控制器
 *
 * @author Claude Code
 */
@RestController
@RequestMapping("/api/annotation")
public class AnnotationController {

    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    /**
     * 批注保存请求体
     */
    public static class SaveRequest {
        private String fileKey;
        private List<Annotation> annotations;

        public String getFileKey() { return fileKey; }
        public void setFileKey(String fileKey) { this.fileKey = fileKey; }
        public List<Annotation> getAnnotations() { return annotations; }
        public void setAnnotations(List<Annotation> annotations) { this.annotations = annotations; }
    }

    /**
     * 批注回复请求体
     */
    public static class ReplyRequest {
        private String fileKey;
        private String annotationId;
        private String content;
        private String authorName;

        public String getFileKey() { return fileKey; }
        public void setFileKey(String fileKey) { this.fileKey = fileKey; }
        public String getAnnotationId() { return annotationId; }
        public void setAnnotationId(String annotationId) { this.annotationId = annotationId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
    }

    /**
     * 获取文件的所有批注
     */
    @GetMapping("/list")
    public ResponseEntity<List<Annotation>> getAnnotations(@RequestParam String fileKey) {
        if (!ConfigConstants.isCollaborationAnnotationEnabled()) {
            return ResponseEntity.status(403).build();
        }

        List<Annotation> annotations = annotationService.getAnnotations(fileKey);
        return ResponseEntity.ok(annotations);
    }

    /**
     * 保存批注（支持批量）
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveAnnotations(@RequestBody SaveRequest request) {
        if (!ConfigConstants.isCollaborationAnnotationEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "批注功能未启用"));
        }

        if (request.getFileKey() == null || request.getFileKey().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileKey不能为空"));
        }

        List<Annotation> annotations = request.getAnnotations();
        if (annotations == null || annotations.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "批注列表不能为空"));
        }

        annotationService.saveAnnotations(request.getFileKey(), annotations);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 删除单条批注
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> deleteAnnotation(
            @PathVariable String id,
            @RequestParam String fileKey) {

        if (!ConfigConstants.isCollaborationAnnotationEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "批注功能未启用"));
        }

        annotationService.deleteAnnotation(fileKey, id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 回复批注
     */
    @PostMapping("/reply")
    public ResponseEntity<Map<String, Object>> addReply(@RequestBody ReplyRequest request) {
        if (!ConfigConstants.isCollaborationAnnotationEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "批注功能未启用"));
        }

        if (request.getFileKey() == null || request.getAnnotationId() == null || request.getContent() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "参数不完整"));
        }

        Annotation.AnnotationReply reply = new Annotation.AnnotationReply();
        reply.setContent(request.getContent());
        reply.setAuthorName(request.getAuthorName());

        annotationService.addReply(request.getFileKey(), request.getAnnotationId(), reply);
        return ResponseEntity.ok(Map.of("success", true));
    }
}