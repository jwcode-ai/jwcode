package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 任务命令 — 查看和管理后台任务。
 */
public class TasksCommand implements Command {

    @Override
    public String getName() { return "tasks"; }

    @Override
    public java.util.List<String> getAliases() {
        return java.util.List.of("jobs");
    }

    @Override
    public String getDescription() { return "查看和管理后台任务、子代理和 shell 进程"; }

    @Override
    public String getUsage() { return "tasks [list|stop|output] [taskId]"; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String action = args.length > 0 ? args[0] : "list";

        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                      任务管理器                             ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        switch (action) {
            case "list" -> {
                sb.append("【活动任务】\n\n");
                if (session != null && session.getActiveTask() != null) {
                    var task = session.getActiveTask();
                    sb.append("  当前任务: ").append(task.getDescription() != null ? task.getDescription() : "无描述").append("\n");
                    sb.append("  状态: ").append(task.getStatus()).append("\n");
                    sb.append("  ID: ").append(task.getTaskId()).append("\n");
                } else {
                    sb.append("  (无活动任务)\n");
                }
                sb.append("\n任务类型说明:\n");
                sb.append("  • 后台任务    — shell 命令长时间执行 (mvn build, npm install)\n");
                sb.append("  • 子代理      — AI 子代理并行处理任务\n");
                sb.append("  • 压缩任务    — 上下文压缩\n");
                sb.append("  • 索引任务    — 代码库索引构建\n");
            }
            case "stop" -> {
                if (args.length < 2) {
                    return CommandResult.error("用法: /tasks stop <任务ID>");
                }
                sb.append("已请求停止任务: ").append(args[1]);
            }
            case "output" -> {
                if (args.length < 2) {
                    return CommandResult.error("用法: /tasks output <任务ID>");
                }
                sb.append("任务 ").append(args[1]).append(" 的输出:\n");
                sb.append("(获取中...)");
            }
            default -> {
                return CommandResult.error("未知操作: " + action + "。支持: list, stop, output");
            }
        }

        return CommandResult.success(sb.toString());
    }
}
