<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${fileName} 文件处理中</title>
  <style>
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
    }

    body {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      color: #4f2a12;
      background:
        radial-gradient(circle at top, rgba(255, 227, 181, 0.72), rgba(255, 250, 242, 0) 36%),
        linear-gradient(180deg, #fff7ee 0%, #fffdf9 100%);
    }

    .loading-card {
      width: min(760px, calc(100vw - 48px));
      padding: 36px 44px 40px;
      border-radius: 40px;
      background: rgba(255, 252, 246, 0.94);
      border: 1px solid rgba(243, 207, 153, 0.5);
      box-shadow:
        0 24px 64px rgba(212, 146, 58, 0.16),
        inset 0 1px 0 rgba(255, 255, 255, 0.9);
      text-align: center;
      backdrop-filter: blur(8px);
    }

    .loading-stage {
      position: relative;
      display: inline-flex;
      justify-content: center;
      align-items: center;
      width: min(360px, 100%);
      margin: 0 auto 10px;
    }

    .loading-stage::before {
      content: '';
      position: absolute;
      inset: 16px 30px 30px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(255, 196, 96, 0.34), rgba(255, 196, 96, 0) 72%);
      filter: blur(10px);
      animation: bearGlow 3s ease-in-out infinite;
    }

    .loading-image {
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

    .loading-file {
      display: inline-flex;
      max-width: 100%;
      margin-bottom: 14px;
      padding: 7px 14px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.8);
      border: 1px solid rgba(232, 206, 165, 0.9);
      color: #8b5b34;
      font-size: 13px;
      font-weight: 600;
      line-height: 1.5;
      word-break: break-all;
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
      line-height: 1.8;
      color: #9b6844;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .loading-meta {
      display: flex;
      justify-content: center;
      gap: 10px;
      flex-wrap: wrap;
      margin-top: 16px;
    }

    .loading-pill {
      padding: 5px 12px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.84);
      border: 1px solid rgba(222, 195, 154, 0.72);
      color: #8b5b34;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.04em;
    }

    .loading-pill--progress {
      color: #b95b1d;
      background: rgba(255, 241, 221, 0.9);
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

    .loading-progress {
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

    .loading-progress__bar {
      position: relative;
      width: 0;
      height: 100%;
      border-radius: inherit;
      background: linear-gradient(90deg, #ff8f49 0%, #ffb95e 56%, #ffd57a 100%);
      transition: width 0.35s ease;
      box-shadow: 0 8px 20px rgba(255, 146, 66, 0.3);
    }

    .loading-progress__bar::after {
      content: '';
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

    .loading-actions {
      display: flex;
      justify-content: center;
      gap: 12px;
      flex-wrap: wrap;
      margin-top: 28px;
    }

    .loading-btn {
      min-width: 180px;
      padding: 13px 24px;
      border-radius: 999px;
      border: none;
      cursor: pointer;
      font-size: 15px;
      font-weight: 600;
      transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
    }

    .loading-btn:hover {
      transform: translateY(-1px);
      box-shadow: 0 12px 22px rgba(255, 146, 66, 0.2);
    }

    .loading-btn--primary {
      color: #fff;
      background: linear-gradient(90deg, #ff8f49 0%, #ffb95e 100%);
    }

    .loading-btn--secondary {
      color: #8b5b34;
      background: rgba(255, 255, 255, 0.82);
      border: 1px solid rgba(222, 195, 154, 0.9);
    }

    .loading-tips {
      width: min(520px, 100%);
      margin: 16px auto 0;
      padding: 12px 14px;
      border-radius: 16px;
      background: rgba(251, 146, 60, 0.12);
      border: 1px solid rgba(251, 146, 60, 0.18);
      color: #b95b1d;
      font-size: 13px;
      line-height: 1.7;
      text-align: left;
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
      body {
        padding: 16px;
      }

      .loading-card {
        width: calc(100vw - 32px);
        padding: 26px 22px 30px;
        border-radius: 28px;
      }

      .loading-progress {
        margin-top: 22px;
        margin-bottom: 12px;
      }

      .loading-btn {
        width: 100%;
      }
    }
  </style>
</head>
<body>
  <div class="loading-card">
    <div class="loading-stage">
      <img class="loading-image" src="images/loading-bear.png" alt="正在整理资料">
    </div>
    <div class="loading-file">${fileName}</div>
    <div class="loading-title">正在整理资料...</div>
    <div class="loading-subtitle" id="loadingMessage">${message}</div>
    <div class="loading-meta">
      <span class="loading-pill">将在 <span id="countdown">${time}</span> 秒后自动刷新</span>
      <span class="loading-pill loading-pill--progress">当前进度 <span id="progressText">0%</span></span>
    </div>
    <div class="loading-dots" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
    </div>
    <div class="loading-progress">
      <div id="loadingBar" class="loading-progress__bar"></div>
    </div>
    <div class="loading-caption">马上就好啦！</div>
    <div class="loading-actions">
      <button class="loading-btn loading-btn--primary" id="refreshBtn">立即刷新</button>
      <button class="loading-btn loading-btn--secondary" id="backBtn">返回上一页</button>
    </div>
    <div class="loading-tips">
      文件转换时间会受文件大小和服务器负载影响。
      如果长时间未完成，可以先稍后再试，或联系管理员检查转换服务状态。
    </div>
  </div>
  <script>
    let countdown = ${time};
    const initialCountdown = Math.max(countdown, 1);
    let countdownInterval;

    const progressBar = document.getElementById('loadingBar');
    const progressText = document.getElementById('progressText');
    const messageElement = document.getElementById('loadingMessage');

    function cleanForceUpdateParam() {
      const url = new URL(window.location.href);
      const params = new URLSearchParams(url.search);
      if (!params.has('forceUpdatedCache')) {
        return;
      }
      params.delete('forceUpdatedCache');
      const newSearch = params.toString();
      const newUrl = url.origin + url.pathname + (newSearch ? '?' + newSearch : '');
      window.history.replaceState({}, document.title, newUrl);
    }

    function extractProgressFromMessage(message) {
      if (!message) return null;
      const matched = message.match(/进度[:：]\s*(\d{1,3})%/);
      if (!matched) return null;
      const value = Number(matched[1]);
      if (Number.isNaN(value)) return null;
      return Math.max(0, Math.min(100, value));
    }

    function setProgress(progress) {
      const percent = Math.max(8, Math.min(96, Math.round(progress)));
      progressBar.style.width = percent + '%';
      progressText.textContent = percent + '%';
    }

    function syncCountdownProgress() {
      const detectedProgress = extractProgressFromMessage(messageElement.textContent);
      if (detectedProgress !== null) {
        const upper = Math.min(96, detectedProgress + 8);
        const elapsedRatio = (initialCountdown - countdown) / initialCountdown;
        setProgress(detectedProgress + (upper - detectedProgress) * elapsedRatio);
        return;
      }
      const elapsedRatio = (initialCountdown - countdown) / initialCountdown;
      setProgress(12 + elapsedRatio * 74);
    }

    function startCountdown() {
      const countdownElement = document.getElementById('countdown');
      syncCountdownProgress();
      countdownInterval = setInterval(() => {
        if (countdown > 0) {
          countdown--;
          countdownElement.textContent = countdown;
          syncCountdownProgress();
          return;
        }
        clearInterval(countdownInterval);
        setProgress(98);
        window.location.reload();
      }, 1000);
    }

    document.getElementById('refreshBtn').addEventListener('click', function() {
      setProgress(98);
      window.location.reload();
    });

    document.getElementById('backBtn').addEventListener('click', function() {
      window.history.back();
    });

    window.addEventListener('load', function() {
      cleanForceUpdateParam();
      startCountdown();
    });
  </script>
</body>
</html>
