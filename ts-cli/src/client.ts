/**
 * WebSocket client — matches python-cli/jwcode/client.py protocol with Java backend.
 */
import WebSocket from 'ws';
import { appendFileSync, existsSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { homedir } from 'node:os';
import type { WSMessage } from './protocol.js';

type Handler = (msg: WSMessage) => void;

const PING_INTERVAL = 15000;
const MAX_RECONNECT_DELAY = 30000;
const MAX_RECONNECT_RETRIES = 50;
const CONNECT_TIMEOUT = 30000;

/** Write a line to ~/.jwcode/logs/jwcode.log. Creates the directory if needed. */
function writeLog(msg: string): void {
  try {
    const dir = join(homedir(), '.jwcode', 'logs');
    if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
    appendFileSync(join(dir, 'jwcode-cli.log'), new Date().toISOString().slice(11, 19) + ' ' + msg + '\n');
  } catch {}
}

function stderr(msg: string): void {
  try { process.stderr.write(msg); } catch {}
}

export function debugLog(cat: string, msg: string): void {
  writeLog('[' + cat + '] ' + msg);
  if (process.env.JWCODE_DEBUG_STDERR) {
    try { process.stderr.write('[dbg ' + cat + '] ' + msg + '\n'); } catch {}
  }
}

export class JwCodeClient {
  private ws: WebSocket | null = null;
  private handlers = new Map<string, Set<Handler>>();
  private _running = false;
  private _reconnecting = false;
  private _reconnectRetries = 0;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectDelay = 1000;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  /** Saved sessionId to reuse across reconnects */
  private _previousSessionId: string | null = null;
  /** Messages queued while disconnected, replayed on reconnect */
  private _pendingMessages: Array<{ msgType: string; message?: string; data?: Record<string, unknown> }> = [];

  sessionId: string | null = null;
  backendUrl: string;
  wsUrl: string;
  token: string;

  constructor(backendUrl: string, wsUrl: string, token = 'default-token') {
    this.backendUrl = backendUrl;
    this.wsUrl = wsUrl;
    this.token = token;
  }

  on(msgType: string, handler: Handler): () => void {
    if (!this.handlers.has(msgType)) this.handlers.set(msgType, new Set());
    this.handlers.get(msgType)!.add(handler);
    return () => this.handlers.get(msgType)?.delete(handler);
  }

  async connect(): Promise<string> {
    this._running = true;
    writeLog('[CONNECT] Connecting to ' + this.wsUrl);

    // Tear down the old socket before creating a new one — prevents stale
    // error events on a half-dead TCP socket from crashing the process.
    // Remove the close/error listeners first so the async close event from
    // a socket WE are closing doesn't fire our reconnect logic.
    if (this.ws) {
      writeLog('[CONNECT] Closing old socket');
      this.ws.removeAllListeners('close');
      this.ws.removeAllListeners('error');
      try { this.ws.close(); } catch {}
      this.ws = null;
    }

    return new Promise((resolve, reject) => {
      let settled = false;

      // 连接超时：WS 建立后如果 auth_success 一直不来，主动放弃
      const connectTimer = setTimeout(() => {
        if (!settled) {
          settled = true;
          reject(new Error('Connection timed out'));
          if (this.ws) { try { this.ws.close(); } catch {} }
        }
      }, CONNECT_TIMEOUT);

      const settle = (fn: (v: any) => void, v: any) => {
        if (!settled) { settled = true; clearTimeout(connectTimer); fn(v); }
      };

      try {
        this.ws = new WebSocket(this.wsUrl);
      } catch (e) {
        settle(reject, e);
        return;
      }

      // Silence EPIPE on the raw TCP socket.  The ws library normally
      // forwards socket errors to ws.on('error'), but there is a narrow
      // window after a remote-close where a queued write hits EPIPE on the
      // socket and ws has already removed its internal error listener.
      const sock = (this.ws as any)._socket as NodeJS.EventEmitter | undefined;
      if (sock) sock.on('error', () => {});

      this.ws.on('message', (raw: WebSocket.Data) => {
        let data: WSMessage;
        try { data = JSON.parse(raw.toString()); } catch { return; }

        if (data.type === 'auth_required') {
          this.ws!.send(JSON.stringify({ type: 'auth', token: this.token }));
          return;
        }
        if (data.type === 'auth_success') {
          // Only reuse the saved sessionId on a reconnect — first connect
          // has no previous SID to keep.
          const reuse = this._reconnectRetries > 0 ? this._previousSessionId : null;
          this._createSession(reuse).then(sid => {
            this.sessionId = sid;
            this._previousSessionId = null;
            this._startHeartbeat();
            this._startReadLoop();
            this._reconnecting = false;
            this._reconnectRetries = 0;
            this.reconnectDelay = 1000;
            settle(resolve, sid);
            // Flush pending messages after reconnect
            this._flushPendingMessages();
          }).catch(err => settle(reject, err));
          return;
        }
        if (data.type === 'auth_failed') {
          settle(reject, new Error(`Auth failed: ${JSON.stringify(data)}`));
          return;
        }
      });

      this.ws.on('error', (err) => {
        if (!this.sessionId) settle(reject, err);
        else this.dispatch({ type: 'error', data: `WebSocket error: ${err.message}` });
      });

      this.ws.on('close', () => {
        this._stopHeartbeat();
        writeLog('[CONNECT] WS close event fired');
        this.dispatch({ type: 'disconnected', data: 'Connection closed' });
        // 无条件 reject 当前 Promise（防止挂起）并触发重连
        // _reconnecting 可能锁死后续重连，这里先解锁再调度
        if (!this.sessionId) {
          settle(reject, new Error('Connection closed before authentication'));
        }
        if (this._running) {
          this._reconnecting = false;
          this._startReconnect();
        }
      });
    });
  }

  private async _createSession(existingSessionId?: string | null): Promise<string> {
    writeLog('[SESS] Creating session at ' + this.backendUrl + '/api/sessions' + (existingSessionId ? ' (reusing ' + existingSessionId + ')' : ''));
    const body: Record<string, unknown> = {};
    if (existingSessionId) body.sessionId = existingSessionId;
    const resp = await fetch(`${this.backendUrl}/api/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await resp.json() as Record<string, unknown>;
    const inner = (data.data as Record<string, unknown>) || {};
    const sid = (inner.sessionId as string) || (data.sessionId as string) || (data.id as string) || 'default-session';
    writeLog('[SESS] Created session: ' + sid);
    stderr(`[ws] Session: ${sid}\n`);
    return sid;
  }

  private _startReadLoop(): void {
    if (!this.ws) return;
    this.ws.on('message', (raw: WebSocket.Data) => {
      let data: WSMessage;
      try { data = JSON.parse(raw.toString()); } catch { return; }
      writeLog('[RECV] type=' + data.type + ' data=' + (typeof data.data === 'string' ? data.data.slice(0, 80) : JSON.stringify(data).slice(0, 80)));
      if (data.type === 'auth_required' || data.type === 'auth_success' || data.type === 'auth_failed') return;
      // Reply to server PINGs — without this the server disconnects after 90s.
      // Also drop server PONGs (response to our client-initiated PING) so they
      // don't fall through to dispatch with "no handlers".
      if (data.type === 'ping') {
        if (this.ws?.readyState === WebSocket.OPEN) {
          try { this.ws.send(JSON.stringify({ type: 'pong' })); } catch {}
        }
        return;
      }
      if (data.type === 'pong') return;
      this.dispatch(data);
    });
  }

  private _startHeartbeat(): void {
    this._stopHeartbeat();
    // 立即发第一个 ping，不等 setInterval
    this._sendPing();
    this.pingTimer = setInterval(() => {
      this._sendPing();
    }, PING_INTERVAL);
  }

  private _sendPing(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      try {
        this.ws.send(JSON.stringify({ type: 'ping' }));
      } catch {
        // Socket may have closed between the readyState check and send
      }
    }
  }

  private _stopHeartbeat(): void {
    if (this.pingTimer) { clearInterval(this.pingTimer); this.pingTimer = null; }
  }

  private _startReconnect(): void {
    if (!this._running) return;
    if (this._reconnectRetries >= MAX_RECONNECT_RETRIES) {
      stderr(`[ws] Max reconnect retries (${MAX_RECONNECT_RETRIES}) reached, giving up.\n`);
      this.dispatch({ type: 'error', data: 'Connection lost — max retries reached.' });
      return;
    }
    // 防止并发：上一个 setTimeout 还没到期的窗口期
    if (this._reconnecting) return;

    // Save current sessionId so it can be reused after reconnect
    if (this.sessionId) this._previousSessionId = this.sessionId;
    this._reconnecting = true;
    this._reconnectRetries++;

    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }

    this.reconnectTimer = setTimeout(async () => {
      // Bail out if the client was closed while we were waiting
      if (!this._running) return;
      try {
        stderr(`[ws] Reconnecting (attempt ${this._reconnectRetries})...\n`);
        this.dispatch({ type: 'notification', data: `Reconnecting (${this._reconnectRetries})...` });
        await this.connect();
        // On success, _reconnecting and _reconnectRetries are reset inside connect()
        this.dispatch({ type: 'notification', data: 'Reconnected.' });
      } catch {
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY);
        this._reconnecting = false;
        this._startReconnect();
      }
    }, this.reconnectDelay);
  }

  private dispatch(msg: WSMessage): void {
    writeLog('[DISPATCH] type=' + msg.type + ' sid=' + (this.sessionId || 'null'));
    const handlers = this.handlers.get(msg.type);
    if (!handlers) { writeLog('[DISPATCH] no handlers for type=' + msg.type); return; }
    for (const h of handlers) {
      try { h(msg); } catch (e) { console.error(`Handler error [${msg.type}]:`, e); writeLog('[DISPATCH] ERROR: ' + String(e)); }
    }
  }

  send(msgType: string, message?: string, data?: Record<string, unknown>): void {
    debugLog('send', msgType + (message ? ' msg=' + message.slice(0, 40) : '') + (data ? ' data=' + JSON.stringify(data).slice(0, 60) : ''));
    writeLog('[SEND] type=' + msgType + ' sid=' + (this.sessionId || 'null') + (message ? ' msg=' + message.slice(0, 60) : ''));
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      writeLog('[SEND] NOT CONNECTED, dropping: ' + msgType);
      // Cache chat/plan messages for retry on reconnect
      if (msgType === 'chat' || msgType === 'plan') {
        this._pendingMessages.push({ msgType, message, data });
        writeLog('[SEND] CACHED pending message: ' + msgType);
        console.warn(`[ws] Not connected, queued ${msgType} for retry`);
      } else {
        console.warn(`[ws] Not connected, dropping: ${msgType}`);
        this.dispatch({ type: 'notification', data: `Cannot ${msgType} — not connected` });
      }
      return;
    }
    const payload: Record<string, unknown> = { type: msgType, sessionId: this.sessionId };
    if (message !== undefined) payload.message = message;
    if (data) payload.data = typeof data == "string" ? data : JSON.stringify(data);
    try {
      this.ws.send(JSON.stringify(payload));
      writeLog('[SEND] OK type=' + msgType + ' sid=' + (this.sessionId || 'null'));
    } catch (e) {
      writeLog('[SEND] ERROR type=' + msgType + ' ' + String(e));
      console.error(`[ws] send error [${msgType}]:`, e);
    }
  }

  /** Replay any messages that were queued while disconnected. */
  private _flushPendingMessages(): void {
    if (this._pendingMessages.length === 0) return
    const batch = this._pendingMessages.splice(0)
    writeLog('[FLUSH] Replaying ' + batch.length + ' pending messages')
    stderr(`[ws] Replaying ${batch.length} queued message(s)\n`)
    for (const cached of batch) {
      this.send(cached.msgType, cached.message, cached.data)
    }
  }

  chat(content: string, planMode = false): void { this.send(planMode ? 'plan' : 'chat', content); }
  stop(): void { debugLog('send', 'stop'); this.send('stop'); }
  pause(): void { this.send('pause'); }
  resume(): void { this.send('resume'); }
  planConfirm(): void { this.send('plan_confirm'); }
  updateDocs(): void { this.send('update_docs'); }
  doctor(): void { this.send('doctor'); }
  rewind(): void { this.send('rewind'); }
  compact(): void { this.send('compact'); }
  switchModel(model: string): void { this.send('model_change', undefined, { model }); }
  approveHook(approvalId: string): void { debugLog('send', 'approveHook ' + approvalId); this.send('hook_allow', undefined, { approvalId }); }
  denyHook(approvalId: string): void { debugLog('send', 'denyHook ' + approvalId); this.send('hook_deny', undefined, { approvalId }); }
  exit(): void { this.send('exit'); }
  init(): void { this.send('init'); }
  effort(level: string): void { this.send('effort', undefined, { level }); }
  branch(name: string): void { this.send('branch', undefined, { name }); }
  mcp(action: string): void { this.send('mcp', undefined, { action }); }
  skills(): void { this.send('skills'); }
  agents(): void { this.send('agents'); }
  config(action: string): void { this.send('config', undefined, { action }); }
  plugin(action: string): void { this.send('plugin', undefined, { action }); }

  // File API for @ file references with local filesystem fallback
  async listFiles(query?: string): Promise<string[]> {
    // Try HTTP API first
    try {
      const url = query
        ? `${this.backendUrl}/api/files?path=${encodeURIComponent(query)}`
        : `${this.backendUrl}/api/files`;
      const resp = await fetch(url);
      if (resp.ok) {
        const data = await resp.json() as any;
        // Flatten FileNode tree to path list
        const paths: string[] = [];
        const walk = (nodes: any[]) => {
          for (const n of nodes) {
            if (n.type === 'file') paths.push(n.path);
            if (n.children) walk(n.children);
          }
        };
        walk(data?.data || data || []);
        if (paths.length > 0) return paths;
      }
    } catch { /* HTTP failed, fall through to filesystem */ }

    // Fallback: scan local filesystem (max 3 levels deep, skip node_modules/.git/target)
    try {
      const skipDirs = new Set(['node_modules', '.git', 'target', '.next', 'dist', 'build', '.cache', '.vite', 'coverage', '__pycache__', '.venv', 'vendor'])
      const cwd = process.cwd()
      const results: string[] = []
      const queryLower = (query || '').toLowerCase()

      function scan(dir: string, depth: number) {
        if (depth > 3) return
        let entries: string[]
        try { entries = readdirSync(dir) } catch { return }
        for (const entry of entries) {
          const full = join(dir, entry)
          try {
            const stat = statSync(full)
            const relative = full.startsWith(cwd + '\\') ? full.slice(cwd.length + 1) : entry
            if (stat.isDirectory()) {
              if (!skipDirs.has(entry)) scan(full, depth + 1)
            } else if (stat.isFile()) {
              if (!queryLower || entry.toLowerCase().includes(queryLower)) {
                results.push(relative)
              }
            }
          } catch { /* skip unreadable */ }
        }
      }

      scan(cwd, 0)
      // Limit results to avoid overwhelming UI
      return results.slice(0, 200)
    } catch { return []; }
  }

  async readFileContent(path: string): Promise<string | null> {
    try {
      const resp = await fetch(`${this.backendUrl}/api/files/read?path=${encodeURIComponent(path)}`);
      if (!resp.ok) return null;
      const data = await resp.json() as any;
      return (data?.data || data) as string;
    } catch { return null; }
  }

  // Session history API
  async listSessions(): Promise<Array<{ id: string; title: string; createdAt: string; updatedAt: string; messageCount: number }>> {
    try {
      const resp = await fetch(`${this.backendUrl}/api/sessions`);
      const data = await resp.json() as any;
      return (data?.data || data || []);
    } catch { return []; }
  }

  async deleteSession(sessionId: string): Promise<boolean> {
    try {
      const resp = await fetch(`${this.backendUrl}/api/sessions/${sessionId}`, { method: 'DELETE' });
      const data = await resp.json() as any;
      return data?.success === true;
    } catch { return false; }
  }

  async getSessionMessages(sessionId: string): Promise<any[]> {
    try {
      const resp = await fetch(`${this.backendUrl}/api/sessions/${sessionId}/messages`);
      const data = await resp.json() as any;
      return (data?.data || []);
    } catch { return []; }
  }

  async close(): Promise<void> {
    this._running = false;
    this._reconnecting = false;
    this._stopHeartbeat();
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
    // Drop any queued messages — they were captured against a now-dead socket
    this._pendingMessages = [];
    if (this.ws) { this.ws.close(); this.ws = null; }
  }
}
