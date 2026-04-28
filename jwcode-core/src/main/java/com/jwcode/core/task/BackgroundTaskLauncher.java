package com.jwcode.core.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BackgroundTaskLauncher — OS 级后台任务启动器。
 *
 * <p>实现 Kimi Code 的 {@code Shell run_in_background=true} 模式：</p>
 * <ul>
 *   <li><b>OS 级隔离</b>：通过 {@link ProcessBuilder} 启动独立进程，避免阻塞 JVM 主线程</li>
 *   <li><b>实时输出泵</b>：启动独立线程将子进程 stdout/stderr 实时泵入 Task 输出缓冲区</li>
 *   <li><b>生命周期管理</b>：支持 TaskList 查看、TaskOutput 拉取、TaskStop 终止</li>
 *   <li><b>自动事件通知</b>：任务到达终止态时通过 {@link WireEventBus} 通知主控 Agent</li>
 * </ul>
 */
public class BackgroundTaskLauncher {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundTaskLauncher.class);

    private final Path workingDir;
    private final TaskStore taskStore;
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, Thread> outputPumpThreads = new ConcurrentHashMap<>();

    public BackgroundTaskLauncher(Path workingDir, TaskStore taskStore) {
        this.workingDir = workingDir;
        this.taskStore = taskStore;
    }

    // ==================== 核心启动接口 ====================

    /**
     * 启动 OS 级后台任务。
     *
     * @param command  要执行的命令（如 {@code "mvn clean package"}）
     * @param description 任务描述
     * @return 创建的任务对象
     */
    public Task launch(String command, String description) {
        String taskId = UUID.randomUUID().toString();
        Task task = new Task(description != null ? description : command, command);
        task.setId(taskId);
        task.markRunning();
        taskStore.create(task);

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            activeProcesses.put(taskId, process);

            // 启动输出泵线程
            Thread pumpThread = new Thread(() -> pumpOutput(process, task), "bg-pump-" + taskId.substring(0, 8));
            pumpThread.setDaemon(true);
            pumpThread.start();
            outputPumpThreads.put(taskId, pumpThread);

            // 注册进程退出监听器
            CompletableFuture.runAsync(() -> {
                try {
                    int exitCode = process.waitFor();
                    onProcessExit(task, exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    task.markFailed("等待进程退出被中断");
                    taskStore.update(task);
                }
            });

            logger.info("[BackgroundTaskLauncher] 启动后台任务 | taskId={} | command={}", taskId, command);
            return task;

        } catch (IOException e) {
            logger.error("[BackgroundTaskLauncher] 启动失败 | taskId={}", taskId, e);
            task.markFailed("启动失败: " + e.getMessage());
            taskStore.update(task);
            return task;
        }
    }

    /**
     * 终止后台任务。
     */
    public boolean stop(String taskId) {
        Process process = activeProcesses.get(taskId);
        if (process == null) {
            logger.warn("[BackgroundTaskLauncher] 找不到进程 | taskId={}", taskId);
            return false;
        }

        process.destroy();
        logger.info("[BackgroundTaskLauncher] 终止任务 | taskId={}", taskId);

        // 等待进程结束
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cleanup(taskId);
        return true;
    }

    /**
     * 拉取任务输出（非阻塞，返回当前已积累的输出）。
     */
    public String getOutput(String taskId) {
        Task task = taskStore.get(taskId);
        return task != null ? task.getOutputString() : null;
    }

    /**
     * 检查任务是否仍在运行。
     */
    public boolean isRunning(String taskId) {
        Process process = activeProcesses.get(taskId);
        return process != null && process.isAlive();
    }

    // ==================== 内部实现 ====================

    private void pumpOutput(Process process, Task task) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, read);
                task.appendOutput(chunk);
                taskStore.update(task);
            }
        } catch (IOException e) {
            logger.debug("[BackgroundTaskLauncher] 输出泵结束 | taskId={}", task.getId());
        }
    }

    private void onProcessExit(Task task, int exitCode) {
        String taskId = task.getId();

        if (exitCode == 0) {
            task.markCompleted();
            logger.info("[BackgroundTaskLauncher] 任务完成 | taskId={} | exitCode={}", taskId, exitCode);
        } else {
            task.markFailed("进程退出码: " + exitCode);
            logger.warn("[BackgroundTaskLauncher] 任务失败 | taskId={} | exitCode={}", taskId, exitCode);
        }

        taskStore.update(task);
        cleanup(taskId);

        // 发布 Wire 事件通知主控 Agent
        WireEventBus.getInstance().publish(new WireEventBus.TaskCompletedEvent(taskId, exitCode == 0, exitCode));
    }

    private void cleanup(String taskId) {
        activeProcesses.remove(taskId);
        Thread pump = outputPumpThreads.remove(taskId);
        if (pump != null) {
            pump.interrupt();
        }
    }
}
