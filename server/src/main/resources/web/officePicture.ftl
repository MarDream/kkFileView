<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <title>${file.name}图片预览</title>
    <#include "*/commonHeader.ftl">
    <link rel="stylesheet" href="css/officePicture.css"/>
</head>
<body>
<div class="container">
    <#list imgUrls as img>
              <div class="img-area" id="imgArea${img_index+1}">
            <div class="image-container">
                <img  class="my-photo"  id="page${img_index+1}" src="images/loading.gif" guoyu-src="${img}">
                <div class="button-container">
                    <button class="sszImg" >${img_index+1}/${imgUrls?size}页</button>
                    <button class="nszImg" onclick="rotateImg('page${img_index+1}', false)">逆时针</button>
                    <button class="sszImg" onclick="rotateImg('page${img_index+1}', true)">顺时针</button>
                   <button onclick="recoveryImg('page${img_index+1}')">恢复</button>
                   <#if collaborationAnnotationEnabled!false>
                   <button onclick="startAnnotation(${img_index+1})" style="background: #007bff; color: white; border: none; padding: 5px 10px; cursor: pointer;">批注</button>
                   </#if>
                </div>
            </div>
        </div>
    </#list>

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

<!-- 批注画布层 -->
<canvas id="annotationCanvas" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 9999;"></canvas>
</#if>

<!-- 页码跳转输入框 -->
<div id="pageJumpBox" style="position: fixed; top: 20px; right: 120px; background: white; padding: 10px; border: 1px solid #ddd; box-shadow: 0 2px 5px rgba(0,0,0,0.2); display: none; z-index: 1000;">
    <div style="margin-bottom: 5px; font-size: 14px;">跳转到第几页?</div>
    <input type="number" id="jumpPageInput" style="width: 60px; padding: 5px; border: 1px solid #ccc;" min="1" max="${imgUrls?size}" value="1">
    <button onclick="jumpToPage()" style="margin-left: 5px; padding: 5px 10px; background: #007bff; color: white; border: none; cursor: pointer;">跳转</button>
    <button onclick="hidePageJumpBox()" style="margin-left: 5px; padding: 5px 10px; background: #6c757d; color: white; border: none; cursor: pointer;">关闭</button>
</div>



 <script type="text/javascript">
 // 获取页码参数，默认为1
 var targetPage = ${page!1};
 var totalPages = ${imgUrls?size};
 
 // 如果页码超出范围，设为第一页
 if (targetPage < 1 || targetPage > totalPages) {
     targetPage = 1;
 }
 
 // 页面加载完成后跳转到指定页码
 window.onload = function() {
     // 延迟执行，确保DOM完全加载
     setTimeout(function() {
         // 滚动到指定页码
         scrollToPage(targetPage);
     }, 100);
     
     // 初始化懒加载
     initLazyLoad();
     
     // 初始化水印
     initWaterMark();
     
     // 为每个图片区域添加点击事件，显示当前页码
     var imgAreas = document.querySelectorAll('.img-area');
     imgAreas.forEach(function(area, index) {
         area.onclick = function() {
         };
     });
     
     // 添加键盘事件支持
     document.addEventListener('keydown', function(e) {
         var currentPage = getCurrentPage();
         
         // 右箭头或空格键翻到下一页
         if (e.key === 'ArrowRight' || e.key === ' ' || e.key === 'PageDown') {
             e.preventDefault();
             var nextPage = Math.min(currentPage + 1, totalPages);
             scrollToPage(nextPage);
         }
         // 左箭头翻到上一页
         else if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
             e.preventDefault();
             var prevPage = Math.max(currentPage - 1, 1);
             scrollToPage(prevPage);
         }
         // J键打开跳转框
         else if (e.key === 'j' || e.key === 'J') {
             e.preventDefault();
             showPageJumpBox();
         }
         // ESC键关闭跳转框
         else if (e.key === 'Escape') {
             hidePageJumpBox();
         }
     });
 };
 
 // 滚动到指定页码
 function scrollToPage(pageNum) {
     var targetElement = document.getElementById('imgArea' + pageNum);
     if (targetElement) {
         // 确保图片已加载
         var targetImg = document.getElementById('page' + pageNum);
         if (targetImg && targetImg.src.includes('loading.gif')) {
             targetImg.src = targetImg.getAttribute('guoyu-src');
         }
         
         // 滚动到目标位置
         targetElement.scrollIntoView({behavior: 'smooth', block: 'start'});
         
     }
 }
 

 // 获取当前可见的页码
 function getCurrentPage() {
     var imgAreas = document.querySelectorAll('.img-area');
     var viewportHeight = window.innerHeight || document.documentElement.clientHeight;
     
     for (var i = 0; i < imgAreas.length; i++) {
         var rect = imgAreas[i].getBoundingClientRect();
         // 如果元素顶部在视口中且高度超过一定阈值
         if (rect.top >= 0 && rect.top <= viewportHeight * 0.3) {
             return i + 1;
         }
     }
     
     return 1;
 }
 
 // 显示页码跳转框
 function showPageJumpBox() {
     var jumpBox = document.getElementById('pageJumpBox');
     var currentPage = getCurrentPage();
     document.getElementById('jumpPageInput').value = currentPage;
     jumpBox.style.display = 'block';
     document.getElementById('jumpPageInput').focus();
     document.getElementById('jumpPageInput').select();
 }
 
 // 隐藏页码跳转框
 function hidePageJumpBox() {
     document.getElementById('pageJumpBox').style.display = 'none';
 }
 
 // 跳转到输入的页码
 function jumpToPage() {
     var pageInput = document.getElementById('jumpPageInput');
     var pageNum = parseInt(pageInput.value);
     
     if (isNaN(pageNum) || pageNum < 1 || pageNum > totalPages) {
         alert('请输入有效的页码 (1-' + totalPages + ')');
         return;
     }
     
     scrollToPage(pageNum);
     hidePageJumpBox();
 }
 
 // 初始化懒加载
 function initLazyLoad() {
     var aImg = document.querySelectorAll('.my-photo');
     var len = aImg.length;
     var n = 0;
     
     function lazyLoad() {
         var seeHeight = document.documentElement.clientHeight;
         var scrollTop = document.body.scrollTop || document.documentElement.scrollTop;
         
         for (var i = n; i < len; i++) {
             var rect = aImg[i].getBoundingClientRect();
             var top = rect.top;
             
             // 如果图片进入可视区域或附近区域
             if (top < seeHeight + scrollTop + 500) {
                 var src = aImg[i].getAttribute('guoyu-src');
                 if (src && aImg[i].src.includes('loading.gif')) {
                     aImg[i].src = src;
                 }
                 n = i + 1;
             }
         }
     }
     
     // 初始加载当前页码及附近的图片
     setTimeout(function() {
         // 加载当前页码图片
         var currentImg = document.getElementById('page' + targetPage);
         if (currentImg && currentImg.src.includes('loading.gif')) {
             currentImg.src = currentImg.getAttribute('guoyu-src');
         }
         
         // 加载前后几页图片
         for (var i = Math.max(1, targetPage - 2); i <= Math.min(totalPages, targetPage + 2); i++) {
             if (i === targetPage) continue;
             var img = document.getElementById('page' + i);
             if (img && img.src.includes('loading.gif')) {
                 img.src = img.getAttribute('guoyu-src');
             }
         }
     }, 300);
     
     window.onscroll = function() {
         lazyLoad();
         initWaterMark();
     };
     
     // 初始加载一次
     lazyLoad();
 }
 
 var aImg = document.querySelectorAll('.my-photo');
 var len = aImg.length;
 var n = 0;
 window.onscroll = function() {
     var seeHeight = document.documentElement.clientHeight-30;
     var scrollTop = document.body.scrollTop || document.documentElement.scrollTop;
     for (var i = n; i < len; i++) {
        var rect = aImg[i].getBoundingClientRect();
        var top = rect.top;
         if (top < seeHeight + scrollTop) {
              aImg[i].src = aImg[i].getAttribute('guoyu-src');
             n = i + 1;
         }
     }
     initWaterMark();
 };
 
 function changePreviewType(previewType) {
     var url = window.location.href;
     if (url.indexOf("officePreviewType=image") !== -1) {
         url = url.replace("officePreviewType=image", "officePreviewType="+previewType);
     } else {
         url = url + "&officePreviewType="+previewType;
     }
     window.location.href = url;
 }
 
 function rotateImg(imgId, isRotate) {
     var img = document.querySelector("#" + imgId);
 
     if (img.classList.contains("imgT90")) {
         img.classList.remove("imgT90");
         if (isRotate) {
             img.classList.add("imgT180");
         }
     } else if (img.classList.contains("imgT-90")) {
         img.classList.remove("imgT-90");
         if (!isRotate) {
             img.classList.add("imgT-180");
         }
     } else if (img.classList.contains("imgT180")) {
         img.classList.remove("imgT180");
         if (isRotate) {
             img.classList.add("imgT270");
         } else {
             img.classList.add("imgT90");
         }
     } else if (img.classList.contains("imgT-180")) {
         img.classList.remove("imgT-180");
         if (isRotate) {
             img.classList.add("imgT-90");
         } else {
             img.classList.add("imgT-270");
         }
     } else if (img.classList.contains("imgT270")) {
         img.classList.remove("imgT270");
         if (!isRotate) {
             img.classList.add("imgT180");
         }
     } else if (img.classList.contains("imgT-270")) {
         img.classList.remove("imgT-270");
         if (isRotate) {
             img.classList.add("imgT-180");
         }
     } else {
         if (isRotate) {
             img.classList.add("imgT90");
         } else {
             img.classList.add("imgT-90");
         }
     }
 }
 
 function recoveryImg(imgId) {
     document.querySelector("#" + imgId).classList.remove("imgT90", "imgT180", "imgT270", "imgT-90", "imgT-180", "imgT-270");
 }
 
 // 滚动监听更新页码指示器
 window.addEventListener('scroll', function() {
     var currentPage = getCurrentPage();
 });

 <#if collaborationAnnotationEnabled!false>
 // 图片批注功能
 (function() {
     const canvas = document.getElementById('annotationCanvas');
     const ctx = canvas.getContext('2d');
     const toolbar = document.getElementById('annotationToolbar');
     let currentTool = null;
     let isDrawing = false;
     let annotations = [];
     let currentPage = 1;
     let fileKey = window.location.href;

     function resizeCanvas() {
         canvas.width = window.innerWidth;
         canvas.height = document.documentElement.scrollHeight;
         redrawAnnotations();
     }
     window.addEventListener('resize', resizeCanvas);

     function startAnnotation(page) {
         currentPage = page;
         toolbar.style.display = 'block';
         resizeCanvas();
         canvas.style.pointerEvents = 'auto';
     }
     window.startAnnotation = startAnnotation;

     document.querySelectorAll('.anno-tool').forEach(btn => {
         btn.addEventListener('click', function() {
             currentTool = this.dataset.tool;
             document.querySelectorAll('.anno-tool').forEach(b => b.style.background = 'white');
             this.style.background = '#e0e0e0';
         });
     });

     document.getElementById('toggleAnnotations').addEventListener('click', async function() {
         if (annotations.length === 0) {
             await loadAnnotations();
         } else {
             redrawAnnotations();
         }
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
             page: currentPage,
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
             if (anno.page !== currentPage) return;
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