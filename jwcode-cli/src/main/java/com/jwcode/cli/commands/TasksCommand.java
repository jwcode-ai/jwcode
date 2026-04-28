package com.jwcode.cli.commands;

import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStatus;
import com.jwcode.core.task.TaskStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * TasksCommand - /tasks 命令
 *
 * 管理后台任务，支持列出、查看、停止任务。
 * 已连接到真实 TaskStore，非 mock 数据。
 *
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/tasks", description = "管理后台任务")
public class TasksCommand implements Runnable {

    @Parameters(index = "0", description = "子命令 (list, get, stop)", arity = "0..1")
    private String subCommand;

    @Parameters(index = "1", description = "任务 ID", arity = "0..1")
    private String taskId;

    @Option(names = {"-l", "--limit"}, description = "限制显示数量", defaultValue = "10")
    private int limit;

    @Option(names = {"-s", "--status"}, description = "按状态过滤")
    private String status;

    @Override
    public void run() {
        if (subCommand == null || "list".equals(subCommand)) {
            listTasks();
        } else if ("get".equals(subCommand)) {
            getTask();
        } else if ("stop".equals(subCommand)) {
            stopTask();
        } else if ("help".equals(subCommand)) {
            showHelp();
        } else {
            System.out.println("未知命令：" + subCommand);
            showHelp();
        }
    }

    private void listTasks() {
        System.out.println("后台任务列表:");
        System.out.println("------------");

        List<Task> tasks = new ArrayList<>(TaskStore.getInstance().list());

        // 按状态过滤
        if (status != null) {
            tasks.removeIf(t -> !status.equalsIgnoreCase(t.getStatus().name()));
        }

        // 限制数量
        if (limit > 0 && tasks.size() > limit) {
            tasks = tasks.subList(0, limit);
        }

        if (tasks.isEmpty()) {
            System.out.println("(无任务)");
            return;
        }

        for (Task task : tasks) {
            String statusIcon = getStatusIcon(task.getStatus().name());
            System.out.printf("%s [%s] %s - %s%n",
                task.getId(), statusIcon, task.getTitle(), formatTime(task.getCreatedAt()));
        }

        System.out.println();
        System.out.println("使用 /tasks get <id> 查看任务详情");
        System.out.println("使用 /tasks stop <id> 停止任务");
    }

    private void getTask() {
        if (taskId == null) {
            System.out.println("错误：需要指定任务 ID");
            System.out.println("用法：/tasks get <task_id>");
            return;
        }

        Task task = TaskStore.getInstance().get(taskId);
        if (task == null) {
            System.out.println("任务不存在：" + taskId);
            return;
        }

        System.out.println("任务详情:");
        System.out.println("---------");
        System.out.println("ID: " + task.getId());
        System.out.println("名称：" + task.getTitle());
        System.out.println("描述：" + (task.getDescription() != null ? task.getDescription() : "无"));
        System.out.println("状态：" + task.getStatus());
        System.out.println("优先级：" + task.getPriority());
        System.out.println("创建时间：" + formatTime(task.getCreatedAt()));
        if (task.getCompletedAt() != null) {
            System.out.println("完成时间：" + formatTime(task.getCompletedAt()));
        }
        if (task.getOutputString() != null && !task.getOutputString().isEmpty()) {
            System.out.println("输出：");
            System.out.println(task.getOutputString());
        }
    }

    private void stopTask() {
        if (taskId == null) {
            System.out.println("错误：需要指定任务 ID");
            System.out.println("用法：/tasks stop <task_id>");
            return;
        }

        Task task = TaskStore.getInstance().get(taskId);
        if (task == null) {
            System.out.println("任务不存在：" + taskId);
            return;
        }

        if (task.getStatus().isFinished()) {
            System.out.println("任务已经结束，无法停止：" + task.getStatus());
            return;
        }

        task.updateStatus(TaskStatus.CANCELLED);
        TaskStore.getInstance().update(task);
        System.out.println("任务已停止：" + taskId);
    }

    private void showHelp() {
        System.out.println("任务管理命令");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /tasks list              - 列出所有任务");
        System.out.println("  /tasks get <id>          - 查看任务详情");
        System.out.println("  /tasks stop <id>         - 停止任务");
        System.out.println("  /tasks help              - 显示帮助");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  -l, --limit <n>    限制显示数量，默认：10");
        System.out.println("  -s, --status <s>   按状态过滤 (PENDING, RUNNING, COMPLETED, CANCELLED, FAILED)");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  /tasks list -l 5");
        System.out.println("  /tasks get task_001");
        System.out.println("  /tasks stop task_002");
    }

    private String getStatusIcon(String status) {
        switch (status.toLowerCase()) {
            case "pending":
                return "⏳";
            case "running":
                return "🔄";
            case "completed":
                return "✅";
            case "cancelled":
                return "❌";
            case "failed":
                return "⚠️";
            default:
                return " ";
        }
    }

    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) return "未知";
        long timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long ago = System.currentTimeMillis() - timestamp;
        if (ago < 60000) {
            return ago / 1000 + "秒前";
        } else if (ago < 3600000) {
            return ago / 60000 + "分钟前";
        } else {
            return ago / 3600000 + "小时前";
        }
    }
}
