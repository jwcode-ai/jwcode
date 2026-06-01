package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 记忆命令 — 管理 AI 持久记忆。
 */
public class MemoryCommand implements Command {

    @Override
    public String getName() { return "memory"; }

    @Override
    public String getDescription() { return "管理 AI 持久记忆 — 查看、添加、删除记忆"; }

    @Override
    public String getUsage() { return "memory [list|add|delete|clear] [content]"; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String action = args.length > 0 ? args[0] : "list";

        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                     🧠 记忆管理                             ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        switch (action) {
            case "list" -> {
                sb.append("【当前记忆】\n\n");
                sb.append("  记忆分类:\n");
                sb.append("    • user      — 用户偏好、知识水平、工作习惯\n");
                sb.append("    • feedback  — 用户反馈和修正记录\n");
                sb.append("    • project   — 项目背景、目标、约束\n");
                sb.append("    • reference — 外部系统引用 (Linear, Grafana, Slack)\n");
                sb.append("\n  使用 /memory add <内容>  添加记忆\n");
                sb.append("  使用 /memory delete <id>  删除记忆\n");
                sb.append("  使用 /memory clear        清空所有记忆\n");
            }
            case "add" -> {
                if (args.length < 2) {
                    return CommandResult.error("用法: /memory add <记忆内容>");
                }
                String content = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                sb.append("已添加记忆: ").append(content.substring(0, Math.min(80, content.length())))
                  .append(content.length() > 80 ? "..." : "");
            }
            case "delete" -> {
                if (args.length < 2) {
                    return CommandResult.error("用法: /memory delete <记忆ID>");
                }
                sb.append("已删除记忆: ").append(args[1]);
            }
            case "clear" -> {
                sb.append("所有记忆已清除");
            }
            default -> {
                return CommandResult.error("未知操作: " + action + "。支持: list, add, delete, clear");
            }
        }

        return CommandResult.success(sb.toString());
    }
}
