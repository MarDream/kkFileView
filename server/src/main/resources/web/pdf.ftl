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

<#if collaborationAnnotationEnabled!false>
<!-- 批注工具栏 -->
<div id="annotationToolbar" style="position: fixed; top: 12px; left: 50%; transform: translateX(-50%); z-index: 10000; background: white; padding: 8px 16px; border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,0.15); display: none;">
    <button class="anno-tool" data-tool="highlight" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">高亮</button>
    <button class="anno-tool" data-tool="underline" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">下划线</button>
    <button class="anno-tool" data-tool="text" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">文本批注</button>
    <button class="anno-tool" data-tool="draw" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">自由绘制</button>
    <button id="toggleAnnotations" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #007bff; background: #007bff; color: white; cursor: pointer; border-radius: 4px;">显示批注</button>
</div>

<!-- 批注画布层 -->
<canvas id="annotationCanvas" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 9999;"></canvas>

<script>
(function() {
    const canvas = document.getElementById('annotationCanvas');
    const ctx = canvas.getContext('2d');
    const toolbar = document.getElementById('annotationToolbar');
    let currentTool = null;
    let isDrawing = false;
    let annotations = [];
    let fileKey = window.location.href; // 用 URL 作为 fileKey

    // 初始化画布大小
    function resizeCanvas() {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
        redrawAnnotations();
    }

    // 调整画布大小
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();

    // 显示/隐藏工具栏
    setTimeout(() => {
        toolbar.style.display = 'block';
    }, 2000);

    // 工具按钮点击
    document.querySelectorAll('.anno-tool').forEach(btn => {
        btn.addEventListener('click', function() {
            currentTool = this.dataset.tool;
            canvas.style.pointerEvents = 'auto';
            document.querySelectorAll('.anno-tool').forEach(b => b.style.background = 'white');
            this.style.background = '#e0e0e0';
        });
    });

    // 显示批注按钮
    document.getElementById('toggleAnnotations').addEventListener('click', async function() {
        if (annotations.length === 0) {
            await loadAnnotations();
        } else {
            redrawAnnotations();
        }
    });

    // 鼠标事件
    canvas.addEventListener('mousedown', function(e) {
        if (!currentTool) return;
        isDrawing = true;
        this.startX = e.clientX;
        this.startY = e.clientY;
    });

    canvas.addEventListener('mouseup', async function(e) {
        if (!isDrawing || !currentTool) return;
        isDrawing = false;

        const annotation = {
            id: Date.now().toString(),
            fileKey: fileKey,
            page: 1,
            x: this.startX,
            y: this.startY,
            width: e.clientX - this.startX,
            height: e.clientY - this.startY,
            content: '',
            authorName: '匿名用户',
            color: currentTool === 'highlight' ? '#ffff00' : '#ff0000',
            createdAt: new Date().toISOString()
        };

        // 文本批注需要输入内容
        if (currentTool === 'text') {
            const content = prompt('请输入批注内容:');
            if (!content) return;
            annotation.content = content;
        }

        annotations.push(annotation);
        redrawAnnotations();
        await saveAnnotations();
    });

    // 重绘批注
    function redrawAnnotations() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        annotations.forEach(anno => {
            ctx.fillStyle = anno.color || '#ffff00';
            ctx.globalAlpha = 0.3;
            ctx.fillRect(anno.x, anno.y, anno.width, anno.height);

            if (anno.content) {
                ctx.fillStyle = '#000';
                ctx.globalAlpha = 1;
                ctx.font = '12px sans-serif';
                ctx.fillText(anno.content, anno.x, anno.y - 5);
            }
        });
    }

    // 加载批注
    async function loadAnnotations() {
        try {
            const response = await fetch('/api/annotation/list?fileKey=' + encodeURIComponent(fileKey));
            if (response.ok) {
                const data = await response.json();
                annotations = data || [];
                redrawAnnotations();
            }
        } catch (err) {
            console.error('加载批注失败:', err);
        }
    }

    // 保存批注
    async function saveAnnotations() {
        try {
            await fetch('/api/annotation/save', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({fileKey: fileKey, annotations: annotations})
            });
        } catch (err) {
            console.error('保存批注失败:', err);
        }
    }

    // 页面加载后加载批注
    loadAnnotations();
})();
</script>
</#if>

<#if collaborationEditEnabled!false>
<!-- 在线用户列表 -->
<div id="onlineUsers" style="position: fixed; top: 60px; right: 16px; z-index: 10000; background: white; padding: 10px; border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,0.15); width: 150px; display: none;">
    <div style="font-weight: bold; margin-bottom: 8px;">在线用户</div>
    <div id="onlineUsersList"></div>
</div>

<!-- 加载 SockJS 和 STOMP 客户端 -->
<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>

<script>
(function() {
    const fileKey = window.location.href;
    let stompClient = null;
    let sessionId = null;
    let currentUser = null;

    function connectWebSocket() {
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            console.warn('SockJS/STOMP 客户端未加载，跳过协作连接');
            return;
        }
        const socket = new SockJS('/ws-collab');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;

        stompClient.connect({}, function(frame) {
            console.log('已连接到协作服务');
            const usersBox = document.getElementById('onlineUsers');
            if (usersBox) usersBox.style.display = 'block';

            fetch('/api/collab/session?fileKey=' + encodeURIComponent(fileKey), {method: 'POST'})
                .then(res => res.json())
                .then(data => {
                    sessionId = data.sessionId;
                    stompClient.subscribe('/topic/presence/' + sessionId, function(message) {
                        const payload = JSON.parse(message.body);
                        updateOnlineUsers(payload.onlineUsers || []);
                    });
                    stompClient.subscribe('/topic/session/' + sessionId, function(message) {
                        const payload = JSON.parse(message.body);
                        handleOperation(payload);
                    });
                    stompClient.subscribe('/topic/cursor/' + sessionId, function(message) {
                        const payload = JSON.parse(message.body);
                        updateRemoteCursor(payload);
                    });

                    currentUser = {
                        nickname: '用户' + Math.floor(Math.random() * 1000),
                        color: '#' + Math.floor(Math.random()*16777215).toString(16).padStart(6, '0')
                    };
                    stompClient.send('/app/join/' + sessionId, {}, JSON.stringify(currentUser));

                    document.addEventListener('mousemove', function(e) {
                        if (!stompClient || !stompClient.connected) return;
                        stompClient.send('/app/cursor/' + sessionId, {}, JSON.stringify({
                            userId: currentUser.nickname,
                            nickname: currentUser.nickname,
                            color: currentUser.color,
                            x: e.clientX,
                            y: e.clientY
                        }));
                    });
                })
                .catch(err => console.error('创建协作会话失败:', err));
        }, function(error) {
            console.error('WebSocket 连接失败:', error);
        });
    }

    function updateOnlineUsers(users) {
        const usersList = document.getElementById('onlineUsersList');
        if (!usersList) return;
        usersList.innerHTML = '';
        users.forEach(function(user) {
            const div = document.createElement('div');
            div.style.margin = '4px 0';
            div.innerHTML = '<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:' + (user.color || '#999') + ';margin-right:5px;"></span>' + (user.nickname || '匿名');
            usersList.appendChild(div);
        });
    }

    function handleOperation(operation) {
        console.log('收到远程操作:', operation);
    }

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

    window.addEventListener('beforeunload', function() {
        if (stompClient && stompClient.connected && sessionId && currentUser) {
            try {
                stompClient.send('/app/leave/' + sessionId, {}, JSON.stringify(currentUser));
            } catch (e) { /* ignore */ }
        }
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', connectWebSocket);
    } else {
        connectWebSocket();
    }
})();
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
