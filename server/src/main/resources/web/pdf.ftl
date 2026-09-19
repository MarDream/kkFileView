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

<#-- 在线批注：工具栏与画布由 collab-annotation.js 注入，样式在 /css/collab.css。
     批注画布逐页锚定到 pdfjs viewer（iframe 内同源）的 .page 元素上，
     页码即元素 data-page-number，缩放/翻页后批注随页面元素自动跟随 -->
<#if collaborationAnnotationEnabled!false>
    <link rel="stylesheet" href="/css/collab.css"/>
    <script src="/js/collab-annotation.js"></script>
    <script>
        initCollabAnnotation({
            fileKey: window.location.href,
            // pdfjs viewer 与本页同源，逐页返回 .page 元素与页码；
            // viewer 异步加载，组件内部会轮询等待。跨域等异常时兜底返回 []
            getPageElements: function () {
                try {
                    var doc = document.getElementById('pdfFrame').contentDocument;
                    if (!doc) {
                        return [];
                    }
                    var pages = [];
                    var pageEls = doc.querySelectorAll('.page');
                    for (var i = 0; i < pageEls.length; i++) {
                        var pageNumber = parseInt(pageEls[i].dataset.pageNumber, 10);
                        if (pageNumber > 0) {
                            pages.push({ el: pageEls[i], pageNumber: pageNumber });
                        }
                    }
                    return pages;
                } catch (e) {
                    return [];
                }
            },
            showDelay: 2000
        });
    </script>
</#if>

<#-- 在线协作编辑：presence + 远程光标（SockJS/STOMP 库由 collab-stomp.js 按需动态加载） -->
<#if collaborationEditEnabled!false>
    <link rel="stylesheet" href="/css/collab.css"/>
    <script src="/js/collab-stomp.js"></script>
    <script>
        /**
         * 渲染远程协作者光标指示点（本页原有逻辑，随公共组件回调保留）
         * @param {{userId?: string, nickname?: string, color?: string, x?: number, y?: number}} cursor 光标数据
         */
        function updateRemoteCursor(cursor) {
            if (!cursor || !cursor.userId) return;
            let indicator = document.getElementById('remote-cursor-' + cursor.userId);
            if (!indicator) {
                indicator = document.createElement('div');
                indicator.id = 'remote-cursor-' + cursor.userId;
                indicator.style.cssText = 'position:fixed;width:12px;height:12px;border-radius:50%;pointer-events:none;z-index:10001;transition:all 0.1s;';
                document.body.appendChild(indicator);
            }
            indicator.style.background = cursor.color || '#999';
            indicator.style.left = (cursor.x || 0) + 'px';
            indicator.style.top = (cursor.y || 0) + 'px';
            indicator.title = cursor.nickname || cursor.userId;
        }

        initCollabStomp({
            fileKey: window.location.href,
            enableCursor: true,
            onCursor: updateRemoteCursor
        });
    </script>
</#if>

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
    viewerUrl += "&disablepresentationmode=true";
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
