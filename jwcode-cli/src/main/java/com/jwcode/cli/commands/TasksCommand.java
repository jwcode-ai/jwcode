package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TasksCommand - /tasks 命令
 * 
 * 功能说明：
 * 管理后台任务，支持列出、查看、停止任务。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/tasks", description = "管理后台任务")
public class TasksCommand implements Runnable {
    
    // 模拟任务存储
    private static final Map<String, TaskInfo> taskStore = new ConcurrentHashMap<>();
    
    @Parameters(index = "0", description = "子命令 (list, get, stop)", arity = "0..1")
    private String subCommand;
    
    @Parameters(index = "1", description = "任务 ID", arity = "0..1")
    private String taskId;
    
    @Option(names = {"-l", "--limit"}, description = "限制显示数量", defaultValue = "10")
    private int limit;
    
    @Option(names = {"-s", "--status"}, description = "按状态过滤")
    private String status;
    
    static {
        // 初始化一些示例任务
        initSampleTasks();
    }
    
    private static void initSampleTasks() {
        TaskInfo task1 = new TaskInfo();
        task1.id = "task_001";
        task1.name = "构建项目";
        task1.status = "completed";
        task1.createdAt = System.currentTimeMillis() - 3600000;
        taskStore.put("task_001", task1);
        
        TaskInfo task2 = new TaskInfo();
        task2.id = "task_002";
        task2.name = "运行测试";
        task2.status = "running";
        task2.createdAt = System.currentTimeMillis() - 1800000;
        taskStore.put("task_002", task2);
        
        TaskInfo task3 = new TaskInfo();
        task3.id = "task_003";
        task3.name = "部署应用";
        task3.status = "pending";
        task3.createdAt = System.currentTimeMillis() - 600000;
        taskStore.put("task_003", task3);
    }
    
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
        
        List<TaskInfo> tasks = new ArrayList<>(taskStore.values());
        
        // 按状态过滤
        if (status != null) {
            tasks.removeIf(t -> !status.equalsIgnoreCase(t.status));
        }
        
        // 限制数量
        if (limit > 0 && tasks.size() > limit) {
            tasks = tasks.subList(0, limit);
        }
        
        if (tasks.isEmpty()) {
            System.out.println("(无任务)");
            return;
        }
        
        for (TaskInfo task : tasks) {
            String statusIcon = getStatusIcon(task.status);
            System.out.printf("%s [%s] %s - %s%n", 
                task.id, statusIcon, task.name, formatTime(task.createdAt));
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
        
        TaskInfo task = taskStore.get(taskId);
        if (task == null) {
            System.out.println("任务不存在：" + taskId);
            return;
        }
        
        System.out.println("任务详情:");
        System.out.println("---------");
        System.out.println("ID: " + task.id);
        System.out.println("名称：" + task.name);
        System.out.println("描述：" + (task.description != null ? task.description : "无"));
        System.out.println("状态：" + task.status);
        System.out.println("优先级：" + (task.priority != null ? task.priority : "normal"));
        System.out.println("创建时间：" + formatTime(task.createdAt));
        if (task.completedAt != null) {
            System.out.println("完成时间：" + formatTime(task.completedAt));
        }
    }
    
    private void stopTask() {
        if (taskId == null) {
            System.out.println("错误：需要指定任务 ID");
            System.out.println("用法：/tasks stop <task_id>");
            return;
        }
        
        TaskInfo task = taskStore.get(taskId);
        if (task == null) {
            System.out.println("任务不存在：" + taskId);
            return;
        }
        
        if ("completed".equals(task.status) || "cancelled".equals(task.status)) {
            System.out.println("任务已经结束，无法停止：" + task.status);
            return;
        }
        
        task.status = "cancelled";
        task.completedAt = System.currentTimeMillis();
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
        System.out.println("  -s, --status <s>   按状态过滤 (pending, running, completed, cancelled)");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  /tasks list -l 5");
        System.out.println("  /tasks get task_001");
        System.out.println("  /tasks stop task_002");
    }
    
    private String getStatusIcon(String status) {
        switch (status) {
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
    
    private String formatTime(long timestamp) {
        long ago = System.currentTimeMillis() - timestamp;
        if (ago < 60000) {
            return ago / 1000 + "秒前";
        } else if (ago < 3600000) {
            return ago / 60000 + "分钟前";
        } else {
            return ago / 3600000 + "小时前";
        }
    }
    
    /**
     * 任务信息类
     */
    static class TaskInfo {
        public String id;
        public String name;
        public String description;
        public String status; // pending, running, completed, cancelled, failed
        public String priority;
        public Long createdAt;
        public Long completedAt;
    }
}