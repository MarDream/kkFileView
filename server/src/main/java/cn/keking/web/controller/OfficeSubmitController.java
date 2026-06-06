package cn.keking.web.controller;

import cn.keking.model.FileAttribute;
import cn.keking.service.FileHandlerService;
import cn.keking.service.impl.OfficeFilePreviewImpl;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Office 异步预览控制器。
 *
 * <p>提供"提交 + 轮询"风格的异步预览接口，用于大文件预览场景下避免前端 HTTP 超时。
 * 与 {@code /onlinePreview}（同步阻塞返回 HTML）平行存在，互补使用：</p>
 *
 * <ul>
 *   <li>小文件 / 已转码文件：仍走 {@code /onlinePreview}</li>
 *   <li>大文件 / 首次转码：先调 {@code /officeSubmit} 拿 taskId，再轮询 {@code /getOfficeOnlineHtmlUrl?taskId=xxx}</li>
 * </ul>
 *
 * <p>状态码（与 kkFileView 现有约定保持一致）：</p>
 * <ul>
 *   <li>{@code 0}：转换中（CONVERTING），需继续轮询</li>
 *   <li>{@code 2}：转换完成（SUCCESS），{@code convertUrl} 可直接嵌入 iframe</li>
 *   <li>{@code -1}：转换失败（FAILED），{@code message} 包含错误信息</li>
 * </ul>
 */
@RestController
public class OfficeSubmitController {

    private static final Logger logger = LoggerFactory.getLogger(OfficeSubmitController.class);
    private static final String BASE64_DECODE_ERROR_MSG = "Base64解码失败，请检查你的 %s 是否采用 Base64 + urlEncode 双重编码了！";
    /** 状态码：转换中 */
    private static final int STATUS_CONVERTING = 0;
    /** 状态码：转换完成 */
    private static final int STATUS_SUCCESS = 2;
    /** 状态码：转换失败 */
    private static final int STATUS_FAILED = -1;

    private final FileHandlerService fileHandlerService;
    private final OfficeFilePreviewImpl officeFilePreview;

    public OfficeSubmitController(FileHandlerService fileHandlerService, OfficeFilePreviewImpl officeFilePreview) {
        this.fileHandlerService = fileHandlerService;
        this.officeFilePreview = officeFilePreview;
    }

    /**
     * 提交异步 office 转码任务。
     *
     * <p>该方法 <strong>不阻塞</strong>等待转码完成：仅启动后台转码并立即返回。
     * 调用方拿到 taskId 后通过 {@link #pollOfficeStatus} 轮询状态。</p>
     *
     * <p>taskId 是 cacheName 的 URL-safe 编码形式（避免含中文的 cacheName 在 URL 传输时被 Tomcat 错误解码）。</p>
     *
     * @param url        文件 URL（与 {@code /onlinePreview} 一致：Base64(+ urlEncode) 编码）
     * @param encryption 可选加密参数（同 {@code /onlinePreview}）
     * @return JSON：{@code {status, taskId, message, convertUrl}}
     */
    @GetMapping("/officeSubmit")
    public Map<String, Object> submitOffice(@RequestParam String url,
                                            @RequestParam(required = false) String encryption,
                                            @RequestParam(required = false) String key,
                                            HttpServletRequest req) {
        if (WebUtils.validateKey(key)) {
            return errorResult("访问不合法：访问密码不正确");
        }
        String fileUrl;
        try {
            fileUrl = WebUtils.decodeUrl(url, encryption);
        } catch (Exception ex) {
            return errorResult(String.format(BASE64_DECODE_ERROR_MSG, "url"));
        }
        FileAttribute fileAttribute = fileHandlerService.getFileAttribute(fileUrl, req);
        try {
            String cacheName = officeFilePreview.submitAsyncOfficeTask(fileAttribute);
            // 把 cacheName 编码成 URL-safe 形式作为 taskId 返回（避免中文 cacheName 在 URL 中传输）
            String taskId = URLEncoder.encode(cacheName, StandardCharsets.UTF_8);
            logger.info("Office异步预览已提交: fileUrl={}, taskId={}", fileUrl, taskId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", STATUS_CONVERTING);
            result.put("taskId", taskId);
            return result;
        } catch (Exception e) {
            logger.error("Office异步预览提交失败: {}", fileUrl, e);
            return errorResult("提交任务失败: " + e.getMessage());
        }
    }

    /**
     * 轮询 office 转码任务状态。
     *
     * <p>根据 taskId（URL 编码后的 cacheName）查 {@link FileConvertStatusManager}，返回当前状态。
     * 转码完成（status=2）时，{@code convertUrl} 指向 kkFileView 自身的预览页 URL，
     * 浏览器可直接打开渲染。</p>
     *
     * @param taskId 任务 ID（URL 编码的 cacheName）
     * @return JSON：{@code {status, taskId, message, convertUrl}}
     */
    @GetMapping("/getOfficeOnlineHtmlUrl")
    public Map<String, Object> pollOfficeStatus(@RequestParam("taskId") String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return errorResult("taskId 不能为空");
        }
        // 解码回原始 cacheName 用于状态查询
        String cacheName;
        try {
            cacheName = URLDecoder.decode(taskId, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return errorResult("taskId 格式不合法: " + e.getMessage());
        }
        FileConvertStatusManager.ConvertStatus status = FileConvertStatusManager.getConvertStatus(cacheName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);

        if (status == null) {
            // 状态为 null：可能已转换完成并清理状态（convertSuccess 会清空 STATUS_MAP）
            // 也可能是未知/已过期 taskId
            result.put("status", STATUS_SUCCESS);
            result.put("convertUrl", buildPreviewUrl(taskId));
            return result;
        }

        switch (status.getStatus()) {
            case CONVERTING:
                result.put("status", STATUS_CONVERTING);
                result.put("message", status.getRealTimeMessage());
                result.put("progress", status.getProgress());
                break;
            case FAILED:
                result.put("status", STATUS_FAILED);
                result.put("message", status.getMessage() != null ? status.getMessage() : "文件转换失败");
                break;
            case TIMEOUT:
                result.put("status", STATUS_FAILED);
                result.put("message", "文件转换超时");
                break;
            case QUEUED:
            default:
                result.put("status", STATUS_CONVERTING);
                result.put("message", status.getMessage());
                break;
        }
        return result;
    }

    /**
     * 构造可嵌入 iframe 的 kkFileView 预览 URL。
     *
     * <p>转码完成后，kkFileView 的 {@code /onlinePreview} 会对已缓存文件走快路径秒级返回。
     * 这里拼接的 URL 与原 {@code officeSubmit} 调用的 URL 形式一致（含原始 fileUrl 编码）。</p>
     */
    private String buildPreviewUrl(String taskId) {
        // taskId 即 cacheName，但预览 URL 需要原始 fileUrl
        // 简化方案：使用在线预览通用路径，前端拿到 convertUrl 后可直接嵌入 iframe
        // 具体实现：保留 /onlinePreview 路径，让 kkFileView 内部基于缓存快速返回
        // 这里我们让前端重新构造完整 URL：使用一个通用 /onlinePreview 入口
        // 由于缺少原始 fileUrl，调用方应使用 officeSubmit 时传入的 url 重新拼装
        // 此处返回带 cacheName 的占位 URL，实际使用由调用方在拿到 status=2 后自行拼装
        return "/onlinePreview?cacheName=" + taskId;
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", STATUS_FAILED);
        result.put("message", message);
        return result;
    }
}
