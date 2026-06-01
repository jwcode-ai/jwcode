package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 费用命令 — 显示 token 使用量和费用统计。
 */
public class CostCommand implements Command {

    @Override
    public String getName() { return "cost"; }

    @Override
    public String getDescription() { return "显示 token 使用量和 API 费用统计"; }

    @Override
    public String getUsage() { return "cost [detail|reset]"; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                     Token 用量 & 费用统计                    ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        if (session != null) {
            sb.append("【本次会话】\n");
            sb.append("  消息数:      ").append(session.getMessageCount()).append("\n");

            // 从 metadata 读取 token 统计
            long promptTokens = session.getMetadata("total_prompt_tokens") instanceof Number n ? n.longValue() : 0;
            long completionTokens = session.getMetadata("total_completion_tokens") instanceof Number n ? n.longValue() : 0;
            long totalTokens = session.getMetadata("total_tokens") instanceof Number n ? n.longValue() : 0;
            double estimatedCost = session.getMetadata("estimated_cost") instanceof Number n ? n.doubleValue() : 0;

            sb.append("  Prompt Tokens:     ").append(formatTokens(promptTokens)).append("\n");
            sb.append("  Completion Tokens: ").append(formatTokens(completionTokens)).append("\n");
            sb.append("  总计 Tokens:       ").append(formatTokens(totalTokens)).append("\n");

            String model = session.getModel();
            if (model != null) {
                sb.append("  模型:              ").append(model).append("\n");

                // 按模型估算费用
                if (estimatedCost <= 0 && totalTokens > 0) {
                    estimatedCost = estimateCost(model, promptTokens, completionTokens);
                }
            }

            if (estimatedCost > 0) {
                sb.append("  估算费用:          $").append(String.format("%.4f", estimatedCost)).append("\n");
            }

            // 压缩统计
            int compactCount = session.getCompactCount();
            if (compactCount > 0) {
                sb.append("  上下文压缩次数:    ").append(compactCount).append("\n");
            }
        } else {
            sb.append("(无活动会话)\n");
        }

        sb.append("\n【费用参考 — 常见模型价格 (每 1M tokens)】\n");
        sb.append("  Claude Opus 4.7:   $15.00 input / $75.00 output\n");
        sb.append("  Claude Sonnet 4.6: $3.00 input / $15.00 output\n");
        sb.append("  Claude Haiku 4.5:  $0.80 input / $4.00 output\n");
        sb.append("  GPT-4o:            $2.50 input / $10.00 output\n");
        sb.append("  GPT-4o-mini:       $0.15 input / $0.60 output\n");
        sb.append("  DeepSeek V3:       $0.27 input / $1.10 output\n");

        return CommandResult.success(sb.toString());
    }

    private String formatTokens(long tokens) {
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1_000) return String.format("%.1fK", tokens / 1_000.0);
        return String.valueOf(tokens);
    }

    private double estimateCost(String model, long promptTokens, long completionTokens) {
        double promptRate, completionRate;
        String lower = model.toLowerCase();

        if (lower.contains("opus")) {
            promptRate = 15.0; completionRate = 75.0;
        } else if (lower.contains("sonnet")) {
            promptRate = 3.0; completionRate = 15.0;
        } else if (lower.contains("haiku")) {
            promptRate = 0.8; completionRate = 4.0;
        } else if (lower.contains("gpt-4o-mini")) {
            promptRate = 0.15; completionRate = 0.60;
        } else if (lower.contains("gpt-4o")) {
            promptRate = 2.5; completionRate = 10.0;
        } else if (lower.contains("deepseek")) {
            promptRate = 0.27; completionRate = 1.10;
        } else {
            promptRate = 1.0; completionRate = 5.0;
        }

        return (promptTokens / 1_000_000.0) * promptRate
             + (completionTokens / 1_000_000.0) * completionRate;
    }
}
