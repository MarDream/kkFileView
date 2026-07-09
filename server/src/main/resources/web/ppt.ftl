<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <#include "*/commonHeader.ftl">
    <link href="pptx/bootstrap/css/bootstrap.min.css" rel="stylesheet">
    <link href="pptx/idocv/idocv_common.min.css" rel="stylesheet">
    <link href="pptx/jquery.contextMenu.css" rel="stylesheet">
    <#--  手机端预览兼容  -->
    <script type="text/javascript">
        var windowWidth = document.documentElement.clientWidth;
        var searchStr = window.location.search.substr(1);
        if ((windowWidth < 768 || (/micromessenger/.test(navigator.userAgent.toLowerCase()))) && (!searchStr || searchStr.indexOf('type=') < 0)) {
            var redirectUrl = window.location.pathname + '?type=mobile' + (!!searchStr ? ('&' + searchStr) : '');
            window.location.replace(redirectUrl);
        }
    </script>

    <!--[if lt IE 9]>
    <script src="/static/bootstrap/js/html5shiv.js"></script>
    <![endif]-->
</head>

<body onload="resetImgSize();" class="ppt-body">

<div class="loading-mask" style="display: block;">
    <div class="loading-zone">
        <div class="text"><img src="pptx/img/loader_indicator_lite.gif">加载中...</div>
    </div>
</div>

<div class="navbar navbar-inverse navbar-fixed-top">
    <div class="navbar-inner">
        <div class="container-fluid">
            <button type="button" class="btn btn-navbar" data-toggle="collapse" data-target=".nav-collapse">
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
            </button>
            <!-- FILE NAME HERE -->
            <!-- SIGN UP & SIGN IN -->

            <div class="nav-collapse collapse"></div><!--/.nav-collapse -->
        </div>
    </div>
</div>

<div class="container-fluid" style="max-height: 100%;">
    <div class="row-fluid">
        <div class="span2 hidden-phone"
             style="position: fixed; top: 60px; left: 20px; bottom: 20px; padding-right: 10px; border-right: 3px solid #c8c8c8; max-height: 100%; overflow: auto; text-align: center;">
        </div>
        <div class="span9 offset2">
            <div class="slide-img-container">
                <div class="ppt-turn-left-mask"></div>
                <div class="ppt-turn-right-mask"></div>
                <!--
                <img src="" class="img-polaroid" style="max-height: 100%;">
                 -->
            </div>
            <!-- ONLY AVAILABLE ON MOBILE -->
            <div class="span12 visible-phone text-center"
                 style="position: fixed; bottom: 10px; left: 0px; z-index: 1000;">
                <select class="select-page-selector span1" style="width: 80px; margin-top: 10px;">
                    <!-- PAGE NUMBERS HERE -->
                </select>
            </div>
        </div>
    </div>
</div>

<div class="progress progress-striped active bottom-paging-progress">
    <div class="bar" style="width: 0%;"></div>
</div>

<#if collaborationAnnotationEnabled!false>
<!-- 批注工具栏 -->
<div id="annotationToolbar" style="position: fixed; top: 12px; left: 50%; transform: translateX(-50%); z-index: 10000; background: white; padding: 8px 16px; border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,0.15); display: none;">
    <button class="anno-tool" data-tool="highlight" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">高亮</button>
    <button class="anno-tool" data-tool="underline" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">下划线</button>
    <button class="anno-tool" data-tool="text" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">文本批注</button>
    <button class="anno-tool" data-tool="draw" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px;">自由绘制</button>
    <button id="toggleAnnotations" style="padding: 6px 12px; margin-right: 8px; border: 1px solid #007bff; background: #007bff; color: white; cursor: pointer; border-radius: 4px;">显示批注</button>
</div>
<canvas id="annotationCanvas" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 9999;"></canvas>
</#if>

<!-- JavaSript ================================================== -->
<script src="js/jquery-3.6.1.min.js"></script>
<script src="pptx/jquery.contextMenu.js?v=11.2.5_20210128"></script>
<script src="pptx/idocv/idocv_common.min.js"></script>
<script src="pptx/jquery.mobile-events.min.js"></script>
<script src="pptx/ppt.js"></script>
<script>
    var resultData = {
        "code": 1,
        "name": "PPT预览",
        "totalSize": ${imgUrls ? size},
        "curPage": 1,
        "totalPage": 1,
        "pageSize": 10,
        "titles": null,
        "data": [
            <#assign index = 0>
            <#list imgUrls as img>
            <#if index != 0>, </#if>{
                "uuid": null,
                "title": null,
                "content": null,
                "text": null,
                "url": "${img}",
                "destFile": null,
                "viewCount": 0,
                "downloadCount": 0,
                "ctime": null,
                "thumbUrl": "${img}",
                "largeUrl": null,
                "ratio": 0.5625,
                "note": null
            }<#assign index = index + 1>
            </#list>],
        "desc": "Success"
    }

    var contextPath = '';
    var version = '12';
    // var urlObj = $.url($.url().attr('source').replace(contextPath, ''));
    var id = window.location.pathname.replace(contextPath, '').split('/')[2];
    var uuid = id;
    var params = getAllUrlParams(window.location.href); // 如果用urlObj.param()方法获取则被非正常解码
    // var queryStr = urlObj.attr('query'); // 参数被decode，IE下如果有中文参数则报错，需要获取原生参数
    var queryStr = window.location.search.slice(1);
    uuid = !!'' ? '' : uuid;
    var name = 'pptx';
    if (!!name) {
        params.name = name;
    }
    var reqUrl = '';
    var reqUrlMd5 = '';
    var authMap = '{}';
    var authMapStr = 'null';
    if (!!reqUrlMd5 && !!authMapStr) {
        authMap = JSON.parse(authMapStr);
    }

    window.onload = function () {
        initWaterMark();
    }

    <#if collaborationAnnotationEnabled!false>
    // PPT 批注功能
    (function() {
        const canvas = document.getElementById('annotationCanvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        const toolbar = document.getElementById('annotationToolbar');
        let currentTool = null;
        let isDrawing = false;
        let annotations = [];
        let fileKey = window.location.href;

        function resizeCanvas() {
            canvas.width = window.innerWidth;
            canvas.height = document.documentElement.scrollHeight;
            redrawAnnotations();
        }
        window.addEventListener('resize', resizeCanvas);
        setTimeout(() => { toolbar.style.display = 'block'; resizeCanvas(); }, 2000);

        document.querySelectorAll('.anno-tool').forEach(btn => {
            btn.addEventListener('click', function() {
                currentTool = this.dataset.tool;
                canvas.style.pointerEvents = 'auto';
                document.querySelectorAll('.anno-tool').forEach(b => b.style.background = 'white');
                this.style.background = '#e0e0e0';
            });
        });

        document.getElementById('toggleAnnotations').addEventListener('click', async function() {
            if (annotations.length === 0) await loadAnnotations();
            else redrawAnnotations();
        });

        canvas.addEventListener('mousedown', function(e) {
            if (!currentTool) return;
            isDrawing = true;
            this.startX = e.clientX + window.scrollX;
            this.startY = e.clientY + window.scrollY;
        });

        canvas.addEventListener('mouseup', async function(e) {
            if (!isDrawing || !currentTool) return;
            isDrawing = false;
            const endX = e.clientX + window.scrollX;
            const endY = e.clientY + window.scrollY;
            const annotation = {
                id: Date.now().toString(),
                fileKey: fileKey,
                page: 1,
                x: this.startX,
                y: this.startY,
                width: endX - this.startX,
                height: endY - this.startY,
                content: '',
                authorName: '匿名用户',
                color: currentTool === 'highlight' ? '#ffff00' : '#ff0000',
                createdAt: new Date().toISOString()
            };
            if (currentTool === 'text') {
                const content = prompt('请输入批注内容:');
                if (!content) return;
                annotation.content = content;
            }
            annotations.push(annotation);
            redrawAnnotations();
            await saveAnnotations();
        });

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

        loadAnnotations();
    })();
    </#if>
</script>
</body>
</html>
