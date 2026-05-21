<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, user-scalable=yes, initial-scale=1.0">
    <title>PDF预览</title>
    <#include "*/commonHeader.ftl">
    <script src="js/base64.min.js" type="text/javascript"></script>
    <style>
        /* 简单全屏布局，无滚动条 */
        html, body {
            margin: 0;
            padding: 0;
            height: 100%;
            overflow: hidden;
        }
        iframe {
            width: 100%;
            height: 100%;
            border: none;
            display: block;
        }
        .img-preview {
            position: fixed;
            bottom: 20px;
            right: 20px;
            cursor: pointer;
            z-index: 999;
            width: 48px;
            height: 48px;
        }

        /* 自定义缩放控制按钮 */
        .zoom-controls {
            display: none;
        }

        .zoom-btn {
            width: 36px;
            height: 36px;
            border: none;
            border-radius: 50%;
            background: #007bff;
            color: white;
            font-size: 18px;
            font-weight: bold;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.2s;
        }

        .zoom-btn:hover {
            background: #0056b3;
            transform: scale(1.1);
        }

        .zoom-btn:active {
            transform: scale(0.95);
        }

        .zoom-btn:disabled {
            background: #ccc;
            cursor: not-allowed;
            transform: none;
        }

        .zoom-display {
            min-width: 60px;
            text-align: center;
            font-size: 14px;
            color: #333;
            font-weight: 500;
        }

        .zoom-reset-btn {
            background: #6c757d;
            font-size: 14px;
        }

        .zoom-reset-btn:hover {
            background: #545b62;
        }
    </style>
</head>
<body>

<#if pdfUrl?contains("http://") || pdfUrl?contains("https://")>
    <#assign finalUrl="${pdfUrl}">
<#else>
    <#assign finalUrl="${baseUrl}${pdfUrl}">
</#if>

<iframe id="pdfFrame" src="about:blank"></iframe>

<#if "false" == switchDisabled>
    <img class="img-preview" src="images/jpg.svg" alt="使用图片预览" title="使用图片预览" onclick="goForImage()"/>
</#if>

<!-- 自定义缩放控制 -->
<div class="zoom-controls" id="zoomControls">
    <button class="zoom-btn" id="zoomOutBtn" title="缩小">−</button>
    <span class="zoom-display" id="zoomDisplay">100%</span>
    <button class="zoom-btn" id="zoomInBtn" title="放大">+</button>
    <button class="zoom-btn zoom-reset-btn" id="zoomResetBtn" title="重置">⟳</button>
</div>

<script type="text/javascript">
    var url = '${finalUrl}';
    var kkagent = '${kkagent}';
    var baseUrl = '${baseUrl}'.endsWith('/') ? '${baseUrl}' : '${baseUrl}' + '/';
    if (kkagent === 'true' || !url.startsWith(baseUrl)) {
        url = baseUrl + 'getCorsFile?urlPath=' + encodeURIComponent(Base64.encode(url)) + "&key=${kkkey}";
    }
    var viewerUrl = baseUrl + "pdfjs/web/viewer.html?file=" + encodeURIComponent(url);
	var watermarkEncoded = encodeURIComponent('${watermarkTxt?js_string}');
    var highlightEncoded = encodeURIComponent('${highlightall?js_string}');
    viewerUrl += "&disablepresentationmode=${pdfPresentationModeDisable}";
    viewerUrl += "&disableopenfile=${pdfOpenFileDisable}";
    viewerUrl += "&disableprint=${pdfPrintDisable}";
    viewerUrl += "&disabledownload=${pdfDownloadDisable}";
    viewerUrl += "&disablebookmark=${pdfBookmarkDisable}";
    viewerUrl += "&disableediting=${pdfDisableEditing}";
    viewerUrl += "&verbosity=0";
    viewerUrl += "&watermarktxt=" + watermarkEncoded;
    viewerUrl += "&pdfhighlightall=" + highlightEncoded;
    viewerUrl += "#page=${page}";   // ?c 确保数字不包含千位分隔符
<#if "true" == pdfSidebarOpen>
	viewerUrl += "&pagemode=thumbs";
<#else>
	viewerUrl += "&pagemode=none";
</#if>
    var iframe = document.getElementById('pdfFrame');
    var zoomControlsBound = false;
    var zoomDisplayTimer = null;
    iframe.src = viewerUrl;

    // 等待 iframe 加载完成后设置缩放控制
    iframe.onload = function() {
        setupZoomControls();
    };

    // 如果 iframe 已经加载完成（cached），立即设置
    if (iframe.src && iframe.src !== 'about:blank') {
        setTimeout(setupZoomControls, 100);
    }

    // 设置缩放控制
    function setupZoomControls() {
        if (zoomControlsBound) {
            return;
        }

        var zoomInBtn = document.getElementById('zoomInBtn');
        var zoomOutBtn = document.getElementById('zoomOutBtn');
        var zoomResetBtn = document.getElementById('zoomResetBtn');
        var zoomDisplay = document.getElementById('zoomDisplay');
        zoomControlsBound = true;

        // 查找 PDF.js 的 PDFViewerApplication
        function findPDFViewer() {
            try {
                return iframe.contentWindow.PDFViewerApplication;
            } catch (e) {
                return null;
            }
        }

        // 更新缩放显示
        function updateZoomDisplay() {
            try {
                var pdfViewer = findPDFViewer();
                if (pdfViewer && pdfViewer.pdfViewer) {
                    var scale = pdfViewer.pdfViewer.currentScale;
                    var percent = Math.round(scale * 100);
                    zoomDisplay.textContent = percent + '%';

                    // 更新按钮状态
                    zoomOutBtn.disabled = scale <= 0.1;
                    zoomInBtn.disabled = scale >= 10;
                }
            } catch (e) {
                // 忽略
            }
        }

        // 缩放按钮事件
        zoomInBtn.addEventListener('click', function() {
            try {
                var pdfViewer = findPDFViewer();
                if (pdfViewer && pdfViewer.zoomIn) {
                    pdfViewer.zoomIn();
                    setTimeout(updateZoomDisplay, 50);
                }
            } catch (e) {
                console.log('Zoom in error:', e);
            }
        });

        zoomOutBtn.addEventListener('click', function() {
            try {
                var pdfViewer = findPDFViewer();
                if (pdfViewer && pdfViewer.zoomOut) {
                    pdfViewer.zoomOut();
                    setTimeout(updateZoomDisplay, 50);
                }
            } catch (e) {
                console.log('Zoom out error:', e);
            }
        });

        zoomResetBtn.addEventListener('click', function() {
            try {
                var pdfViewer = findPDFViewer();
                if (pdfViewer && pdfViewer.zoomReset) {
                    pdfViewer.zoomReset();
                    setTimeout(updateZoomDisplay, 50);
                }
            } catch (e) {
                console.log('Zoom reset error:', e);
            }
        });

        // 定期更新缩放显示以同步内部状态
        updateZoomDisplay();
        if (zoomDisplayTimer) {
            clearInterval(zoomDisplayTimer);
        }
        zoomDisplayTimer = setInterval(updateZoomDisplay, 500);
    }

    // 图片预览切换
    function goForImage() {
        var href = window.location.href;
        if (href.indexOf("officePreviewType=pdf") !== -1) {
            href = href.replace("officePreviewType=pdf", "officePreviewType=image");
        } else {
            href += (href.indexOf('?') === -1 ? '?' : '&') + "officePreviewType=image";
        }
        window.location.href = href;
    }
</script>
</body>
</html>
