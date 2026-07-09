<#setting classic_compatible=true>
<link rel="icon" href="./favicon.ico" type="image/x-icon">
<script src="js/watermark.js" type="text/javascript"></script>

<script>
    /**
     * 初始化水印
     */
    function initWaterMark() {
        let watermarkTxt = '${watermarkTxt?js_string}';
        if (watermarkTxt === '') {
            return;
        }
        let lastWidth = 0;
        let lastHeight = 0;
        const checkResize = () => {
            const currentWidth = document.documentElement.scrollWidth;
            const currentHeight = document.documentElement.scrollHeight;
            // 检测尺寸是否变化
            if (currentWidth === lastWidth && currentHeight === lastHeight) {
                return;
            }
            // 如果变化了, 重新初始化水印
            watermark.init({
                watermark_txt: watermarkTxt,
                watermark_x: 0,
                watermark_y: 0,
                watermark_rows: 0,
                watermark_cols: 0,
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
            // 更新存储的宽口大小
            lastWidth = currentWidth;
            lastHeight = currentHeight;
        };
        setInterval(checkResize, 1000);
    }
</script>

<style>
    * {
        margin: 0;
        padding: 0;
    }

    html, body {
        height: 100%;
        width: 100%;
    }

    /* 在线协作功能样式 */
    .kk-collab-share-btn {
        position: fixed;
        top: 12px;
        right: 16px;
        z-index: 9999;
        padding: 6px 14px;
        background: #007bff;
        color: #fff;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        font-size: 13px;
        box-shadow: 0 2px 6px rgba(0,0,0,0.2);
    }
    .kk-collab-share-btn:hover { background: #0056b3; }

    .kk-collab-dialog {
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: #fff;
        padding: 24px;
        border-radius: 8px;
        box-shadow: 0 4px 20px rgba(0,0,0,0.2);
        z-index: 10000;
        width: 360px;
        max-width: 90vw;
    }
    .kk-collab-dialog h3 { margin-bottom: 16px; }
    .kk-collab-dialog label { display: block; margin: 10px 0 4px; font-size: 13px; color: #555; }
    .kk-collab-dialog input, .kk-collab-dialog select {
        width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box;
    }
    .kk-collab-dialog .actions { margin-top: 18px; text-align: right; }
    .kk-collab-dialog button {
        padding: 6px 16px; border: none; border-radius: 4px; cursor: pointer; margin-left: 8px;
    }
    .kk-collab-dialog .btn-primary { background: #007bff; color: #fff; }
    .kk-collab-dialog .btn-cancel { background: #eee; }
    .kk-collab-mask {
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background: rgba(0,0,0,0.4); z-index: 9999;
    }
    .kk-collab-share-link {
        margin-top: 12px; padding: 8px; background: #f5f5f5; border-radius: 4px;
        word-break: break-all; font-family: monospace; font-size: 12px;
    }
</style>

<#if collaborationShareEnabled!false>
<script>
    function initShareButton() {
        if (document.querySelector('.kk-collab-share-btn')) return;
        const btn = document.createElement('button');
        btn.className = 'kk-collab-share-btn';
        btn.textContent = '分享';
        btn.onclick = openShareDialog;
        document.body.appendChild(btn);
    }

    function openShareDialog() {
        const fileUrl = window.location.href;
        const fileName = document.title || '未命名文件';

        const mask = document.createElement('div');
        mask.className = 'kk-collab-mask';
        mask.onclick = closeShareDialog;

        const dialog = document.createElement('div');
        dialog.className = 'kk-collab-dialog';
        dialog.innerHTML = `
            <h3>创建分享链接</h3>
            <label>有效期</label>
            <select id="kk-share-ttl">
                <option value="24">24 小时</option>
                <option value="72">3 天</option>
                <option value="168">7 天</option>
                <option value="720">30 天</option>
            </select>
            <label>访问密码（可选）</label>
            <input type="text" id="kk-share-password" placeholder="留空则无密码">
            <div class="actions">
                <button class="btn-cancel" onclick="closeShareDialog()">取消</button>
                <button class="btn-primary" onclick="createShare()">创建</button>
            </div>
            <div id="kk-share-result"></div>
        `;

        document.body.appendChild(mask);
        document.body.appendChild(dialog);
    }

    function closeShareDialog() {
        document.querySelectorAll('.kk-collab-mask, .kk-collab-dialog').forEach(el => el.remove());
    }

    async function createShare() {
        const ttl = document.getElementById('kk-share-ttl').value;
        const password = document.getElementById('kk-share-password').value;
        const resultDiv = document.getElementById('kk-share-result');

        try {
            const response = await fetch('/api/share/create', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    url: window.location.href,
                    fileName: document.title || '未命名文件',
                    password: password || null,
                    ttlHours: parseInt(ttl)
                })
            });
            const data = await response.json();
            if (response.ok && data.shareUrl) {
                const fullUrl = window.location.origin + data.shareUrl;
                resultDiv.innerHTML = `
                    <div class="kk-collab-share-link" id="kk-share-link">${fullUrl}</div>
                    <div class="actions">
                        <button class="btn-primary" onclick="copyShareLink('${fullUrl}')">复制链接</button>
                    </div>
                `;
            } else {
                resultDiv.innerHTML = `<p style="color:#dc3545;margin-top:10px;">${data.error || '创建失败'}</p>`;
            }
        } catch (err) {
            resultDiv.innerHTML = `<p style="color:#dc3545;margin-top:10px;">网络错误</p>`;
        }
    }

    function copyShareLink(url) {
        navigator.clipboard.writeText(url).then(() => {
            alert('链接已复制到剪贴板');
        }).catch(() => {
            prompt('请手动复制:', url);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initShareButton);
    } else {
        initShareButton();
    }
</script>
</#if>
