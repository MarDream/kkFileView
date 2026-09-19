/**
 * @file collab-annotation.js
 * 在线批注公共组件（高亮 / 文本 / 下划线 / 自由绘制）。
 *
 * 架构：页面锚定 + 归一化比例坐标。
 * 子 canvas 挂到每个页面元素（el）内部（position:absolute 随元素尺寸伸缩），
 * 批注坐标全部存 0~1 比例（相对所在页面元素的宽高），
 * 因此 PDF 缩放/翻页、图片懒加载、PPT 尺寸变化后批注位置自动跟随。
 *
 * 页面元素由调用方通过 getPageElements() 提供，el 可在父文档或同源 iframe
 * 文档内（iframe 内元素加载不到父页面的 collab.css，故子 canvas 一律使用
 * JS 内联样式；工具栏仍在父文档，可继续使用 class 样式）。
 *
 * 批注数据形状（与后端 /api/annotation/* 契约一致）：
 *   { type: 'highlight'|'text'|'underline'|'draw', x, y, w, h, color, text, page }
 *   坐标字段均为 0~1 比例；draw 类型额外携带 points: [{x, y}, ...]（比例）。
 *   page 即锚定页面元素的页码（getPageElements 返回的 pageNumber）。
 *
 * UMD 风格：浏览器环境挂载 window.initCollabAnnotation。
 */
(function (root, factory) {
    if (typeof module === 'object' && typeof module.exports === 'object') {
        module.exports = factory();
    } else {
        root.initCollabAnnotation = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    /** 下划线基准厚度（像素）：保存时换算为比例高度 = 该值 / 页面元素高度 */
    var UNDERLINE_HEIGHT = 4;
    /** 矩形类批注（高亮/下划线/文本框）的填充透明度 */
    var FILL_ALPHA = 0.3;
    /** 自由绘制线条宽度（像素） */
    var DRAW_LINE_WIDTH = 2;
    /** 页面元素轮询间隔（毫秒）：pdfjs viewer 异步生成 .page 元素 */
    var PAGE_POLL_INTERVAL = 1000;
    /** 页面元素轮询超时（毫秒），超时后放弃锚定 */
    var PAGE_POLL_TIMEOUT = 30000;
    /** 元素签名比对周期（毫秒）：检测翻页/新增页面并增量补锚 */
    var RESYNC_INTERVAL = 2000;
    /** 各工具默认颜色 */
    var TOOL_COLORS = {
        highlight: '#ffff00',
        text: '#ff0000',
        underline: '#ff0000',
        draw: '#ff0000'
    };

    /**
     * 判断是否为旧版像素坐标批注（任一坐标绝对值 > 1 即视为像素坐标）。
     * 比例坐标体系无法表达像素坐标，旧数据直接过滤避免错位。
     *
     * @param {Object} anno 批注对象
     * @returns {boolean} 是否为旧版像素坐标批注
     */
    function isLegacyPixelAnnotation(anno) {
        var coords = [anno.x, anno.y, anno.w, anno.h];
        (anno.points || []).forEach(function (p) {
            coords.push(p.x, p.y);
        });
        return coords.some(function (v) {
            return typeof v === 'number' && Math.abs(v) > 1;
        });
    }

    /**
     * 创建批注组件实例。
     *
     * @param {Object} options 初始化选项
     * @param {string} [options.fileKey] 批注存储主键（默认取当前页面 URL）
     * @param {Function} [options.getPageElements]
     *        返回页面元素列表的函数：() => [{el, pageNumber}]，
     *        el 可在父文档或同源 iframe 文档内
     * @param {number} [options.showDelay=0] 工具栏延迟显示毫秒数，0 表示立即显示
     * @returns {{destroy: Function}} 组件句柄
     */
    function initCollabAnnotation(options) {
        var opts = options || {};
        var fileKey = opts.fileKey || window.location.href;
        var showDelay = typeof opts.showDelay === 'number' ? opts.showDelay : 0;
        var getPageElements = typeof opts.getPageElements === 'function'
            ? opts.getPageElements
            : function () { return []; };

        var currentTool = null;        // 当前激活的工具名
        var isDrawing = false;         // 是否处于拖拽/绘制中
        var activeAnchor = null;       // 本次拖拽所在的锚定记录
        var startPoint = null;         // 本次拖拽起点（比例坐标）
        var currentPoints = [];        // 自由绘制进行中的路径点（比例坐标）
        var annotations = [];          // 已保存的批注列表
        var anchors = [];              // 已锚定页面列表 [{el, pageNumber, canvas, ctx, observer}]
        var pollTimer = null;          // 页面元素轮询定时器
        var resyncTimer = null;        // 元素签名比对定时器
        var pollStartAt = 0;           // 轮询开始时间戳
        var destroyed = false;         // 组件是否已销毁

        /* ---------- DOM 构建（工具栏在父文档，样式用 collab.css） ---------- */

        var toolbar = document.createElement('div');
        toolbar.id = 'kk-collab-annotation-toolbar';
        toolbar.className = 'kk-anno-toolbar';

        /** 工具条按钮定义：名称、文案 */
        var TOOL_DEFS = [
            { name: 'highlight', label: '高亮' },
            { name: 'text', label: '文本' },
            { name: 'underline', label: '下划线' },
            { name: 'draw', label: '绘制' },
            { name: '__close', label: '关闭' }
        ];

        var toolButtons = {};
        TOOL_DEFS.forEach(function (def) {
            var btn = document.createElement('button');
            btn.className = 'kk-anno-btn' + (def.name === '__close' ? ' kk-anno-close' : '');
            btn.type = 'button';
            btn.textContent = def.label;
            if (def.name === '__close') {
                // "关闭"按钮：退出工具模式，恢复页面点击
                btn.addEventListener('click', deactivate);
            } else {
                btn.addEventListener('click', function () {
                    activateTool(def.name, btn);
                });
            }
            toolButtons[def.name] = btn;
            toolbar.appendChild(btn);
        });

        document.body.appendChild(toolbar);

        /* ---------- 页面元素锚定 ---------- */

        /**
         * 安全调用 getPageElements（模板内已 try/catch，此处二次兜底）。
         *
         * @returns {Array<{el: HTMLElement, pageNumber: number}>} 页面元素列表
         */
        function safeGetPageElements() {
            try {
                return getPageElements() || [];
            } catch (e) {
                return [];
            }
        }

        /**
         * 为页面元素创建子 canvas 并绑定鼠标事件。
         * iframe 内元素加载不到父页面的 collab.css，故全部使用内联样式；
         * 若 el 为 static 定位需补 relative，使 canvas 的 absolute 定位相对 el。
         *
         * @param {Object} rec 锚定记录（写入 canvas 字段）
         * @returns {void}
         */
        function createAnchorCanvas(rec) {
            var el = rec.el;
            var canvas = document.createElement('canvas');
            canvas.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;'
                + 'pointer-events:none;z-index:10;';
            bindCanvasEvents(rec, canvas);
            // static 元素无法作为 absolute 子元素的定位上下文，需补 relative
            var position = window.getComputedStyle(el).position;
            if (!position || position === 'static') {
                el.style.position = 'relative';
            }
            el.appendChild(canvas);
            rec.canvas = canvas;
            rec.ctx = canvas.getContext('2d');
        }

        /**
         * 在锚定画布上绑定鼠标交互（mousedown/mousemove/mouseup），
         * 事件坐标按画布当前尺寸换算为 0~1 比例坐标。
         *
         * @param {Object} rec 锚定记录
         * @param {HTMLCanvasElement} canvas 该页的子画布
         * @returns {void}
         */
        function bindCanvasEvents(rec, canvas) {
            canvas.addEventListener('mousedown', function (e) {
                if (!currentTool) return;
                isDrawing = true;
                activeAnchor = rec;
                startPoint = eventRatio(e, canvas);
                if (currentTool === 'draw') {
                    currentPoints = [startPoint];
                }
            });

            canvas.addEventListener('mousemove', function (e) {
                if (!isDrawing || currentTool !== 'draw' || activeAnchor !== rec) return;
                currentPoints.push(eventRatio(e, canvas));
                drawPendingPath(rec);
            });

            canvas.addEventListener('mouseup', function (e) {
                if (!isDrawing || !currentTool || activeAnchor !== rec) return;
                isDrawing = false;
                var endPoint = eventRatio(e, canvas);
                var annotation = buildAnnotation(currentTool, rec, startPoint, endPoint);
                if (!annotation) return;
                annotations.push(annotation);
                redrawAnchor(rec);
                saveAnnotations();
            });
        }

        /**
         * 读取鼠标事件相对画布（所在页面元素）的归一化比例坐标。
         *
         * @param {MouseEvent} e 鼠标事件
         * @param {HTMLCanvasElement} canvas 事件所在画布
         * @returns {{x: number, y: number}} 0~1 比例坐标
         */
        function eventRatio(e, canvas) {
            var rect = canvas.getBoundingClientRect();
            return {
                x: rect.width ? (e.clientX - rect.left) / rect.width : 0,
                y: rect.height ? (e.clientY - rect.top) / rect.height : 0
            };
        }

        /**
         * 同步锚定画布的绘制分辨率与其所在页面元素当前尺寸，并重绘该页。
         * ResizeObserver 观察到元素尺寸变化（如 PDF 缩放）时触发。
         *
         * @param {Object} rec 锚定记录
         * @returns {void}
         */
        function resizeAnchor(rec) {
            var width = rec.el.clientWidth;
            var height = rec.el.clientHeight;
            if (!width || !height) return;
            if (rec.canvas.width !== width) rec.canvas.width = width;
            if (rec.canvas.height !== height) rec.canvas.height = height;
            redrawAnchor(rec);
        }

        /**
         * 比对最新页面元素列表与当前锚定列表，增量补锚 / 原地更新页码 / 清理失效画布。
         *
         * @param {Array<{el: HTMLElement, pageNumber: number}>} pages 最新页面元素列表
         * @returns {void}
         */
        function syncAnchors(pages) {
            // 先处理已锚定的元素：从 DOM 消失的移除画布，页码变化的原地更新
            for (var i = anchors.length - 1; i >= 0; i--) {
                var rec = anchors[i];
                var matched = null;
                for (var j = 0; j < pages.length; j++) {
                    if (pages[j].el === rec.el) {
                        matched = pages[j];
                        break;
                    }
                }
                if (!matched) {
                    // 页面元素已从 DOM 消失：断开尺寸观察并移除画布
                    if (rec.observer) rec.observer.disconnect();
                    rec.canvas.remove();
                    anchors.splice(i, 1);
                } else if (matched.pageNumber !== rec.pageNumber) {
                    // 页码变化（如 PPT 单容器轮播翻页）：更新页码并重绘该页批注
                    rec.pageNumber = matched.pageNumber;
                    redrawAnchor(rec);
                }
            }
            // 再补锚新出现的页面元素
            pages.forEach(function (page) {
                var alreadyAnchored = anchors.some(function (rec) { return rec.el === page.el; });
                if (alreadyAnchored) return;
                var rec = { el: page.el, pageNumber: page.pageNumber, canvas: null, ctx: null, observer: null };
                createAnchorCanvas(rec);
                // 观察元素尺寸变化（跨文档同源可用），缩放时自动重设画布并重绘
                if (typeof ResizeObserver !== 'undefined') {
                    rec.observer = new ResizeObserver(function () { resizeAnchor(rec); });
                    rec.observer.observe(page.el);
                }
                anchors.push(rec);
                resizeAnchor(rec);
            });
        }

        /**
         * 判断锚定状态是否落后于最新页面元素列表
         * （数量、el 引用、页码任一变化即需重新同步）。
         *
         * @param {Array<{el: HTMLElement, pageNumber: number}>} pages 最新页面元素列表
         * @returns {boolean} 是否需要重新同步
         */
        function needsResync(pages) {
            if (pages.length !== anchors.length) return true;
            for (var i = 0; i < pages.length; i++) {
                if (pages[i].el !== anchors[i].el || pages[i].pageNumber !== anchors[i].pageNumber) {
                    return true;
                }
            }
            return false;
        }

        /* ---------- 页面元素轮询与周期比对 ---------- */

        /**
         * 轮询等待页面元素出现（pdfjs viewer 异步加载、页面动态生成），
         * 首次锚定成功后停止轮询并启动周期性签名比对。
         *
         * @returns {void}
         */
        function pollForPages() {
            if (destroyed) return;
            var pages = safeGetPageElements();
            if (pages.length) {
                syncAnchors(pages);
                resyncTimer = setInterval(checkResync, RESYNC_INTERVAL);
                return;
            }
            if (Date.now() - pollStartAt > PAGE_POLL_TIMEOUT) {
                console.info('批注组件等待页面元素超时（'
                    + (PAGE_POLL_TIMEOUT / 1000) + 's），停止锚定');
                return;
            }
            pollTimer = setTimeout(pollForPages, PAGE_POLL_INTERVAL);
        }

        /**
         * 周期性比对页面元素签名，变化时增量补锚/清理失效画布
         * （覆盖 pdfjs 动态生成页面、PPT 轮播翻页等场景）。
         *
         * @returns {void}
         */
        function checkResync() {
            if (destroyed) return;
            var pages = safeGetPageElements();
            if (needsResync(pages)) {
                syncAnchors(pages);
            }
        }

        /* ---------- 工具切换 ---------- */

        /**
         * 激活指定工具；再次点击当前激活工具则取消（恢复页面点击）。
         *
         * @param {string} name 工具名
         * @param {HTMLButtonElement} btn 触发的按钮元素
         * @returns {void}
         */
        function activateTool(name, btn) {
            if (currentTool === name) {
                deactivate();
                return;
            }
            currentTool = name;
            Object.keys(toolButtons).forEach(function (key) {
                if (key !== '__close') {
                    toolButtons[key].classList.toggle('active', toolButtons[key] === btn);
                }
            });
            setCanvasesPointerEvents('auto');
        }

        /**
         * 退出工具模式：清空激活态与绘制中状态，
         * 将全部画布 pointer-events 置为 none，恢复页面点击。
         *
         * @returns {void}
         */
        function deactivate() {
            currentTool = null;
            isDrawing = false;
            activeAnchor = null;
            startPoint = null;
            currentPoints = [];
            Object.keys(toolButtons).forEach(function (key) {
                toolButtons[key].classList.remove('active');
            });
            setCanvasesPointerEvents('none');
            redrawAll();
        }

        /**
         * 统一切换所有锚定画布的 pointer-events：
         * 激活工具时可接收鼠标绘制，退出后交还页面交互。
         *
         * @param {string} value 'auto' 或 'none'
         * @returns {void}
         */
        function setCanvasesPointerEvents(value) {
            anchors.forEach(function (rec) {
                rec.canvas.style.pointerEvents = value;
            });
        }

        /* ---------- 绘制渲染 ---------- */

        /**
         * 绘制进行中的自由绘制路径（实时预览）。
         *
         * @param {Object} rec 拖拽所在锚定记录
         * @returns {void}
         */
        function drawPendingPath(rec) {
            redrawAnchor(rec);
            if (currentPoints.length < 2) return;
            var ctx = rec.ctx;
            var w = rec.canvas.width;
            var h = rec.canvas.height;
            ctx.globalAlpha = 0.9;
            ctx.strokeStyle = TOOL_COLORS.draw;
            ctx.lineWidth = DRAW_LINE_WIDTH;
            ctx.beginPath();
            ctx.moveTo(currentPoints[0].x * w, currentPoints[0].y * h);
            for (var i = 1; i < currentPoints.length; i++) {
                ctx.lineTo(currentPoints[i].x * w, currentPoints[i].y * h);
            }
            ctx.stroke();
            ctx.globalAlpha = 1;
        }

        /**
         * 重绘单个锚定页：只绘制 page 与该页页码一致的批注。
         * draw 类型按 polyline 路径绘制，其余按半透明矩形绘制；
         * 文本批注在矩形上方追加内容文字。坐标按比例 * 画布当前尺寸换算。
         *
         * @param {Object} rec 锚定记录
         * @returns {void}
         */
        function redrawAnchor(rec) {
            var ctx = rec.ctx;
            if (!ctx) return;
            var w = rec.canvas.width;
            var h = rec.canvas.height;
            ctx.clearRect(0, 0, w, h);
            annotations.forEach(function (anno) {
                // 只绘制本页的批注；宽松比较兼容历史数据中字符串页码
                if (anno.page != null && rec.pageNumber != null && anno.page != rec.pageNumber) {
                    return;
                }
                if (anno.type === 'draw' && anno.points && anno.points.length > 1) {
                    // 自由绘制：按路径绘制折线
                    ctx.globalAlpha = 0.9;
                    ctx.strokeStyle = anno.color || TOOL_COLORS.draw;
                    ctx.lineWidth = DRAW_LINE_WIDTH;
                    ctx.beginPath();
                    ctx.moveTo(anno.points[0].x * w, anno.points[0].y * h);
                    for (var i = 1; i < anno.points.length; i++) {
                        ctx.lineTo(anno.points[i].x * w, anno.points[i].y * h);
                    }
                    ctx.stroke();
                } else {
                    // 矩形类批注：高亮 / 下划线 / 文本框
                    ctx.fillStyle = anno.color || TOOL_COLORS.highlight;
                    ctx.globalAlpha = FILL_ALPHA;
                    ctx.fillRect(anno.x * w, anno.y * h, anno.w * w, anno.h * h);
                }
                if (anno.text) {
                    ctx.fillStyle = '#000';
                    ctx.globalAlpha = 1;
                    ctx.font = '12px sans-serif';
                    ctx.fillText(anno.text, anno.x * w, Math.max(10, anno.y * h - 5));
                }
                ctx.globalAlpha = 1;
            });
        }

        /** 重绘全部锚定页。 */
        function redrawAll() {
            anchors.forEach(redrawAnchor);
        }

        /* ---------- 批注构建 ---------- */

        /**
         * 根据工具类型把一次拖拽转换为批注对象（契约形状，坐标为 0~1 比例）。
         * 文本批注内容为空时不保存（返回 null）。
         *
         * @param {string} tool 工具名
         * @param {Object} rec 拖拽所在锚定记录（提供页码与画布尺寸）
         * @param {{x: number, y: number}} start 起点（比例坐标）
         * @param {{x: number, y: number}} end 终点（比例坐标）
         * @returns {Object|null} 批注对象；无需保存时返回 null
         */
        function buildAnnotation(tool, rec, start, end) {
            var canvasH = rec.canvas.height || 1;
            var anno = {
                type: tool,
                x: Math.min(start.x, end.x),
                y: Math.min(start.y, end.y),
                w: Math.abs(end.x - start.x),
                h: Math.abs(end.y - start.y),
                color: TOOL_COLORS[tool] || '#ff0000',
                page: rec.pageNumber,
                text: ''
            };

            if (tool === 'underline') {
                // 真下划线：细长半透明矩形贴选区底部，比例高度 = 基准厚度 / 画布高度
                var bottom = Math.max(start.y, end.y);
                anno.y = bottom - UNDERLINE_HEIGHT / canvasH;
                anno.h = UNDERLINE_HEIGHT / canvasH;
            } else if (tool === 'text') {
                var content = window.prompt('请输入批注内容:');
                if (!content) return null; // 空内容不保存
                anno.text = content;
            } else if (tool === 'draw') {
                // 自由绘制：提交完整路径点（比例坐标）
                anno.points = currentPoints.slice();
                anno.x = 0;
                anno.y = 0;
                anno.w = 0;
                anno.h = 0;
            }
            return anno;
        }

        /* ---------- 数据加载 / 保存（契约接口不变） ---------- */

        /**
         * 从后端加载当前文件的批注列表并重绘。
         * 兼容处理：过滤掉旧版像素坐标批注（任一坐标 > 1），避免比例换算后错位。
         * 失败时仅输出中文错误日志，不影响页面。
         */
        function loadAnnotations() {
            fetch('/api/annotation/list?fileKey=' + encodeURIComponent(fileKey))
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('HTTP ' + response.status);
                    }
                    return response.json();
                })
                .then(function (data) {
                    var ignored = 0;
                    annotations = (data || []).filter(function (anno) {
                        if (isLegacyPixelAnnotation(anno)) {
                            ignored++;
                            return false;
                        }
                        return true;
                    });
                    if (ignored > 0) {
                        console.info('忽略旧版像素坐标批注 ' + ignored + ' 条');
                    }
                    redrawAll();
                })
                .catch(function (err) {
                    console.error('加载批注失败:', err);
                });
        }

        /**
         * 将批注列表整体保存到后端。
         * 失败时输出中文错误日志。
         */
        function saveAnnotations() {
            fetch('/api/annotation/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ fileKey: fileKey, annotations: annotations })
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('HTTP ' + response.status);
                    }
                })
                .catch(function (err) {
                    console.error('保存批注失败:', err);
                });
        }

        /* ---------- 启动 ---------- */

        /**
         * 窗口尺寸变化时重设全部画布（无 ResizeObserver 环境的兜底路径）。
         */
        function resizeAllAnchors() {
            anchors.forEach(resizeAnchor);
        }

        /**
         * 显示批注工具栏（画布尺寸由页面锚定与 ResizeObserver 管理，无需手动调整）。
         */
        function showToolbar() {
            toolbar.style.display = 'flex';
        }

        window.addEventListener('resize', resizeAllAnchors);
        if (showDelay > 0) {
            window.setTimeout(showToolbar, showDelay);
        } else {
            showToolbar();
        }
        pollStartAt = Date.now();
        pollForPages();
        loadAnnotations();

        /**
         * 销毁组件：停止轮询/比对定时器、断开尺寸观察、移除注入的 DOM。
         *
         * @returns {void}
         */
        function destroy() {
            destroyed = true;
            if (pollTimer) clearTimeout(pollTimer);
            if (resyncTimer) clearInterval(resyncTimer);
            window.removeEventListener('resize', resizeAllAnchors);
            anchors.forEach(function (rec) {
                if (rec.observer) rec.observer.disconnect();
                rec.canvas.remove();
            });
            anchors = [];
            toolbar.remove();
        }

        return { destroy: destroy };
    }

    return initCollabAnnotation;
});
