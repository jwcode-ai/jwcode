package com.jwcode.core.channel;

/**
 * 统一渠道适配器接口。每种渠道（微信、飞书、钉钉）各实现一次。
 */
public interface ChannelAdapter {
    String getChannelType();
    void initialize(ChannelConfig config);
    void shutdown();
    boolean isConnected();
    /** 发送文本给指定用户（超长自动分段） */
    void send(String recipientId, String text);
    /** 非阻塞取一条入站消息，无消息返回 null */
    InboundChannelMessage poll();
    ChannelConfig getConfig();
}
