import { WSMessage } from '../types';

type MessageHandler = (message: WSMessage) => void;
type ConnectionHandler = () => void;

class WebSocketService {
  private ws: WebSocket | null = null;
  private url: string;
  private messageHandlers: Set<MessageHandler> = new Set();
  private openHandlers: Set<ConnectionHandler> = new Set();
  private closeHandlers: Set<ConnectionHandler> = new Set();
  private errorHandlers: Set<ConnectionHandler> = new Set();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private isManualClose = false;
  private token: string | null = null;
  private authenticated = false;
  
  // 心跳检测相关
  private heartbeatInterval: ReturnType<typeof setInterval> | null = null;
  private lastPongTime = 0;
  private readonly HEARTBEAT_INTERVAL = 20000; // 20秒发送一次心跳检测
  private readonly HEARTBEAT_TIMEOUT = 100000;  // 100秒未收到pong则认为断开

  constructor(url: string = `ws://${window.location.hostname}:8081`) {
    this.url = url;
  }

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    this.isManualClose = false;
    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log('WebSocket connected');
      this.reconnectAttempts = 0;
      this.lastPongTime = Date.now();
      this.startHeartbeat();
      this.openHandlers.forEach((handler) => handler());
    };

    this.ws.onclose = () => {
      console.log('WebSocket closed');
      this.stopHeartbeat();
      this.closeHandlers.forEach((handler) => handler());
      
      if (!this.isManualClose && this.reconnectAttempts < this.maxReconnectAttempts) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('[WS] WebSocket error:', error);
      this.errorHandlers.forEach((handler) => handler());
    };

    this.ws.onmessage = (event) => {
      try {
        const message: WSMessage = JSON.parse(event.data);
        
        // 处理 pong 回复（响应服务器的 ping），收到 ping 也视为活跃信号
        if (message.type === 'pong' || message.type === 'ping') {
          this.lastPongTime = Date.now();
          if (message.type === 'pong') {
            return;
          }
        }
        
        this.messageHandlers.forEach((handler) => handler(message));
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
      }
    };
  }

  /**
   * 启动心跳检测
   * 定期检查是否收到服务器的 pong 回复，如果超时则主动重连
   */
  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.lastPongTime = Date.now();
    
    this.heartbeatInterval = setInterval(() => {
      // 检查是否超过心跳超时时间
      if (Date.now() - this.lastPongTime > this.HEARTBEAT_TIMEOUT) {
        console.warn('[WS] Heartbeat timeout, reconnecting...');
        // 主动触发重连
        this.isManualClose = false;
        this.ws?.close();
        // 如果 ws.onclose 没有触发重连（比如 ws 已经是 null），手动触发
        if (!this.ws) {
          this.scheduleReconnect();
        }
        return;
      }
      
      // 主动发送 ping 给服务器
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.send({ type: 'ping' });
      }
    }, this.HEARTBEAT_INTERVAL);
  }

  /**
   * 停止心跳检测
   */
  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  private scheduleReconnect(): void {
    this.reconnectAttempts++;
    const delay = Math.min(3000 * Math.pow(2, this.reconnectAttempts), 30000);
    console.log(`Reconnecting... Attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts}, delay=${delay}ms`);
    setTimeout(() => this.connect(), delay);
  }

  disconnect(): void {
    this.isManualClose = true;
    this.stopHeartbeat();
    this.ws?.close();
    this.ws = null;
  }

  send(data: WSMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    } else {
      console.warn('WebSocket is not connected');
    }
  }

  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  onOpen(handler: ConnectionHandler): () => void {
    this.openHandlers.add(handler);
    return () => this.openHandlers.delete(handler);
  }

  onClose(handler: ConnectionHandler): () => void {
    this.closeHandlers.add(handler);
    return () => this.closeHandlers.delete(handler);
  }

  onError(handler: ConnectionHandler): () => void {
    this.errorHandlers.add(handler);
    return () => this.errorHandlers.delete(handler);
  }

  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  setToken(token: string): void {
    this.token = token;
    // 保存到localStorage
    localStorage.setItem('auth_token', token);
  }

  get isAuthenticated(): boolean {
    return this.authenticated;
  }

  setAuthenticated(authenticated: boolean): void {
    this.authenticated = authenticated;
  }
}

export const wsService = new WebSocketService();
export default wsService;
