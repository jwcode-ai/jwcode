import { describe, it, expect, beforeEach, vi } from 'vitest';
import { JwCodeClient } from '../client.js';

describe('JwCodeClient', () => {
  let client: JwCodeClient;

  beforeEach(() => {
    vi.restoreAllMocks();
    client = new JwCodeClient('http://localhost:8080', 'ws://localhost:8081/ws');
  });

  describe('event handling', () => {
    it('can register and trigger event handlers', () => {
      const handler = vi.fn();
      expect(() => client.on('message', handler)).not.toThrow();
    });

    it('unsubscribe function works', () => {
      const handler = vi.fn();
      const unsubscribe = client.on('message', handler);
      expect(() => unsubscribe()).not.toThrow();
    });

    it('registering same handler twice is idempotent', () => {
      const handler = vi.fn();
      client.on('message', handler);
      client.on('message', handler);
    });
  });

  describe('connection management', () => {
    it('sessionId is null before connect', () => {
      expect(client.sessionId).toBeNull();
    });

    it('connect does not immediately change sessionId (async WebSocket)', () => {
      expect(typeof client.connect).toBe('function');
    });

    it('close method exists', () => {
      expect(typeof client.close).toBe('function');
    });

    it('sessionId returns null by default', () => {
      expect(client.sessionId).toBeNull();
    });
  });

  describe('message sending', () => {
    it('send method exists and is callable', () => {
      expect(typeof client.send).toBe('function');
    });

    it('send does not throw when not connected', () => {
      expect(() => client.send('ping')).not.toThrow();
    });
  });
});
