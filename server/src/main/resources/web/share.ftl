<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>分享验证 - kkFileView</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
            background: #f5f5f5;
        }
        .container {
            background: white;
            padding: 2rem;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            width: 100%;
            max-width: 400px;
        }
        h2 {
            margin-top: 0;
            color: #333;
        }
        input[type="password"] {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid #ddd;
            border-radius: 4px;
            margin: 1rem 0;
            font-size: 1rem;
        }
        button {
            width: 100%;
            padding: 0.75rem;
            background: #007bff;
            color: white;
            border: none;
            border-radius: 4px;
            font-size: 1rem;
            cursor: pointer;
        }
        button:hover {
            background: #0056b3;
        }
        .error {
            color: #dc3545;
            margin-top: 1rem;
        }
    </style>
</head>
<body>
<div class="container">
    <h2>请输入分享密码</h2>
    <input type="password" id="password" placeholder="输入密码" autofocus>
    <button onclick="submitPassword()">验证</button>
    <div id="error" class="error" style="display: none;"></div>
</div>
<script>
    const token = "${shareToken!''}";

    async function submitPassword() {
        const password = document.getElementById('password').value;
        const errorDiv = document.getElementById('error');

        try {
            const response = await fetch('/api/share/verify/' + token, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({password: password})
            });

            const data = await response.json();

            if (response.ok && data.success) {
                window.location.reload();
            } else {
                errorDiv.textContent = data.error || '验证失败';
                errorDiv.style.display = 'block';
            }
        } catch (err) {
            errorDiv.textContent = '网络错误';
            errorDiv.style.display = 'block';
        }
    }

    document.getElementById('password').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') submitPassword();
    });
</script>
</body>
</html>