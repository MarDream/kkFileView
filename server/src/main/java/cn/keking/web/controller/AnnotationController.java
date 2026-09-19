package cn.keking.web.controller;

import cn.keking.model.collaboration.Annotation;
import cn.keking.service.AnnotationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 批注控制器
 * 功能开关由 CollaborationInterceptor 统一校验
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
     * 获取文件的所有批注
     */
    @GetMapping("/list")
    public ResponseEntity<List<Annotation>> getAnnotations(@RequestParam String fileKey) {
        List<Annotation> annotations = annotationService.getAnnotations(fileKey);
        return ResponseEntity.ok(annotations);
    }

    /**
     * 保存批注（支持批量）
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveAnnotations(@RequestBody SaveRequest request) {
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
}
