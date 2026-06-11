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
        iframe, embed, object {
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

        /* 缩放控制按钮：浏览器原生 PDF viewer 不暴露 PDFViewerApplication，
           这些按钮仅作为视觉占位，实际缩放请用浏览器 Ctrl+滚轮 / Ctrl+ +/- */
        .zoom-controls {
            display: none;
        }
    </style>
</head>
<body>

<#if pdfUrl?contains("http://") || pdfUrl?contains("https://")>
    <#assign finalUrl="${pdfUrl}">
<#else>
    <#assign finalUrl="${baseUrl}${pdfUrl}">
</#if>

<!--
  用浏览器原生 PDF viewer 直接渲染 PDF，替代 kkFileView 原 PDF.js viewer。
  原因：PDF.js viewer 解析 LibreOffice 嵌入的 NotoSansCJK 字体子集时
  CMap 错乱，导致中文 fallback 成"海""黄"等单字符；浏览器原生 viewer 正常。
-->
<iframe id="pdfFrame" src="${finalUrl}"></iframe>

<#if "false" == switchDisabled>
    <img class="img-preview" src="images/jpg.svg" alt="使用图片预览" title="使用图片预览" onclick="goForImage()"/>
</#if>

<script type="text/javascript">
    var url = '${finalUrl}';
    var kkagent = '${kkagent}';
    var baseUrl = '${baseUrl}'.endsWith('/') ? '${baseUrl}' : '${baseUrl}' + '/';
    // CORS 处理：跨域或非 baseUrl 内地址走 getCorsFile 代理
    if (kkagent === 'true' || !url.startsWith(baseUrl)) {
        url = baseUrl + 'getCorsFile?urlPath=' + encodeURIComponent(Base64.encode(url)) + "&key=${kkkey}";
    }
    // 直接用浏览器原生 PDF viewer 渲染（不包裹 pdfjs/web/viewer.html）
    document.getElementById('pdfFrame').src = url;

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
