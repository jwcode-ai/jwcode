/**
 * WebSocket client — matches python-cli/jwcode/client.py protocol with Java backend.
 */
import WebSocket from 'ws';
import type { WSMessage } from './protocol.js';

type Handler = (msg: WSMessage) => void;

const PING_INTERVAL = 30000;
const MAX_RECONNECT_DELAY = 30000;

export class JwCodeClient {
  private ws: WebSocket | null = null;
  private handlers = new Map<string, Set<Handler>>();
  private _running = false;
  private _reconnecting = false;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectDelay = 1000;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

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

    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.wsUrl);
      } catch (e) {
        reject(e);
        return;
      }

      this.ws.on('message', (raw: WebSocket.Data) => {
        let data: WSMessage;
        try { data = JSON.parse(raw.toString()); } catch { return; }

        if (data.type === 'auth_required') {
          this.ws!.send(JSON.stringify({ type: 'auth', token: this.token }));
          return;
        }
        if (data.type === 'auth_success') {
          this._createSession().then(sid => {
            this.sessionId = sid;
            this._startHeartbeat();
            this._startReadLoop();
            this._reconnecting = false;
            this.reconnectDelay = 1000;
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
        // If we haven't authenticated yet, reject so the initial connect / reconnect catch handles it
        if (!this.sessionId) reject(err);
        else this.dispatch({ type: 'error', data: `WebSocket error: ${err.message}` });
      });

      this.ws.on('close', () => {
        this._stopHeartbeat();
        if (this._running && !this._reconnecting) {
          this._startReconnect();
        }
      });
    });
  }

  private async _createSession(): Promise<string> {
    const resp = await fetch(`${this.backendUrl}/api/sessions`, { method: 'POST' });
    const data = await resp.json() as Record<string, unknown>;
    const inner = (data.data as Record<string, unknown>) || {};
    const sid = (inner.sessionId as string) || (data.sessionId as string) || (data.id as string) || 'default-session';
    console.log(`[ws] Session: ${sid}`);
    return sid;
  }

  private _startReadLoop(): void {
    if (!this.ws) return;
    this.ws.on('message', (raw: WebSocket.Data) => {
      let data: WSMessage;
      try { data = JSON.parse(raw.toString()); } catch { return; }
      if (data.type === 'auth_required' || data.type === 'auth_success' || data.type === 'auth_failed') return;
      this.dispatch(data);
    });
  }

  private _startHeartbeat(): void {
    this._stopHeartbeat();
    this.pingTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'ping' }));
      }
    }, PING_INTERVAL);
  }

  private _stopHeartbeat(): void {
    if (this.pingTimer) { clearInterval(this.pingTimer); this.pingTimer = null; }
  }

  private _startReconnect(): void {
    if (!this._running || this._reconnecting) return;
    this._reconnecting = true;

    // Clear any stale timer
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }

    this.reconnectTimer = setTimeout(async () => {
      try {
        console.log(`[ws] Reconnecting...`);
        this.dispatch({ type: 'notification', data: 'Reconnecting...' });
        await this.connect();
        this._reconnecting = false;
        this.dispatch({ type: 'notification', data: 'Reconnected.' });
      } catch {
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY);
        this._reconnecting = false; // allow next schedule
        this._startReconnect();
      }
    }, this.reconnectDelay);
  }

  private dispatch(msg: WSMessage): void {
    const handlers = this.handlers.get(msg.type);
    if (!handlers) return;
    for (const h of handlers) {
      try { h(msg); } catch (e) { console.error(`Handler error [${msg.type}]:`, e); }
    }
  }

  send(msgType: string, message?: string, data?: Record<string, unknown>): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn(`[ws] Not connected, dropping: ${msgType}`);
      return;
    }
    const payload: Record<string, unknown> = { type: msgType, sessionId: this.sessionId };
    if (message !== undefined) payload.message = message;
    if (data) payload.data = JSON.stringify(data);
    try {
      this.ws.send(JSON.stringify(payload));
    } catch (e) {
      console.error(`[ws] send error [${msgType}]:`, e);
    }
  }

  chat(content: string, planMode = false): void { this.send(planMode ? 'plan' : 'chat', content); }
  stop(): void { this.send('stop'); }
  pause(): void { this.send('pause'); }
  resume(): void { this.send('resume'); }
  planConfirm(): void { this.send('plan_confirm'); }
  updateDocs(): void { this.send('update_docs'); }
  doctor(): void { this.send('doctor'); }
  rewind(): void { this.send('rewind'); }
  compact(): void { this.send('compact'); }
  switchModel(model: string): void { this.send('model_change', undefined, { model }); }
  approveHook(approvalId: string): void { this.send('hook_allow', undefined, { approvalId }); }
  denyHook(approvalId: string): void { this.send('hook_deny', undefined, { approvalId }); }
  init(): void { this.send('init'); }
  effort(level: string): void { this.send('effort', undefined, { level }); }
  branch(name: string): void { this.send('branch', undefined, { name }); }
  mcp(action: string): void { this.send('mcp', undefined, { action }); }
  skills(): void { this.send('skills'); }
  agents(): void { this.send('agents'); }
  config(action: string): void { this.send('config', undefined, { action }); }
  plugin(action: string): void { this.send('plugin', undefined, { action }); }

  async close(): Promise<void> {
    this._running = false;
    this._reconnecting = false;
    this._stopHeartbeat();
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
    if (this.ws) { this.ws.close(); this.ws = null; }
  }
}
