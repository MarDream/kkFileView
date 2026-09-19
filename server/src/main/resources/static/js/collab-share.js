/**
 * @file collab-share.js
 * 在线协作 - 分享链接对话框组件。
 *
 * 由 commonHeader.ftl 内联分享脚本忠实移植而来（静态 JS 不经 Freemarker
 * 解析，从而根治了 ${fullUrl} 插值与 <#noparse> 包裹问题）。
 * 全部数据在运行时取自浏览器 location，不依赖任何模板变量。
 *
 * 依赖后端契约：
 *   POST /api/share/create {url, fileName, password, ttlHours}
 *     → {token, shareUrl, expireAt}
 *
 * 页面引用方式：/js/collab-share.js（根相对路径），
 * 样式在 /css/collab.css 的 .kk-collab-* 规则中。
 */

/**
 * 初始化右上角"分享"入口按钮（幂等：已存在则跳过）。
 *
 * @returns {void}
 */
function initShareButton() {
    if (document.querySelector('.kk-collab-share-btn')) return;
    var btn = document.createElement('button');
    btn.className = 'kk-collab-share-btn';
    btn.textContent = '分享';
    btn.onclick = openShareDialog;
    document.body.appendChild(btn);
}

/**
 * 打开分享设置对话框（有效期 + 可选访问密码）。
 *
 * @returns {void}
 */
function openShareDialog() {
    // 半透明遮罩：点击可关闭对话框
    var mask = document.createElement('div');
    mask.className = 'kk-collab-mask';
    mask.onclick = closeShareDialog;

    var dialog = document.createElement('div');
    dialog.className = 'kk-collab-dialog';
    dialog.innerHTML = ''
        + '<h3>创建分享链接</h3>'
        + '<label>有效期</label>'
        + '<select id="kk-share-ttl">'
        + '<option value="24">24 小时</option>'
        + '<option value="72">3 天</option>'
        + '<option value="168">7 天</option>'
        + '<option value="720">30 天</option>'
        + '</select>'
        + '<label>访问密码（可选）</label>'
        + '<input type="text" id="kk-share-password" placeholder="留空则无密码">'
        + '<div class="actions">'
        + '<button class="btn-cancel" onclick="closeShareDialog()">取消</button>'
        + '<button class="btn-primary" onclick="createShare()">创建</button>'
        + '</div>'
        + '<div id="kk-share-result"></div>';

    document.body.appendChild(mask);
    document.body.appendChild(dialog);
}

/**
 * 关闭并移除分享对话框与遮罩。
 *
 * @returns {void}
 */
function closeShareDialog() {
    document.querySelectorAll('.kk-collab-mask, .kk-collab-dialog').forEach(function (el) {
        el.remove();
    });
}

/**
 * 调用后端创建分享链接，并把结果渲染进对话框。
 * 链接与错误文案均通过 DOM API 写入（textContent），避免字符串插值注入。
 *
 * @returns {void}
 */
async function createShare() {
    var ttl = document.getElementById('kk-share-ttl').value;
    var password = document.getElementById('kk-share-password').value;
    var resultDiv = document.getElementById('kk-share-result');

    try {
        var response = await fetch('/api/share/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                url: window.location.href,
                fileName: document.title || '未命名文件',
                password: password || null,
                ttlHours: parseInt(ttl)
            })
        });
        var data = await response.json();
        if (response.ok && data.shareUrl) {
            // 拼接完整可访问地址（运行时取 origin，不依赖模板变量）
            var fullUrl = window.location.origin + data.shareUrl;

            resultDiv.innerHTML = ''
                + '<div class="kk-collab-share-link" id="kk-share-link"></div>'
                + '<div class="actions">'
                + '<button class="btn-primary" id="kk-share-copy-btn">复制链接</button>'
                + '</div>';
            // 链接文本用 textContent 写入，防止 URL 特殊字符破坏 HTML 结构
            document.getElementById('kk-share-link').textContent = fullUrl;
            document.getElementById('kk-share-copy-btn').onclick = function () {
                copyShareLink(fullUrl);
            };
        } else {
            var errText = document.createElement('p');
            errText.style.cssText = 'color:#dc3545;margin-top:10px;';
            errText.textContent = data.error || '创建失败';
            resultDiv.innerHTML = '';
            resultDiv.appendChild(errText);
        }
    } catch (err) {
        var netText = document.createElement('p');
        netText.style.cssText = 'color:#dc3545;margin-top:10px;';
        netText.textContent = '网络错误';
        resultDiv.innerHTML = '';
        resultDiv.appendChild(netText);
    }
}

/**
 * 复制分享链接到剪贴板；失败时降级为手动复制提示。
 *
 * @param {string} url 待复制的完整分享链接
 * @returns {void}
 */
function copyShareLink(url) {
    navigator.clipboard.writeText(url).then(function () {
        alert('链接已复制到剪贴板');
    }).catch(function () {
        prompt('请手动复制:', url);
    });
}

// 页面就绪后注入分享入口按钮
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initShareButton);
} else {
    initShareButton();
}
