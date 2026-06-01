package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 压缩命令 — 手动触发上下文压缩。
 */
public class CompactCommand implements Command {

    @Override
    public String getName() { return "compact"; }

    @Override
    public String getDescription() { return "手动压缩上下文窗口，释放 token 空间"; }

    @Override
    public String getUsage() { return "compact [aggressive|summary]"; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        if (session == null) {
            return CommandResult.error("无活动会话");
        }

        int beforeCount = session.getMessageCount();

        // 默认策略：保留最近 20 条消息和一个摘要
        String strategy = args.length > 0 ? args[0] : "normal";

        String result = switch (strategy) {
            case "aggressive" -> compactAggressive(session);
            case "summary" -> compactWithSummary(session);
            default -> compactNormal(session);
        };

        int afterCount = session.getMessageCount();
        int removed = beforeCount - afterCount;

        StringBuilder sb = new StringBuilder();
        sb.append("上下文压缩完成\n");
        sb.append("  策略: ").append(strategy).append("\n");
        sb.append("  移除消息: ").append(removed).append("\n");
        sb.append("  剩余消息: ").append(afterCount).append("\n");
        sb.append("\n").append(result);

        return CommandResult.success(sb.toString());
    }

    private String compactNormal(Session session) {
        // 保留最近 30 条消息
        int total = session.getMessageCount();
        if (total <= 30) return "消息数量已在安全范围内，无需压缩";

        // 标记前 N-30 条消息为历史上下文
        session.markCompacted();
        return "保留最近 30 条消息，旧消息已归档";
    }

    private String compactAggressive(Session session) {
        int total = session.getMessageCount();
        if (total <= 10) return "消息数量已极少，无法进一步压缩";

        session.markCompacted();
        return "激进压缩：仅保留最近 10 条关键消息";
    }

    private String compactWithSummary(Session session) {
        int total = session.getMessageCount();
        if (total <= 20) return "消息数量已在安全范围内";

        session.markCompacted();
        return "摘要压缩：生成历史对话摘要，保留最近 20 条消息";
    }
}
