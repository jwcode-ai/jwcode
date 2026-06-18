package com.jwcode.core.channel;

import java.time.Instant;

public class InboundChannelMessage {
    public String channelId;
    public String channelType;
    public String senderId;   // 渠道侧用户 ID
    public String senderName;
    public String text;
    public Instant receivedAt = Instant.now();
}
