package com.jwcode.core.message;

import java.util.List;
import java.util.UUID;

/**
 * TombstoneMessage — 标记会话中已被清理的孤立消息。
 *
 * 当模型切换、fallback 或上下文压缩导致某些 assistant 消息被移除时，
 * 前端不应继续显示这些消息的内容。Tombstone 通知前端清除对应消息。
 *
 * 协议格式：
 *   { type: "tombstone", data: { messageIds: [...], reason: "..." } }
 */
public class TombstoneMessage {

    private final String id;
    private final List<String> messageIds;
    private final String reason;
    private final long timestamp;

    public TombstoneMessage(List<String> messageIds, String reason) {
        this.id = UUID.randomUUID().toString();
        this.messageIds = List.copyOf(messageIds);
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public List<String> getMessageIds() { return messageIds; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }

    /**
     * 序列化为 JSON 字符串（用于 WebSocket 协议）。
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"messageIds\":[");
        for (int i = 0; i < messageIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(messageIds.get(i))).append("\"");
        }
        sb.append("],");
        sb.append("\"reason\":\"").append(escapeJson(reason)).append("\"");
        sb.append(",\"ts\":").append(timestamp);
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
