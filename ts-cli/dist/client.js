/**
 * WebSocket client — matches python-cli/jwcode/client.py protocol with Java backend.
 *
 * Protocol flow:
 *   1. Connect ws://localhost:{wsPort}/ws
 *   2. Receive auth_required → send {type: "auth", token: "default-token"}
 *   3. Receive auth_success → POST /api/sessions to create session
 *   4. Start read loop, dispatch 25+ event types to registered handlers
 */
import WebSocket from 'ws';
const PING_INTERVAL = 30000;
export class JwCodeClient {
    ws = null;
    handlers = new Map();
    _running = false;
    pingTimer = null;
    reconnectDelay = 1000;
    reconnectTimer = null;
    sessionId = null;
    backendUrl;
    wsUrl;
    token;
    constructor(backendUrl, wsUrl, token = 'default-token') {
        this.backendUrl = backendUrl;
        this.wsUrl = wsUrl;
        this.token = token;
    }
    on(msgType, handler) {
        if (!this.handlers.has(msgType))
            this.handlers.set(msgType, new Set());
        this.handlers.get(msgType).add(handler);
        return () => this.handlers.get(msgType)?.delete(handler);
    }
    async connect() {
        this._running = true;
        this.reconnectDelay = 1000;
        return new Promise((resolve, reject) => {
            try {
                this.ws = new WebSocket(this.wsUrl);
            }
            catch (e) {
                reject(e);
                return;
            }
            this.ws.on('open', () => {
                // Wait for auth flow on message
            });
            this.ws.on('message', (raw) => {
                let data;
                try {
                    data = JSON.parse(raw.toString());
                }
                catch {
                    return;
                }
                // Auth flow
                if (data.type === 'auth_required') {
                    this.ws.send(JSON.stringify({ type: 'auth', token: this.token }));
                    return;
                }
                if (data.type === 'auth_success') {
                    this._createSession().then(sid => {
                        this.sessionId = sid;
                        this._startHeartbeat();
                        this._startReadLoop();
                        resolve(sid);
                    }).catch(reject);
                    return;
                }
                if (data.type === 'auth_failed') {
                    reject(new Error(`Auth failed: ${JSON.stringify(data)}`));
                    return;
                }
            });
            this.ws.on('error', (err) => {
                if (!this.sessionId)
                    reject(err);
                else
                    this.dispatch({ type: 'error', data: `WebSocket error: ${err.message}` });
            });
            this.ws.on('close', () => {
                this._stopHeartbeat();
                if (this._running) {
                    this._scheduleReconnect();
                }
            });
        });
    }
    async _createSession() {
        const resp = await fetch(`${this.backendUrl}/api/sessions`, { method: 'POST' });
        const data = await resp.json();
        const inner = data.data || {};
        const sid = inner.sessionId || data.sessionId || data.id || 'default-session';
        console.log(`[ws] Session: ${sid}`);
        return sid;
    }
    _startReadLoop() {
        if (!this.ws)
            return;
        const ws = this.ws;
        ws.on('message', (raw) => {
            let data;
            try {
                data = JSON.parse(raw.toString());
            }
            catch {
                return;
            }
            // Skip auth messages (handled in connect)
            if (data.type === 'auth_required' || data.type === 'auth_success' || data.type === 'auth_failed')
                return;
            console.log(`[ws] recv: ${data.type}`);
            this.dispatch(data);
        });
    }
    _startHeartbeat() {
        this._stopHeartbeat();
        this.pingTimer = setInterval(() => {
            if (this.ws?.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify({ type: 'ping' }));
            }
        }, PING_INTERVAL);
    }
    _stopHeartbeat() {
        if (this.pingTimer) {
            clearInterval(this.pingTimer);
            this.pingTimer = null;
        }
    }
    _scheduleReconnect() {
        if (!this._running)
            return;
        console.log(`[ws] Reconnecting in ${this.reconnectDelay / 1000}s...`);
        this.dispatch({ type: 'notification', data: 'Connection lost, reconnecting...' });
        this.reconnectTimer = setTimeout(async () => {
            try {
                await this.connect();
                this.reconnectDelay = 1000;
                this.dispatch({ type: 'notification', data: 'Reconnected.' });
            }
            catch {
                this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30000);
                this._scheduleReconnect();
            }
        }, this.reconnectDelay);
    }
    dispatch(msg) {
        const handlers = this.handlers.get(msg.type);
        if (!handlers)
            return;
        for (const h of handlers) {
            try {
                h(msg);
            }
            catch (e) {
                console.error(`Handler error [${msg.type}]:`, e);
            }
        }
    }
    send(msgType, message, data) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn(`[ws] Not connected, dropping: ${msgType}`);
            return;
        }
        const payload = {
            type: msgType,
            sessionId: this.sessionId,
        };
        if (message !== undefined)
            payload.message = message;
        if (data)
            payload.data = JSON.stringify(data);
        try {
            this.ws.send(JSON.stringify(payload));
            console.log(`[ws] sent: ${msgType}`);
        }
        catch (e) {
            console.error(`[ws] send error [${msgType}]:`, e);
        }
    }
    chat(content, planMode = false) {
        this.send(planMode ? 'plan' : 'chat', content);
    }
    stop() { this.send('stop'); }
    pause() { this.send('pause'); }
    resume() { this.send('resume'); }
    planConfirm() { this.send('plan_confirm'); }
    updateDocs() { this.send('update_docs'); }
    doctor() { this.send('doctor'); }
    rewind() { this.send('rewind'); }
    compact() { this.send('compact'); }
    switchModel(model) { this.send('model_change', undefined, { model }); }
    approveHook(approvalId) {
        this.send('hook_allow', undefined, { approvalId });
    }
    denyHook(approvalId) {
        this.send('hook_deny', undefined, { approvalId });
    }
    async close() {
        this._running = false;
        this._stopHeartbeat();
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }
}
