package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.utils.RemoveSvgAdSimple;
import jakarta.annotation.PreDestroy;
import org.jodconverter.core.util.OSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * CAD文件转换服务
 * 使用反射调用 Aspose CAD，使默认构建不再被私有仓库依赖阻塞。
 */
@Component
public class CadToPdfService {
    private static final Logger logger = LoggerFactory.getLogger(CadToPdfService.class);
    private static final AsposeCadBridge ASPOSE_CAD = AsposeCadBridge.load();

    private final ExecutorService virtualThreadExecutor;
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> taskCompletionStatus = new ConcurrentHashMap<>();
    private final Semaphore concurrentLimit;
    private final long conversionTimeout;

    public CadToPdfService() {
        int maxConcurrent = getConcurrentLimit();
        this.concurrentLimit = new Semaphore(maxConcurrent);
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.conversionTimeout = getConversionTimeout();

        logger.info("CAD转换服务初始化完成，最大并发数: {}，转换超时: {}秒", maxConcurrent, conversionTimeout);
    }

    public boolean isAsposeCadAvailable() {
        return ASPOSE_CAD.isAvailable();
    }

    public String getAsposeCadAvailabilityMessage() {
        return ASPOSE_CAD.getAvailabilityMessage();
    }

    /**
     * CAD文件转换 - 异步版本
     */
    public CompletableFuture<Boolean> cadToPdfAsync(String inputFilePath, String outputFilePath, String cacheName,
                                                    String cadPreviewType, FileAttribute fileAttribute) {
        return submitConversionTask(cacheName, outputFilePath,
                () -> performCadConversion(inputFilePath, outputFilePath, cacheName, cadPreviewType, fileAttribute),
                true);
    }

    /**
     * cadViewer转换
     */
    public CompletableFuture<Boolean> cadViewerConvert(String sDwgFile, String outFilePath, String cachefilepath,
                                                       String cadPreviewType, String cacheName) {
        return submitConversionTask(cacheName, outFilePath + "/" + cacheName,
                () -> executeCadViewerConversion(sDwgFile, outFilePath, cachefilepath, cadPreviewType, cacheName),
                false);
    }

    private CompletableFuture<Boolean> submitConversionTask(String cacheName, String outputFilePath,
                                                            Supplier<Boolean> conversionSupplier,
                                                            boolean deleteOnTimeout) {
        FileConvertStatusManager.startConvert(cacheName);
        CompletableFuture<Boolean> taskFuture = new CompletableFuture<>();
        taskCompletionStatus.put(cacheName, new AtomicBoolean(false));

        Future<?> future = virtualThreadExecutor.submit(() -> {
            try {
                FileConvertStatusManager.updateProgress(cacheName, "正在启动转换任务", 5);
                boolean result = conversionSupplier.get();
                if (result) {
                    taskFuture.complete(true);
                    taskCompletionStatus.get(cacheName).set(true);
                } else {
                    taskFuture.complete(false);
                }
            } catch (Exception e) {
                logger.error("CAD转换任务执行失败: {}", cacheName, e);
                FileConvertStatusManager.markError(cacheName, "转换过程异常: " + e.getMessage());
                taskFuture.completeExceptionally(e);
            } finally {
                runningTasks.remove(cacheName);
                taskCompletionStatus.remove(cacheName);
            }
        });

        runningTasks.put(cacheName, future);
        scheduleTimeoutCheck(cacheName, taskFuture, future, outputFilePath, deleteOnTimeout);
        return taskFuture;
    }

    private void scheduleTimeoutCheck(String fileName, CompletableFuture<Boolean> taskFuture,
                                      Future<?> future, String outputFilePath, boolean deleteOnTimeout) {
        virtualThreadExecutor.submit(() -> {
            try {
                taskFuture.get(conversionTimeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                handleConversionTimeout(fileName, taskFuture, future, outputFilePath, deleteOnTimeout);
            } catch (Exception e) {
                handleConversionException(fileName, taskFuture, e);
            }
        });
    }

    private void handleConversionTimeout(String fileName, CompletableFuture<Boolean> taskFuture,
                                         Future<?> future, String outputFilePath, boolean deleteOnTimeout) {
        logger.error("CAD转换超时，取消任务: {}, 超时时间: {}秒", fileName, conversionTimeout);
        FileConvertStatusManager.markTimeout(fileName);
        cancelRunningTask(fileName, future);
        if (deleteOnTimeout) {
            deleteIncompleteFile(outputFilePath);
        }
        taskFuture.complete(false);
    }

    private void handleConversionException(String fileName, CompletableFuture<Boolean> taskFuture, Exception e) {
        logger.error("CAD转换异常: {}", fileName, e);
        FileConvertStatusManager.markError(fileName, "转换任务异常: " + e.getMessage());
        taskFuture.complete(false);
    }

    private boolean performCadConversion(String inputFilePath, String outputFilePath, String cacheName,
                                         String cadPreviewType, FileAttribute fileAttribute) {
        return executeWithConcurrencyControl(cacheName, () -> {
            try {
                if (!validateInputParameters(inputFilePath, outputFilePath, cadPreviewType)) {
                    FileConvertStatusManager.markError(cacheName, "文件参数验证失败");
                    return false;
                }
                ASPOSE_CAD.ensureAvailable();

                FileConvertStatusManager.updateProgress(cacheName, "正在准备输出目录", 30);
                createOutputDirectoryIfNeeded(outputFilePath, fileAttribute.isCompressFile());

                FileConvertStatusManager.updateProgress(cacheName, "正在加载CAD文件", 40);
                Object cadImage = ASPOSE_CAD.loadImage(inputFilePath);
                try {
                    FileConvertStatusManager.updateProgress(cacheName, "CAD文件加载完成，开始渲染", 50);
                    Object rasterizationOptions = ASPOSE_CAD.createRasterizationOptions(cadImage);
                    Object options = ASPOSE_CAD.createConversionOptions(cadPreviewType, rasterizationOptions);

                    FileConvertStatusManager.updateProgress(cacheName, "正在生成输出文件", 80);
                    ASPOSE_CAD.save(outputFilePath, cadImage, options);

                    FileConvertStatusManager.updateProgress(cacheName, "文件转换完成", 90);
                    if ("svg".equalsIgnoreCase(cadPreviewType) && ConfigConstants.getCadwatermark()) {
                        postProcessSvgFile(outputFilePath);
                    }

                    FileConvertStatusManager.updateProgress(cacheName, "转换成功", 100);
                    FileConvertStatusManager.convertSuccess(cacheName);
                    return true;
                } finally {
                    ASPOSE_CAD.close(cadImage);
                }
            } catch (Exception e) {
                logger.error("CAD转换执行失败: {}", cacheName, e);
                FileConvertStatusManager.markError(cacheName, "转换失败: " + e.getMessage());
                deleteIncompleteFile(outputFilePath);
                return false;
            }
        }, "正在启动转换", "已获取转换资源，开始转换");
    }

    private boolean executeCadViewerConversion(String sDwgFile, String outFilePath, String cachefilepath,
                                               String cadPreviewType, String cacheName) {
        return executeWithConcurrencyControl(cacheName, () -> {
            try {
                if (!validateInputParameters(sDwgFile, outFilePath + "/" + cacheName, cadPreviewType)) {
                    FileConvertStatusManager.markError(cacheName, "文件参数验证失败");
                    return false;
                }

                FileConvertStatusManager.updateProgress(cacheName, "正在准备输出目录", 30);
                createOutputDirectoryForExternal(outFilePath, cacheName);

                FileConvertStatusManager.updateProgress(cacheName, "正在启动外部CAD转换器", 40);
                String result = executeExternalCadViewer(sDwgFile, outFilePath, cachefilepath, cadPreviewType, cacheName);

                if (result != null && result.contains("Elapsed Time")) {
                    FileConvertStatusManager.updateProgress(cacheName, "转换成功", 100);
                    FileConvertStatusManager.convertSuccess(cacheName);
                    return true;
                }

                FileConvertStatusManager.markError(cacheName, "外部转换失败: " + result);
                return false;
            } catch (Exception e) {
                logger.error("外部CAD转换执行失败: {}", cacheName, e);
                FileConvertStatusManager.markError(cacheName, "外部转换失败: " + e.getMessage());
                return false;
            }
        }, "正在启动外部CAD转换", "已获取转换资源，开始外部转换");
    }

    private boolean executeWithConcurrencyControl(String cacheName, Supplier<Boolean> task,
                                                  String startMessage, String acquiredMessage) {
        try {
            FileConvertStatusManager.updateProgress(cacheName, startMessage, 10);
            if (!concurrentLimit.tryAcquire(30, TimeUnit.SECONDS)) {
                FileConvertStatusManager.updateProgress(cacheName, "系统繁忙，等待资源中...", 15);
                throw new TimeoutException("系统繁忙，请稍后重试");
            }

            FileConvertStatusManager.updateProgress(cacheName, acquiredMessage, 20);
            return task.get();
        } catch (TimeoutException e) {
            FileConvertStatusManager.markError(cacheName, "获取转换资源超时");
            return false;
        } catch (Exception e) {
            FileConvertStatusManager.markError(cacheName, "转换过程异常: " + e.getMessage());
            return false;
        } finally {
            concurrentLimit.release();
        }
    }

    private String executeExternalCadViewer(String sDwgFile, String outFilePath, String cachefilepath,
                                            String cadPreviewType, String cacheName) throws Exception {
        boolean isWindows = OSUtils.IS_OS_WINDOWS;
        String encoding = isWindows ? "gbk" : "utf-8";
        outFilePath = outFilePath.replace(cacheName, "");

        List<String> command = buildExternalCommand(sDwgFile, outFilePath, cachefilepath, cadPreviewType, cacheName, isWindows);

        FileConvertStatusManager.updateProgress(cacheName, "正在执行外部转换器", 60);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<String> conversionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeProcessWithVirtualThreads(command, cachefilepath, encoding);
                } catch (Exception e) {
                    throw new CompletionException("外部CAD转换失败", e);
                }
            }, executor);

            FileConvertStatusManager.updateProgress(cacheName, "正在等待转换完成", 80);
            return conversionFuture.get(Integer.parseInt(ConfigConstants.getCadTimeout()), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new Exception("外部转换超时，已终止任务", e);
        }
    }

    private List<String> buildExternalCommand(String sDwgFile, String outFilePath, String cachefilepath,
                                              String cadPreviewType, String cacheName, boolean isWindows) {
        // 安全检查：验证所有参数不包含命令注入危险字符
        validateCommandParameters(sDwgFile, outFilePath, cachefilepath, cacheName, cadPreviewType);

        List<String> command = new ArrayList<>();
        command.add(isWindows ? cachefilepath + "cadviewer.exe" : "./cadviewer");
        command.add("-i=\"" + sDwgFile + "\"");
        command.add("-o=\"" + outFilePath + "/" + cacheName + "\"");
        command.add("-f=\"" + cadPreviewType + "\"");
        command.add("-basic");
        command.add("-lpath=\"" + cachefilepath + "\"");
        command.add("-xpath=\"" + cachefilepath + "files/\"");
        command.add("-fpath=\"" + cachefilepath + "fonts/\"");
        return command;
    }

    /**
     * 验证命令参数的安全性，防止命令注入攻击
     * 检查参数中是否包含危险字符（引号、管道符、分号等）
     */
    private void validateCommandParameters(String inputPath, String outputPath, String cachePath,
                                            String cacheName, String previewType) {
        // 检查输入文件路径
        if (containsCommandInjectionChars(inputPath)) {
            logger.error("输入文件路径包含危险字符，拒绝执行: {}", sanitizeForLog(inputPath));
            throw new SecurityException("Invalid input file path: contains dangerous characters");
        }
        // 检查输出路径
        if (containsCommandInjectionChars(outputPath)) {
            logger.error("输出路径包含危险字符，拒绝执行: {}", sanitizeForLog(outputPath));
            throw new SecurityException("Invalid output path: contains dangerous characters");
        }
        // 检查缓存路径
        if (containsCommandInjectionChars(cachePath)) {
            logger.error("缓存路径包含危险字符，拒绝执行: {}", sanitizeForLog(cachePath));
            throw new SecurityException("Invalid cache path: contains dangerous characters");
        }
        // 检查缓存名称
        if (containsCommandInjectionChars(cacheName)) {
            logger.error("缓存名称包含危险字符，拒绝执行: {}", sanitizeForLog(cacheName));
            throw new SecurityException("Invalid cache name: contains dangerous characters");
        }
        // 检查预览类型（只允许已知类型）
        if (!isSupportedPreviewType(previewType)) {
            logger.error("不支持预览类型: {}", sanitizeForLog(previewType));
            throw new SecurityException("Unsupported preview type");
        }
    }

    /**
     * 检查字符串是否包含命令注入危险字符
     */
    private boolean containsCommandInjectionChars(String str) {
        if (str == null) {
            return false;
        }
        // 危险字符列表：引号、管道符、分号、反引号、换行符等
        return str.contains("\"") || str.contains("'") ||
               str.contains("|") || str.contains(";") ||
               str.contains("`") || str.contains("\n") ||
               str.contains("\r") || str.contains("&") ||
               str.contains("$") || str.contains(">") ||
               str.contains("<") || str.contains("||") ||
               str.contains("&&");
    }

    /**
     * 为日志输出净化字符串，移除敏感信息
     */
    private String sanitizeForLog(String str) {
        if (str == null) {
            return "null";
        }
        // 只显示前50个字符，防止日志注入
        String truncated = str.length() > 50 ? str.substring(0, 50) + "..." : str;
        // 移除控制字符
        return truncated.replaceAll("[\\p{Cntrl}]", "?");
    }

    private boolean validateInputParameters(String inputFilePath, String outputFilePath, String cadPreviewType) {
        if (inputFilePath == null || inputFilePath.trim().isEmpty() || outputFilePath == null || outputFilePath.trim().isEmpty()) {
            return false;
        }
        File inputFile = new File(inputFilePath);
        return inputFile.exists() && isSupportedPreviewType(cadPreviewType);
    }

    private boolean isSupportedPreviewType(String previewType) {
        if (previewType == null) {
            return false;
        }
        return switch (previewType.toLowerCase(Locale.ROOT)) {
            case "svg", "pdf", "tif", "tiff" -> true;
            default -> false;
        };
    }

    private void createOutputDirectoryIfNeeded(String outputFilePath, boolean isCompressFile) {
        if (isCompressFile) {
            createDirectory(new File(outputFilePath).getParentFile());
        }
    }

    private void createOutputDirectoryForExternal(String outputFilePath, String cacheName) {
        createDirectory(new File(outputFilePath.replace(cacheName, "")));
    }

    private void createDirectory(File dir) {
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("无法创建输出目录: " + dir.getAbsolutePath());
        }
    }

    private int getConcurrentLimit() {
        try {
            int threadCount = ConfigConstants.getCadThread();
            if (threadCount <= 0) {
                return Math.max(1, Runtime.getRuntime().availableProcessors());
            }
            return Math.min(threadCount, Runtime.getRuntime().availableProcessors() * 4);
        } catch (Exception e) {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
    }

    private long getConversionTimeout() {
        try {
            long timeout = Long.parseLong(ConfigConstants.getCadTimeout());
            return timeout > 0 ? timeout : 300L;
        } catch (NumberFormatException e) {
            return 300L;
        }
    }

    private void postProcessSvgFile(String outputFilePath) {
        try {
            RemoveSvgAdSimple.removeSvgAdFromFile(outputFilePath);
        } catch (Exception e) {
            logger.warn("SVG文件后处理失败: {}", outputFilePath, e);
        }
    }

    private void cancelRunningTask(String fileName, Future<?> future) {
        if (future != null && future.cancel(true)) {
            logger.info("成功取消转换任务: {}", fileName);
        }
    }

    private void deleteIncompleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            logger.warn("无法删除不完整文件: {}", filePath);
        }
    }

    private static String executeProcessWithVirtualThreads(List<String> command, String workingDir, String encoding) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (!OSUtils.IS_OS_WINDOWS && !"false".equals(workingDir)) {
            processBuilder.directory(new File(workingDir));
        }
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try {
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return readProcessOutput(process, encoding);
                } catch (IOException e) {
                    throw new CompletionException("读取进程输出失败", e);
                }
            });

            process.waitFor();
            return outputFuture.get();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String readProcessOutput(Process process, String encoding) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    public void cancelConversion(String fileName) {
        Future<?> future = runningTasks.get(fileName);
        if (future != null && future.cancel(true)) {
            logger.info("成功取消转换任务: {}", fileName);
            runningTasks.remove(fileName);
            FileConvertStatusManager.markError(fileName, "转换已取消");
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("开始关闭CAD转换服务，正在运行的任务数: {}", runningTasks.size());
        runningTasks.keySet().forEach(this::cancelConversion);

        if (virtualThreadExecutor != null && !virtualThreadExecutor.isShutdown()) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                virtualThreadExecutor.shutdownNow();
            }
        }
    }

    private static final class AsposeCadBridge {
        private static final String ENABLE_HINT = "Aspose CAD 运行时不可用，请使用 -Pwith-aspose-cad 构建，或配置 cad.cadconverterpath 启用外部 CAD 转换器。";

        private final boolean available;
        private final String availabilityMessage;
        private final Class<?> imageClass;
        private final Class<?> loadOptionsClass;
        private final Class<?> imageOptionsBaseClass;
        private final Class<?> cadRasterizationOptionsClass;
        private final Class<?> rasterizationQualityClass;
        private final Class<?> rasterizationQualityValueClass;
        private final Class<?> colorClass;
        private final Class<?> svgOptionsClass;
        private final Class<?> pdfOptionsClass;
        private final Class<?> tiffOptionsClass;
        private final Class<?> tiffExpectedFormatClass;
        private final Class<?> visibilityModeClass;
        private final Class<?> cadDrawTypeModeClass;
        private final Class<?> codePagesClass;

        private AsposeCadBridge(boolean available, String availabilityMessage,
                                Class<?> imageClass, Class<?> loadOptionsClass, Class<?> imageOptionsBaseClass,
                                Class<?> cadRasterizationOptionsClass, Class<?> rasterizationQualityClass,
                                Class<?> rasterizationQualityValueClass, Class<?> colorClass,
                                Class<?> svgOptionsClass, Class<?> pdfOptionsClass, Class<?> tiffOptionsClass,
                                Class<?> tiffExpectedFormatClass, Class<?> visibilityModeClass,
                                Class<?> cadDrawTypeModeClass, Class<?> codePagesClass) {
            this.available = available;
            this.availabilityMessage = availabilityMessage;
            this.imageClass = imageClass;
            this.loadOptionsClass = loadOptionsClass;
            this.imageOptionsBaseClass = imageOptionsBaseClass;
            this.cadRasterizationOptionsClass = cadRasterizationOptionsClass;
            this.rasterizationQualityClass = rasterizationQualityClass;
            this.rasterizationQualityValueClass = rasterizationQualityValueClass;
            this.colorClass = colorClass;
            this.svgOptionsClass = svgOptionsClass;
            this.pdfOptionsClass = pdfOptionsClass;
            this.tiffOptionsClass = tiffOptionsClass;
            this.tiffExpectedFormatClass = tiffExpectedFormatClass;
            this.visibilityModeClass = visibilityModeClass;
            this.cadDrawTypeModeClass = cadDrawTypeModeClass;
            this.codePagesClass = codePagesClass;
        }

        static AsposeCadBridge load() {
            try {
                return new AsposeCadBridge(
                        true,
                        "Aspose CAD 运行时可用",
                        Class.forName("com.aspose.cad.Image"),
                        Class.forName("com.aspose.cad.LoadOptions"),
                        Class.forName("com.aspose.cad.imageoptions.ImageOptionsBase"),
                        Class.forName("com.aspose.cad.imageoptions.CadRasterizationOptions"),
                        Class.forName("com.aspose.cad.imageoptions.RasterizationQuality"),
                        Class.forName("com.aspose.cad.imageoptions.RasterizationQualityValue"),
                        Class.forName("com.aspose.cad.Color"),
                        Class.forName("com.aspose.cad.imageoptions.SvgOptions"),
                        Class.forName("com.aspose.cad.imageoptions.PdfOptions"),
                        Class.forName("com.aspose.cad.imageoptions.TiffOptions"),
                        Class.forName("com.aspose.cad.fileformats.tiff.enums.TiffExpectedFormat"),
                        Class.forName("com.aspose.cad.imageoptions.VisibilityMode"),
                        Class.forName("com.aspose.cad.fileformats.cad.CadDrawTypeMode"),
                        Class.forName("com.aspose.cad.CodePages"));
            } catch (ClassNotFoundException e) {
                logger.warn(ENABLE_HINT);
                return new AsposeCadBridge(false, ENABLE_HINT, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null);
            }
        }

        boolean isAvailable() {
            return available;
        }

        String getAvailabilityMessage() {
            return availabilityMessage;
        }

        void ensureAvailable() {
            if (!available) {
                throw new IllegalStateException(availabilityMessage);
            }
        }

        Object loadImage(String inputFilePath) {
            ensureAvailable();
            try {
                Object loadOptions = loadOptionsClass.getDeclaredConstructor().newInstance();
                invoke(loadOptions, "setSpecifiedEncoding", codePagesClass, enumConstant(codePagesClass, "SimpChinese"));
                return invokeStatic(imageClass, "load", new Class<?>[]{String.class, loadOptionsClass}, inputFilePath, loadOptions);
            } catch (ReflectiveOperationException e) {
                throw wrap("加载 Aspose CAD 文件失败", e);
            }
        }

        Object createRasterizationOptions(Object cadImage) {
            try {
                Object quality = rasterizationQualityClass.getDeclaredConstructor().newInstance();
                Object highQuality = enumConstant(rasterizationQualityValueClass, "High");
                invoke(quality, "setArc", rasterizationQualityValueClass, highQuality);
                invoke(quality, "setHatch", rasterizationQualityValueClass, highQuality);
                invoke(quality, "setText", rasterizationQualityValueClass, highQuality);
                invoke(quality, "setOle", rasterizationQualityValueClass, highQuality);
                invoke(quality, "setObjectsPrecision", rasterizationQualityValueClass, highQuality);
                invoke(quality, "setTextThicknessNormalization", boolean.class, true);

                Object options = cadRasterizationOptionsClass.getDeclaredConstructor().newInstance();
                Object white = invokeStatic(colorClass, "getWhite", new Class<?>[0]);
                invoke(options, "setBackgroundColor", colorClass, white);
                invoke(options, "setPageWidth", int.class, ((Number) invoke(cadImage, "getWidth")).intValue());
                invoke(options, "setPageHeight", int.class, ((Number) invoke(cadImage, "getHeight")).intValue());
                invoke(options, "setUnitType", invoke(cadImage, "getUnitType").getClass(), invoke(cadImage, "getUnitType"));
                invoke(options, "setAutomaticLayoutsScaling", boolean.class, false);
                invoke(options, "setNoScaling", boolean.class, false);
                invoke(options, "setQuality", rasterizationQualityClass, quality);
                invoke(options, "setDrawType", cadDrawTypeModeClass, enumConstant(cadDrawTypeModeClass, "UseObjectColor"));
                invoke(options, "setExportAllLayoutContent", boolean.class, true);
                invoke(options, "setVisibilityMode", visibilityModeClass, enumConstant(visibilityModeClass, "AsScreen"));
                return options;
            } catch (ReflectiveOperationException e) {
                throw wrap("创建 Aspose CAD 渲染选项失败", e);
            }
        }

        Object createConversionOptions(String cadPreviewType, Object rasterizationOptions) {
            try {
                return switch (cadPreviewType.toLowerCase(Locale.ROOT)) {
                    case "svg" -> createVectorOptions(svgOptionsClass, rasterizationOptions);
                    case "pdf" -> createVectorOptions(pdfOptionsClass, rasterizationOptions);
                    case "tif", "tiff" -> createTiffOptions(rasterizationOptions);
                    default -> throw new IllegalArgumentException("不支持的预览类型: " + cadPreviewType);
                };
            } catch (ReflectiveOperationException e) {
                throw wrap("创建 Aspose CAD 输出选项失败", e);
            }
        }

        void save(String outputFilePath, Object cadImage, Object options) {
            try (OutputStream outputStream = new FileOutputStream(outputFilePath)) {
                invoke(cadImage, "save", new Class<?>[]{OutputStream.class, imageOptionsBaseClass}, outputStream, options);
            } catch (IOException | ReflectiveOperationException e) {
                throw wrap("保存 Aspose CAD 输出文件失败", e);
            }
        }

        void close(Object cadImage) {
            if (cadImage == null) {
                return;
            }
            try {
                invoke(cadImage, "close");
            } catch (ReflectiveOperationException e) {
                logger.debug("关闭 Aspose CAD 资源失败", e);
            }
        }

        private Object createVectorOptions(Class<?> optionsClass, Object rasterizationOptions) throws ReflectiveOperationException {
            Object options = optionsClass.getDeclaredConstructor().newInstance();
            invoke(options, "setVectorRasterizationOptions", cadRasterizationOptionsClass, rasterizationOptions);
            return options;
        }

        private Object createTiffOptions(Object rasterizationOptions) throws ReflectiveOperationException {
            Object tiffOptions = tiffOptionsClass
                    .getDeclaredConstructor(tiffExpectedFormatClass)
                    .newInstance(enumConstant(tiffExpectedFormatClass, "TiffJpegRgb"));
            invokeOptional(tiffOptions, "setVectorRasterizationOptions", cadRasterizationOptionsClass, rasterizationOptions);
            return tiffOptions;
        }

        private static RuntimeException wrap(String message, Exception e) {
            Throwable cause = e instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getTargetException()
                    : e;
            return new IllegalStateException(message + ": " + cause.getMessage(), cause);
        }

        private static Object invokeStatic(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... args)
                throws ReflectiveOperationException {
            return type.getMethod(methodName, parameterTypes).invoke(null, args);
        }

        private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
            return target.getClass().getMethod(methodName).invoke(target);
        }

        private static Object invoke(Object target, String methodName, Class<?> parameterType, Object arg)
                throws ReflectiveOperationException {
            return target.getClass().getMethod(methodName, parameterType).invoke(target, arg);
        }

        private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
                throws ReflectiveOperationException {
            return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
        }

        private static void invokeOptional(Object target, String methodName, Class<?> parameterType, Object arg)
                throws ReflectiveOperationException {
            try {
                invoke(target, methodName, parameterType, arg);
            } catch (NoSuchMethodException ignored) {
                // Older Aspose variants may not expose this hook for TIFF options.
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Object enumConstant(Class<?> enumType, String name) {
            Class<? extends Enum> typedEnum = enumType.asSubclass(Enum.class);
            return Enum.valueOf(typedEnum, name);
        }
    }
}
