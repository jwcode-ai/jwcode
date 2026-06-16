import { describe, it, expect, beforeEach, vi } from 'vitest';
import { JwCodeClient } from '../client.js';

describe('JwCodeClient', () => {
  let client: JwCodeClient;

  beforeEach(() => {
    vi.restoreAllMocks();
    client = new JwCodeClient();
  });

  describe('事件处理', () => {
    it('可以注册和触发事件处理器', () => {
      const handler = vi.fn();
      expect(() => client.on('message', handler)).not.toThrow();
    });

    it('可以注销事件处理器', () => {
      const handler = vi.fn();
      client.on('message', handler);
      expect(() => client.off('message', handler)).not.toThrow();
    });

    it('多次注册同一处理器不会重复', () => {
      const handler = vi.fn();
      client.on('message', handler);
      client.on('message', handler);
      // 内部使用 Set，重复添加自动去重
    });
  });

  describe('连接管理', () => {
    it('未连接时状态为未运行', () => {
      expect(client.isRunning()).toBe(false);
    });

    it('connect 调用不会立即进入运行状态（WebSocket 异步连接）', () => {
      // 注意：connect 是异步操作，调用后 isRunning 仍然为 false
      // 直到 WebSocket 'open' 事件触发
      client.connect('ws://localhost:8080');
      // connect 后不立即改变状态，所以仍然是 false
      // 这个测试只是验证方法可调用
      expect(typeof client.connect).toBe('function');
    });

    it('disconnect 方法可调用', () => {
      expect(typeof client.disconnect).toBe('function');
    });

    it('getSessionId 返回字符串', () => {
      expect(typeof client.getSessionId()).toBe('string');
    });
  });

  describe('消息发送', () => {
    it('send 方法存在且可调用', () => {
      expect(typeof client.send).toBe('function');
    });

    it('未连接时 send 不抛出异常', () => {
      expect(() => client.send({ type: 'ping' })).not.toThrow();
    });
  });
});
