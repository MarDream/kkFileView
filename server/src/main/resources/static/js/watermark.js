(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define([], factory());
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.watermark = factory();
    }
}(this, function () {
    var defaultSettings = {
        watermark_id: 'wm_div_id',
        watermark_prefix: 'mask_div_id',
        watermark_txt: '测试水印',
        watermark_x: 20,
        watermark_y: 20,
        watermark_rows: 0,
        watermark_cols: 0,
        watermark_x_space: 50,
        watermark_y_space: 50,
        watermark_font: '微软雅黑',
        watermark_color: 'black',
        watermark_fontsize: '18px',
        watermark_alpha: 0.15,
        watermark_width: 100,
        watermark_height: 100,
        watermark_angle: 15,
        watermark_parent_width: 0,
        watermark_parent_height: 0,
        watermark_parent_node: null,
        monitor: true
    };

    var currentSettings = {};
    var lastOptions = null;
    var observer = null;

    function mergeSettings(options) {
        currentSettings = {};
        Object.keys(defaultSettings).forEach(function (key) {
            currentSettings[key] = defaultSettings[key];
        });
        options = options || {};
        Object.keys(options).forEach(function (key) {
            if (options[key] || options[key] === 0) {
                currentSettings[key] = options[key];
            }
        });
    }

    function getWatermarkTexts(text) {
        return String(text || '')
            .replace(/\\r\\n/g, '\n')
            .replace(/\\n/g, '\n')
            .replace(/\r\n/g, '\n')
            .split('\n')
            .map(function (item) { return item.trim(); })
            .filter(function (item) { return item !== ''; });
    }

    function removeMark() {
        var mark = document.getElementById(currentSettings.watermark_id || defaultSettings.watermark_id);
        if (mark && mark.parentNode) {
            mark.parentNode.removeChild(mark);
        }
    }

    function disconnectObserver() {
        if (observer) {
            observer.disconnect();
            observer = null;
        }
    }

    function loadMark(options) {
        mergeSettings(options);
        removeMark();

        var texts = getWatermarkTexts(currentSettings.watermark_txt);
        if (texts.length === 0) {
            return;
        }

        var parentElement = currentSettings.watermark_parent_node
            ? document.getElementById(currentSettings.watermark_parent_node) || document.body
            : document.body;
        var pageWidth = Math.max(parentElement.scrollWidth, parentElement.clientWidth);
        var pageHeight = Math.max(parentElement.scrollHeight, parentElement.clientHeight);
        var pageOffsetTop = parentElement.offsetTop || 0;
        var pageOffsetLeft = parentElement.offsetLeft || 0;
        var mark = document.createElement('div');
        var root = mark;

        mark.id = currentSettings.watermark_id;
        mark.style.pointerEvents = 'none';
        mark.style.display = 'block';
        parentElement.appendChild(mark);
        if (typeof mark.attachShadow === 'function') {
            root = mark.attachShadow({ mode: 'open' });
        }

        currentSettings.watermark_cols = currentSettings.watermark_cols || Math.max(parseInt(
            (pageWidth - currentSettings.watermark_x)
            / (currentSettings.watermark_width + currentSettings.watermark_x_space), 10), 1);
        currentSettings.watermark_rows = currentSettings.watermark_rows || Math.max(parseInt(
            (pageHeight - currentSettings.watermark_y)
            / (currentSettings.watermark_height + currentSettings.watermark_y_space), 10), 1);

        var allWatermarkWidth = currentSettings.watermark_x
            + currentSettings.watermark_width * currentSettings.watermark_cols
            + currentSettings.watermark_x_space * (currentSettings.watermark_cols - 1);
        var allWatermarkHeight = currentSettings.watermark_y
            + currentSettings.watermark_height * currentSettings.watermark_rows
            + currentSettings.watermark_y_space * (currentSettings.watermark_rows - 1);

        for (var row = 0; row < currentSettings.watermark_rows; row++) {
            var y = pageOffsetTop + currentSettings.watermark_y
                + (pageHeight - allWatermarkHeight) / 2
                + (currentSettings.watermark_y_space + currentSettings.watermark_height) * row;
            for (var col = 0; col < currentSettings.watermark_cols; col++) {
                var x = pageOffsetLeft + currentSettings.watermark_x
                    + (pageWidth - allWatermarkWidth) / 2
                    + (currentSettings.watermark_x_space + currentSettings.watermark_width) * col;
                var div = document.createElement('div');
                div.id = currentSettings.watermark_prefix + row + col;
                div.appendChild(document.createTextNode(texts[(row + col) % texts.length]));
                div.style.webkitTransform = 'rotate(-' + currentSettings.watermark_angle + 'deg)';
                div.style.MozTransform = 'rotate(-' + currentSettings.watermark_angle + 'deg)';
                div.style.msTransform = 'rotate(-' + currentSettings.watermark_angle + 'deg)';
                div.style.OTransform = 'rotate(-' + currentSettings.watermark_angle + 'deg)';
                div.style.transform = 'rotate(-' + currentSettings.watermark_angle + 'deg)';
                div.style.visibility = '';
                div.style.position = 'absolute';
                div.style.left = x + 'px';
                div.style.top = y + 'px';
                div.style.overflow = 'hidden';
                div.style.zIndex = '9999999';
                div.style.opacity = currentSettings.watermark_alpha;
                div.style.fontSize = currentSettings.watermark_fontsize;
                div.style.fontFamily = currentSettings.watermark_font;
                div.style.color = currentSettings.watermark_color;
                div.style.textAlign = 'center';
                div.style.width = currentSettings.watermark_width + 'px';
                div.style.height = currentSettings.watermark_height + 'px';
                div.style.display = 'block';
                div.style.userSelect = 'none';
                div.style.pointerEvents = 'none';
                root.appendChild(div);
            }
        }
    }

    function bindObserver(options) {
        disconnectObserver();
        var monitor = options && options.monitor !== undefined ? options.monitor : defaultSettings.monitor;
        if (!monitor || typeof MutationObserver === 'undefined') {
            return;
        }
        observer = new MutationObserver(function (records) {
            for (var i = 0; i < records.length; i++) {
                if (records[i].removedNodes && records[i].removedNodes.length > 0) {
                    disconnectObserver();
                    loadMark(lastOptions);
                    bindObserver(lastOptions);
                    return;
                }
            }
        });
        observer.observe(document.body, { childList: true, attributes: true, subtree: true });
    }

    return {
        init: function (options) {
            lastOptions = options || {};
            disconnectObserver();
            loadMark(lastOptions);
            bindObserver(lastOptions);
            window.addEventListener('load', function () { loadMark(lastOptions); });
            window.addEventListener('resize', function () { loadMark(lastOptions); });
        },
        load: function (options) {
            lastOptions = options || {};
            loadMark(lastOptions);
        },
        remove: function () {
            removeMark();
            disconnectObserver();
        }
    };
}));
