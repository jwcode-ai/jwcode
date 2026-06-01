package com.jwcode.core.api;

/**
 * 消息发送器 — PlanTaskBroadcaster 和 TodoWriteBroadcaster 的回退传输通道。
 */
@FunctionalInterface
public interface MessageSender {
    void send(String type, String sessionId, String data);
}
