/**
 * @file collab-stomp.js
 * 在线协作（STOMP over SockJS）公共客户端。
 *
 * 由 pdf.ftl 与 officeweb.ftl 两处重复的内联 STOMP 实现合并而成。
 * 职责：创建协作会话（REST）→ 动态加载 SockJS/STOMP 库 → 连接 /ws-collab
 *       → 加入会话（join）→ 订阅在线用户/光标/操作主题。
 *
 * 与后端契约要点：
 * 1. join 成功后 presence 主题会推送 {type:'join'|'leave', user, userSessionId, onlineUsers}，
 *    其中本人 join 消息里的 user.sessionId 必须保存，leave 时回传
 *    {userSessionId: 保存值}，否则后端无法移除用户（在线人数只增不减）。
 * 2. 收到 type==='error' 的消息（如功能开关关闭）时输出错误并断开连接。
 *
 * UMD 风格：浏览器环境挂载 window.initCollabStomp。
 */
(function (root, factory) {
    if (typeof module === 'object' && typeof module.exports === 'object') {
        module.exports = factory();
    } else {
        root.initCollabStomp = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    /** SockJS 本地库路径（CDN 本地化，避免外网依赖） */
    var SOCKJS_LOCAL = '/js/sockjs.min.js';
    /** STOMP 本地库路径 */
    var STOMP_LOCAL = '/js/stomp.min.js';
    /** 本地库加载失败时的 CDN 回退地址 */
    var SOCKJS_CDN = 'https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js';
    var STOMP_CDN = 'https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js';
    /** 光标广播节流间隔（毫秒），避免 mousemove 高频发送拖垮连接 */
    var CURSOR_THROTTLE_MS = 50;
    /** 在线用户心跳间隔（毫秒）：服务端按 90s 无心跳超时清扫幽灵用户 */
    var HEARTBEAT_INTERVAL_MS = 30000;
    /** 库加载的模块级缓存 Promise：防止重复注入 <script> */
    var libsPromise = null;

    /**
     * 动态加载单个脚本；本地路径失败时回退到 CDN。
     *
     * @param {string} localSrc 本地脚本地址
     * @param {string} cdnSrc CDN 回退地址
     * @returns {Promise<void>} 加载完成（或两者皆失败时 reject）
     */
    function loadScript(localSrc, cdnSrc) {
        return new Promise(function (resolve, reject) {
            var el = document.createElement('script');
            el.src = localSrc;
            el.onload = function () { resolve(); };
            el.onerror = function () {
                // 本地库缺失时回退 CDN，保证功能可用
                var fallback = document.createElement('script');
                fallback.src = cdnSrc;
                fallback.onload = function () { resolve(); };
                fallback.onerror = function () {
                    reject(new Error('脚本加载失败: ' + localSrc + ' 与 ' + cdnSrc));
                };
                document.head.appendChild(fallback);
            };
            document.head.appendChild(el);
        });
    }

    /**
     * 按 SockJS → STOMP 顺序加载依赖库（仅加载一次）。
     *
     * @returns {Promise<void>} 全部加载完成后 resolve
     */
    function loadLibs() {
        if (!libsPromise) {
            libsPromise = loadScript(SOCKJS_LOCAL, SOCKJS_CDN)
                .then(function () {
                    return loadScript(STOMP_LOCAL, STOMP_CDN);
                });
        }
        return libsPromise;
    }

    /**
     * 调用后端 REST 接口创建（或复用）协作会话。
     *
     * @param {string} fileKey 预览文件标识（预览页 URL）
     * @returns {Promise<{sessionId: string}>} 会话信息
     */
    function createSession(fileKey) {
        return fetch('/api/collab/session?fileKey=' + encodeURIComponent(fileKey), { method: 'POST' })
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('HTTP ' + res.status);
                }
                return res.json();
            });
    }

    /**
     * 确保在线用户面板存在（不存在则注入），返回面板元素。
     * 面板样式来自 /css/collab.css 的 .kk-collab-users。
     *
     * @returns {HTMLElement} 在线用户面板元素
     */
    function ensureUsersBox() {
        var box = document.getElementById('onlineUsers');
        if (box) {
            return box;
        }
        box = document.createElement('div');
        box.id = 'onlineUsers';
        box.className = 'kk-collab-users';
        box.innerHTML = '<div class="kk-collab-users-title">在线用户</div>'
            + '<div id="onlineUsersList"></div>';
        document.body.appendChild(box);
        return box;
    }

    /**
     * 用后端推送的用户列表刷新在线用户面板。
     *
     * @param {Array<{nickname: string, color: string}>} users 在线用户数组
     * @returns {void}
     */
    function updateOnlineUsers(users) {
        var list = document.getElementById('onlineUsersList');
        if (!list) return;
        list.innerHTML = '';
        users.forEach(function (user) {
            var div = document.createElement('div');
            div.className = 'kk-collab-user-item';
            var dot = document.createElement('span');
            dot.style.cssText = 'display:inline-block;width:10px;height:10px;border-radius:50%;'
                + 'background:' + (user.color || '#999') + ';margin-right:5px;';
            div.appendChild(dot);
            div.appendChild(document.createTextNode(user.nickname || '匿名'));
            list.appendChild(div);
        });
    }

    /**
     * 创建协作客户端实例并启动连接流程。
     *
     * @param {Object} options 初始化选项
     * @param {string} [options.fileKey] 预览文件标识（默认取当前页面 URL）
     * @param {boolean} [options.enableCursor=false] 是否启用鼠标光标共享（仅 PDF 启用）
     * @param {Function} [options.onCursor] 远程光标数据回调（enableCursor 时生效）
     * @param {Function} [options.onOperation] 远程操作数据回调（预留：后端操作通道尚未实现，传入才订阅）
     * @returns {void}
     */
    function initCollabStomp(options) {
        var opts = options || {};
        var fileKey = opts.fileKey || window.location.href;
        var enableCursor = !!opts.enableCursor;
        var onCursor = typeof opts.onCursor === 'function' ? opts.onCursor : null;
        var onOperation = typeof opts.onOperation === 'function' ? opts.onOperation : null;

        var stompClient = null;
        var sessionId = null;         // 协作会话 ID（REST 创建返回）
        var myNickname = '用户' + Math.floor(Math.random() * 1000);
        var myColor = '#' + Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0');
        var myUserSessionId = null;   // 本人 join 响应中的 user.sessionId（leave 必须回传）
        var lastCursorSentAt = 0;     // 上次光标广播时间（节流用）
        var heartbeatTimer = null;    // 在线用户心跳定时器（连接断开时清除）

        /**
         * 处理后端返回的错误消息：输出中文日志并断开连接。
         *
         * @param {{type: string, message?: string}} payload presence 消息体
         * @returns {boolean} 是否为错误消息（是则已断开，调用方应终止后续处理）
         */
        function handleErrorResponse(payload) {
            if (payload && payload.type === 'error') {
                console.error('协作服务返回错误: ' + (payload.message || '未知错误'));
                if (stompClient && stompClient.connected) {
                    stompClient.disconnect(function () {
                        console.info('已断开协作连接');
                    });
                }
                stopHeartbeat();
                return true;
            }
            return false;
        }

        /**
         * 停止在线用户心跳定时器（连接断开/出错/离开会话时调用）。
         *
         * @returns {void}
         */
        function stopHeartbeat() {
            if (heartbeatTimer) {
                clearInterval(heartbeatTimer);
                heartbeatTimer = null;
            }
        }

        /**
         * 启动在线用户心跳：每 30 秒向 /app/heartbeat/{sessionId} 发送本人
         * userSessionId（无响应）。服务端按"90 秒未收到心跳即超时清扫"移除
         * 幽灵用户（如浏览器崩溃未发 leave 的会话），保证在线人数准确。
         * 仅在 STOMP 连接存活且已取得本人 userSessionId 时发送。
         *
         * @returns {void}
         */
        function startHeartbeat() {
            stopHeartbeat();
            heartbeatTimer = setInterval(function () {
                if (stompClient && stompClient.connected && sessionId && myUserSessionId) {
                    stompClient.send('/app/heartbeat/' + sessionId, {},
                        JSON.stringify({ userSessionId: myUserSessionId }));
                }
            }, HEARTBEAT_INTERVAL_MS);
        }

        /**
         * 发送离开消息。
         * 必须携带 join 响应中保存的 user.sessionId（后端按此标识移除用户，
         * 否则在线用户只增不减）。STOMP 无法使用 sendBeacon，
         * beforeunload 时仍用同步 send 尽力发送。
         *
         * @returns {void}
         */
        function sendLeave() {
            stopHeartbeat();
            if (stompClient && stompClient.connected && sessionId && myUserSessionId) {
                try {
                    stompClient.send('/app/leave/' + sessionId, {},
                        JSON.stringify({ userSessionId: myUserSessionId }));
                } catch (e) {
                    // 页面正在卸载，忽略发送失败
                }
            }
        }

        /**
         * STOMP 连接建立后的回调：订阅主题并发送 join。
         *
         * @returns {void}
         */
        function onConnected() {
            console.info('已连接到协作服务');
            var usersBox = ensureUsersBox();
            usersBox.style.display = 'block';

            // 在线用户状态主题：join / leave / error
            stompClient.subscribe('/topic/presence/' + sessionId, function (message) {
                var payload = JSON.parse(message.body);
                if (handleErrorResponse(payload)) return;
                // 保存本人 join 响应中的 user.sessionId（leave 时回传给后端）
                if (payload.type === 'join' && !myUserSessionId
                        && payload.user && payload.user.nickname === myNickname) {
                    myUserSessionId = payload.user.sessionId || payload.userSessionId || null;
                }
                updateOnlineUsers(payload.onlineUsers || []);
            });

            // 操作通道：仅在调用方传入 onOperation 时订阅。
            // 预留：后端操作广播通道尚未实现，当前不会收到消息。
            if (onOperation) {
                stompClient.subscribe('/topic/session/' + sessionId, function (message) {
                    var payload = JSON.parse(message.body);
                    if (handleErrorResponse(payload)) return;
                    onOperation(payload);
                });
            }

            // 光标通道：透传转发，仅 PDF 预览启用
            if (enableCursor && onCursor) {
                stompClient.subscribe('/topic/cursor/' + sessionId, function (message) {
                    var payload = JSON.parse(message.body);
                    if (handleErrorResponse(payload)) return;
                    onCursor(payload);
                });
            }

            // 加入会话；本人 sessionId 从 presence 的 join 消息中异步获得
            stompClient.send('/app/join/' + sessionId, {},
                JSON.stringify({ nickname: myNickname, color: myColor }));

            // join 成功后启动在线用户心跳（维持服务端在线状态、防幽灵用户）
            startHeartbeat();

            // 共享本机鼠标光标（节流广播）
            if (enableCursor) {
                document.addEventListener('mousemove', function (e) {
                    if (!stompClient || !stompClient.connected) return;
                    var now = Date.now();
                    if (now - lastCursorSentAt < CURSOR_THROTTLE_MS) return;
                    lastCursorSentAt = now;
                    stompClient.send('/app/cursor/' + sessionId, {}, JSON.stringify({
                        userId: myNickname,
                        nickname: myNickname,
                        color: myColor,
                        x: e.clientX,
                        y: e.clientY
                    }));
                });
            }

            // 页面卸载前尽力发送离开消息
            window.addEventListener('beforeunload', sendLeave);
        }

        /**
         * 建立 sockjs + stomp 连接。
         *
         * @returns {void}
         */
        function connect() {
            if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
                console.error('SockJS/STOMP 客户端加载失败，跳过协作连接');
                return;
            }
            var socket = new SockJS('/ws-collab');
            stompClient = Stomp.over(socket);
            stompClient.debug = null; // 关闭 stomp 框架自身的调试输出
            stompClient.connect({}, onConnected, function (error) {
                console.error('WebSocket 连接失败:', error);
                stopHeartbeat();
            });
        }

        ensureUsersBox();
        loadLibs()
            .then(function () {
                return createSession(fileKey);
            })
            .then(function (data) {
                sessionId = data.sessionId;
                connect();
            })
            .catch(function (err) {
                console.error('创建协作会话失败:', err);
            });
    }

    return initCollabStomp;
});
