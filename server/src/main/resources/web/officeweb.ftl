<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8" />
    <title>${file.name}预览</title>
    <link rel='stylesheet' href='xlsx/plugins/css/pluginsCss.css' />
    <link rel='stylesheet' href='xlsx/plugins/plugins.css' />
    <link rel='stylesheet' href='xlsx/css/luckysheet.css' />
    <link rel='stylesheet' href='xlsx/assets/iconfont/iconfont.css' />
    <#-- 预加载字体文件，避免 Slow network 警告 -->
    <link rel="preload" href="xlsx/fonts/fontawesome-webfont.woff2?v=4.7.0" as="font" type="font/woff2" crossorigin>
    <#-- 静默 luckysheet 上游库的 console.log/warn 噪音,只保留 console.error -->
    <script>
        (function () {
            var noop = function () {};
            console.log = noop;
            console.warn = noop;
        })();
    </script>
    <#-- 给 luckysheet 内部动态生成的 form 字段补 id,消除 Chrome DevTools issue 提示 -->
    <script>
        (function () {
            var counter = 0;
            function autoId(el) {
                if (!el || el.id || el.name) return;
                el.id = '__ks_auto_' + (++counter);
            }
            function scan(root) {
                if (!root || root.nodeType !== 1) return;
                if (/^(INPUT|BUTTON|SELECT|TEXTAREA)$/.test(root.tagName)) autoId(root);
                var children = root.querySelectorAll && root.querySelectorAll('input, button, select, textarea');
                if (children) for (var i = 0; i < children.length; i++) autoId(children[i]);
            }
            new MutationObserver(function (mutations) {
                for (var i = 0; i < mutations.length; i++) {
                    var added = mutations[i].addedNodes;
                    for (var j = 0; j < added.length; j++) scan(added[j]);
                }
            }).observe(document.documentElement, { childList: true, subtree: true });
        })();
    </script>
    <script src="xlsx/plugins/js/plugin.js"></script>
    <script src="xlsx/luckysheet.umd.js"></script>
    <script src="js/watermark.js" type="text/javascript"></script>
    <script src="js/base64.min.js" type="text/javascript"></script>
</head>
<#if pdfUrl?contains("http://") || pdfUrl?contains("https://") || pdfUrl?contains("ftp://")>
    <#assign finalUrl="${pdfUrl}">
<#else>
    <#assign finalUrl="${baseUrl}${pdfUrl}">
</#if>
<#-- 转义 watermarkTxt 中的换行/回车为 JS 字符串字面量,避免破坏 let 语法 -->
<#-- 必须先替换再 js_string，否则替换无效 -->
<#assign watermarkTxtEscaped = (watermarkTxt!'')?replace('\n', '\\n')?replace('\r', '\\r')>
<#assign watermarkTxtJs = watermarkTxtEscaped?js_string>
<script>
    /**
     * 初始化水印
     */
    function initWaterMark() {
        let watermarkTxt = '${watermarkTxtJs}';
        if (watermarkTxt !== '') {
            watermark.init({
                watermark_txt: watermarkTxt,
                watermark_x: 0,
                watermark_y: 0,
                watermark_rows: 0,
                watermark_cols: ${watermarkCols},
                watermark_x_space: ${watermarkXSpace},
                watermark_y_space: ${watermarkYSpace},
                watermark_font: '${watermarkFont}',
                watermark_fontsize: '${watermarkFontsize}',
                watermark_color: '${watermarkColor}',
                watermark_alpha: ${watermarkAlpha},
                watermark_width: ${watermarkWidth},
                watermark_height: ${watermarkHeight},
                watermark_angle: ${watermarkAngle},
            });
        }
    }

    // 添加加载状态管理
    let isLoading = false;

</script>
<style>
    * {
        margin: 0;
        padding: 0;
    }

    html, body {
        height: 100%;
        width: 100%;
        overflow: hidden;
        background: #fff;
    }

    #preview-shell {
        position: relative;
        width: 100%;
        height: 100%;
        overflow: hidden;
        background: #fff;
    }

    #preview-shell:fullscreen,
    #preview-shell:-webkit-full-screen {
        width: 100vw;
        height: 100vh;
    }

    #luckysheet {
        margin: 0;
        padding: 0;
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        outline: none;
    }

    #loading-overlay {
        position: fixed;
        inset: 0;
        background: rgba(255, 255, 255, 0.95);
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 24px;
        z-index: 9999;
        transition: opacity 0.3s ease;
        background:
            radial-gradient(circle at top, rgba(255, 227, 181, 0.72), rgba(255, 250, 242, 0) 36%),
            linear-gradient(180deg, #fff7ee 0%, #fffdf9 100%);
    }

    .loading-card {
        width: min(680px, calc(100vw - 48px));
        padding: 34px 44px 40px;
        border-radius: 40px;
        background: rgba(255, 252, 246, 0.94);
        border: 1px solid rgba(243, 207, 153, 0.5);
        box-shadow:
            0 24px 64px rgba(212, 146, 58, 0.16),
            inset 0 1px 0 rgba(255, 255, 255, 0.9);
        text-align: center;
        backdrop-filter: blur(8px);
    }

    .loading-bear-stage {
        position: relative;
        display: inline-flex;
        justify-content: center;
        align-items: center;
        width: min(360px, 100%);
        margin: 0 auto 10px;
    }

    .loading-bear-stage::before {
        content: "";
        position: absolute;
        inset: 16px 30px 30px;
        border-radius: 50%;
        background: radial-gradient(circle, rgba(255, 196, 96, 0.34), rgba(255, 196, 96, 0) 72%);
        filter: blur(10px);
        animation: bearGlow 3s ease-in-out infinite;
    }

    .loading-bear-image {
        position: relative;
        z-index: 1;
        width: min(300px, 72vw);
        max-width: 100%;
        user-select: none;
        pointer-events: none;
        filter: drop-shadow(0 22px 26px rgba(201, 135, 52, 0.18));
        transform-origin: center bottom;
        animation: bearFloat 3.2s ease-in-out infinite;
    }

    .loading-title {
        margin-top: 6px;
        font-size: clamp(30px, 4vw, 44px);
        font-weight: 700;
        line-height: 1.2;
        color: #4f2a12;
        letter-spacing: 0.02em;
    }

    .loading-subtitle {
        margin-top: 8px;
        font-size: clamp(15px, 2vw, 18px);
        line-height: 1.7;
        color: #9b6844;
    }

    .loading-dots {
        display: inline-flex;
        gap: 10px;
        margin-top: 18px;
    }

    .loading-dots span {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: linear-gradient(180deg, #ffbc68 0%, #ff8a3d 100%);
        box-shadow: 0 6px 14px rgba(255, 147, 52, 0.28);
        animation: dotBounce 1.3s ease-in-out infinite;
    }

    .loading-dots span:nth-child(2) {
        animation-delay: 0.16s;
    }

    .loading-dots span:nth-child(3) {
        animation-delay: 0.32s;
    }

    #loading-progress {
        position: relative;
        width: min(420px, 100%);
        height: 16px;
        margin: 28px auto 16px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.88);
        border: 2px solid rgba(175, 128, 90, 0.38);
        overflow: hidden;
        box-shadow: inset 0 2px 5px rgba(124, 74, 28, 0.08);
    }

    #loading-bar {
        position: relative;
        width: 0%;
        height: 100%;
        border-radius: inherit;
        background: linear-gradient(90deg, #ff8f49 0%, #ffb95e 56%, #ffd57a 100%);
        transition: width 0.3s ease;
        box-shadow: 0 8px 20px rgba(255, 146, 66, 0.3);
    }

    #loading-bar::after {
        content: "";
        position: absolute;
        top: 0;
        right: -22%;
        width: 22%;
        height: 100%;
        background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0));
        transform: skewX(-18deg);
        animation: progressShine 1.6s linear infinite;
    }

    .loading-caption {
        font-size: clamp(24px, 3.6vw, 34px);
        font-weight: 700;
        color: #4f2a12;
        letter-spacing: 0.03em;
    }

    @keyframes bearFloat {
        0%, 100% {
            transform: translateY(0) rotate(-1deg) scale(1);
        }
        50% {
            transform: translateY(-10px) rotate(1.2deg) scale(1.015);
        }
    }

    @keyframes bearGlow {
        0%, 100% {
            opacity: 0.78;
            transform: scale(0.98);
        }
        50% {
            opacity: 1;
            transform: scale(1.04);
        }
    }

    @keyframes dotBounce {
        0%, 80%, 100% {
            transform: translateY(0);
            opacity: 0.58;
        }
        40% {
            transform: translateY(-7px);
            opacity: 1;
        }
    }

    @keyframes progressShine {
        0% {
            transform: translateX(0) skewX(-18deg);
        }
        100% {
            transform: translateX(-520%) skewX(-18deg);
        }
    }

    @media (max-width: 640px) {
        #loading-overlay {
            padding: 16px;
        }

        .loading-card {
            width: calc(100vw - 32px);
            padding: 26px 22px 30px;
            border-radius: 28px;
        }

        .loading-bear-stage {
            margin-bottom: 2px;
        }

        #loading-progress {
            margin-top: 22px;
            margin-bottom: 12px;
        }
    }

    .error-message {
        display: none;
        background: #ffebee;
        border: 1px solid #ffcdd2;
        border-radius: 4px;
        padding: 20px;
        margin: 20px;
        text-align: center;
    }

    .luckysheet_info_detail {
        padding: 0 20px !important;
    }

    .luckysheet_info_detail_back,
    .luckysheet-share-logo,
    .luckysheet_info_detail_update,
    .luckysheet_info_detail_save,
    .luckysheet_info_detail_user {
        display: none !important;
    }

</style>
<body>
<!-- 添加加载遮罩层 -->
<div id="loading-overlay">
    <div class="loading-card">
        <div class="loading-bear-stage">
            <img class="loading-bear-image" src="images/loading-bear.png" alt="正在整理资料" />
        </div>
        <div class="loading-title">正在整理资料...</div>
        <div class="loading-subtitle">文档较大时会先完成渲染，再进入预览。</div>
        <div class="loading-dots" aria-hidden="true">
            <span></span>
            <span></span>
            <span></span>
        </div>
        <div id="loading-progress">
            <div id="loading-bar"></div>
        </div>
        <div class="loading-caption">马上就好啦！</div>
    </div>
</div>

<!-- 错误提示 -->
<div id="error-message" class="error-message">
    <h3>加载失败</h3>
    <p id="error-detail"></p>
    <button onclick="retryLoad()" style="margin-top: 10px; padding: 8px 16px;">重试</button>
</div>

<div id="lucky-mask-demo" style="position: absolute;z-index: 1000000;left: 0px;top: 0px;bottom: 0px;right: 0px; background: rgba(255, 255, 255, 0.8); text-align: center;font-size: 40px;align-items:center;justify-content: center;display: none;">加载中</div>

<div id="preview-shell">
    <div id="luckysheet"></div>
</div>

<#if collaborationEditEnabled!false>
<!-- 在线用户列表 -->
<div id="onlineUsers" style="position: fixed; top: 60px; right: 16px; z-index: 1000001; background: white; padding: 10px; border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,0.15); width: 150px; display: none;">
    <div style="font-weight: bold; margin-bottom: 8px;">在线用户</div>
    <div id="onlineUsersList"></div>
</div>

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
                        handleCollabOperation(payload);
                    });

                    currentUser = {
                        nickname: '用户' + Math.floor(Math.random() * 1000),
                        color: '#' + Math.floor(Math.random()*16777215).toString(16).padStart(6, '0')
                    };
                    stompClient.send('/app/join/' + sessionId, {}, JSON.stringify(currentUser));
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

    function handleCollabOperation(operation) {
        if (!operation || !window.luckysheet) return;
        // 将远程单元格编辑操作应用到 Luckysheet
        if (operation.type === 'CELL_UPDATE' && operation.cellRange) {
            try {
                const range = operation.cellRange;
                window.luckysheet.setCellValue(range.row, range.column, operation.content);
            } catch (e) {
                console.warn('应用协作操作失败:', e);
            }
        }
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

<script src="xlsx/luckyexcel.umd.js"></script>
<script>
    var url = '${finalUrl}';
   	var kkagent = '${kkagent}';
    var baseUrl = '${baseUrl}'.endsWith('/') ? '${baseUrl}' : '${baseUrl}' + '/';
    if (kkagent === 'true' || !url.startsWith(baseUrl)) {
        url = baseUrl + 'getCorsFile?urlPath=' + encodeURIComponent(Base64.encode(url))+ "&key=${kkkey}";
    }

    let mask = document.getElementById("lucky-mask-demo");
    let loadingOverlay = document.getElementById("loading-overlay");
    let loadingBar = document.getElementById("loading-bar");
    let errorMessage = document.getElementById("error-message");
    let previewShell = document.getElementById("preview-shell");
    let isRefreshingPreviewLayout = false;
    let isReloadingFromTitleClear = false;
    let titleInputObserver = null;
    let displayedProgress = 0;
    let progressTarget = 0;
    let progressAnimationTimer = null;
    let gentleProgressTimer = null;

    function clampProgress(progress) {
        return Math.max(0, Math.min(100, Math.round(progress)));
    }

    function syncProgressAnimation() {
        if (progressAnimationTimer || !loadingBar) {
            return;
        }
        progressAnimationTimer = setInterval(() => {
            if (displayedProgress >= progressTarget) {
                clearInterval(progressAnimationTimer);
                progressAnimationTimer = null;
                return;
            }
            displayedProgress = Math.min(progressTarget, displayedProgress + Math.max(1, (progressTarget - displayedProgress) * 0.22));
            loadingBar.style.width = clampProgress(displayedProgress) + '%';
        }, 120);
    }

    function stopGentleProgress() {
        if (gentleProgressTimer) {
            clearInterval(gentleProgressTimer);
            gentleProgressTimer = null;
        }
    }

    function startGentleProgress(maxPercent, stepPercent, intervalMs) {
        stopGentleProgress();
        gentleProgressTimer = setInterval(() => {
            if (progressTarget >= maxPercent) {
                stopGentleProgress();
                return;
            }
            updateProgress(progressTarget + stepPercent);
        }, intervalMs);
    }

    // 更新加载进度
    function updateProgress(percent, immediate) {
        const next = clampProgress(percent);
        progressTarget = immediate ? next : Math.max(progressTarget, next);
        if (immediate) {
            displayedProgress = next;
            if (loadingBar) {
                loadingBar.style.width = next + '%';
            }
        } else {
            syncProgressAnimation();
        }
    }

    // 显示错误信息
    function showError(message) {
        stopGentleProgress();
        hideLoading();
        errorMessage.style.display = 'block';
        document.getElementById('error-detail').textContent = message;
    }

    function triggerLuckysheetResize() {
        if (window.luckysheet && typeof window.luckysheet.resize === 'function') {
            window.luckysheet.resize();
        }
    }

    function refreshPreviewLayout() {
        if (isRefreshingPreviewLayout) {
            return;
        }
        isRefreshingPreviewLayout = true;
        triggerLuckysheetResize();
        setTimeout(triggerLuckysheetResize, 80);
        setTimeout(triggerLuckysheetResize, 220);
        setTimeout(() => {
            isRefreshingPreviewLayout = false;
        }, 260);
    }

    function reloadPreviewAfterTitleClear() {
        if (isReloadingFromTitleClear) {
            return;
        }
        isReloadingFromTitleClear = true;
        window.location.reload();
    }

    function handleTitleInputChange(event) {
        if (!event || !event.target) {
            return;
        }
        if (event.target.value.trim() === '') {
            reloadPreviewAfterTitleClear();
        }
    }

    function bindTitleInputAutoReload() {
        let titleInput = document.querySelector('.luckysheet_info_detail_input');
        if (!titleInput || titleInput.dataset.clearReloadBound === 'true') {
            return;
        }

        titleInput.dataset.clearReloadBound = 'true';
        titleInput.addEventListener('input', handleTitleInputChange);
        titleInput.addEventListener('change', handleTitleInputChange);
        titleInput.addEventListener('blur', handleTitleInputChange);
    }

    function observeTitleInput() {
        bindTitleInputAutoReload();
        if (titleInputObserver) {
            return;
        }

        titleInputObserver = new MutationObserver(function() {
            bindTitleInputAutoReload();
        });
        titleInputObserver.observe(previewShell, {
            childList: true,
            subtree: true
        });
    }

    // 隐藏加载动画
    function hideLoading() {
        if (loadingOverlay) {
            loadingOverlay.style.opacity = '0';
            setTimeout(() => {
                loadingOverlay.style.display = 'none';
            }, 300);
        }
    }

    // 重试加载
    function retryLoad() {
        errorMessage.style.display = 'none';
        loadingOverlay.style.display = 'flex';
        loadingOverlay.style.opacity = '1';
        displayedProgress = 0;
        progressTarget = 0;
        updateProgress(8, true);
        loadTextAsync();
    }

    // 异步加载Excel文件
    async function loadTextAsync() {
        if (isLoading) return;
        
        isLoading = true;
        updateProgress(8, true);
        
        try {
            initWaterMark();
            
            const value = url;
            const name = '${file.name}';
            
            if (!value) {
                showError('文件URL为空');
                return;
            }

            updateProgress(20);
            startGentleProgress(42, 4, 220);
            
            // 使用异步方式加载
            await new Promise(resolve => setTimeout(resolve, 100)); // 给UI更新一点时间
            
            // 或者使用现有的同步方法，但放在setTimeout中避免阻塞
            await transformWithTimeout(value, name);
            
            stopGentleProgress();
            updateProgress(100);
            
            // 延迟隐藏加载界面，让用户看到加载完成
            setTimeout(() => {
                hideLoading();
                isLoading = false;
            }, 500);
            
        } catch (error) {
            console.error('加载Excel失败:', error);
            showError('加载失败: ' + error.message);
            isLoading = false;
        }
    }

    // 使用setTimeout将同步任务拆分
    function transformWithTimeout(value, name) {
        return new Promise((resolve, reject) => {
            updateProgress(46);
            startGentleProgress(68, 3, 280);
            
            // 将转换过程放在setTimeout中，避免阻塞主线程
            setTimeout(() => {
                try {
                    LuckyExcel.transformExcelToLuckyByUrl(value, name, function(exportJson, luckysheetfile){
                        if(exportJson.sheets==null || exportJson.sheets.length==0){
                            reject(new Error("读取excel文件内容失败!"));
                            return;
                        }
                        
                        stopGentleProgress();
                        updateProgress(78);
                        
                        // 使用requestAnimationFrame来更新UI，避免阻塞
                        requestAnimationFrame(() => {
                            try {
                                updateProgress(88);
                                window.luckysheet.destroy();
                                window.luckysheet.create({
                                    container: 'luckysheet',
                                    lang: "zh",
                                    showtoolbarConfig:{
                                        image: false,
                                        print: false,
                                        exportXlsx: false,
                                    },
                                   allowCopy: true, // 是否允许拷贝
                showtoolbar: false,  // 仅保留预览内容，隐藏原生工具栏
                showinfobar: true, // 是否显示顶部信息栏
                // myFolderUrl: "/",//作用：左上角<返回按钮的链接
                showsheetbar: true, // 是否显示底部sheet页按钮
                showstatisticBar: true, // 是否显示底部计数栏
                sheetBottomConfig: true, // sheet页下方的添加行按钮和回到顶部按钮配置
                allowEdit: ${(xlsxallowEdit!false)?string('true','false')},// 是否允许前台编辑
                enableAddRow: false, // 允许增加行
                enableAddCol: false, // 允许增加列
                userInfo: false, // 右上角的用户信息展示样式
                showRowBar: true, // 是否显示行号区域
                showColumnBar: false, // 是否显示列号区域
                sheetFormulaBar: false, // 是否显示公式栏
                enableAddBackTop: false,//隐藏返回头部按钮
                forceCalculation: false, //下面是导出插件 默认关闭
                                    data: exportJson.sheets,
                                    title: exportJson.info.name,
                                    userInfo: exportJson.info.name.creator,
                                    // 添加加载完成的回调
                                    hook: {
                                        workbookCreateAfter: function() {
                                            stopGentleProgress();
                                            updateProgress(100);
                                            observeTitleInput();
                                            refreshPreviewLayout();
                                            resolve();
                                        }
                                    }
                                });
                            } catch (err) {
                                reject(err);
                            }
                        });
                    });
                    
                } catch (error) {
                    reject(error);
                }
            }, 100);
        });
    }

    // 页面加载完成后开始异步加载
    document.addEventListener('DOMContentLoaded', function() {
        document.addEventListener('fullscreenchange', refreshPreviewLayout);
        window.addEventListener('resize', refreshPreviewLayout);
        observeTitleInput();
        updateProgress(8, true);

        // 延迟一点时间开始加载，确保DOM完全加载
        setTimeout(() => {
            loadTextAsync();
        }, 100);
    });
</script>
</body>
</html>
