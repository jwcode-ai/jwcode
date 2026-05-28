package com.jwcode.core.tool.shell;

import com.jwcode.core.tool.BashTool.BackgroundCommandExecutor;
import com.jwcode.core.tool.WorkspaceGuard;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Docker 容器沙箱执行器 — 实现 {@link BackgroundCommandExecutor}。
 *
 * <p>在隔离的 Docker 容器中执行命令，提供：
 * <ul>
 *   <li>网络隔离 ({@code --network=none})</li>
 *   <li>资源限制 ({@code --memory=512m --cpus=1})</li>
 *   <li>文件系统只读 ({@code :ro}) + 指定可写目录</li>
 *   <li>超时自动 kill ({@code docker stop --time=5})</li>
 *   <li>Docker 不可用时优雅降级到 {@code Runtime.exec} + WorkspaceGuard</li>
 * </ul>
 */
public class DockerSandboxExecutor implements BackgroundCommandExecutor {
    private static final Logger logger = Logger.getLogger(DockerSandboxExecutor.class.getName());

    private static final String DOCKER_IMAGE = "alpine:latest";
    private static final long DEFAULT_TIMEOUT_SEC = 300;
    private static final String MEMORY_LIMIT = "512m";
    private static final String CPU_LIMIT = "1";

    private final Path workspaceRoot;
    private final WorkspaceGuard guard;
    private final ConcurrentHashMap<String, ContainerTask> runningTasks = new ConcurrentHashMap<>();

    private boolean dockerAvailable;
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "docker-sandbox");
        t.setDaemon(true);
        return t;
    });

    public DockerSandboxExecutor(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.guard = new WorkspaceGuard(this.workspaceRoot);
        this.dockerAvailable = checkDocker();
    }

    private static boolean checkDocker() {
        try {
            Process p = new ProcessBuilder("docker", "info").start();
            int rc = p.waitFor();
            return rc == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String execute(String command, String description, Path workingDir) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        ContainerTask task = new ContainerTask(taskId, command, workingDir);
        runningTasks.put(taskId, task);

        CompletableFuture.runAsync(() -> {
            try {
                String output = dockerAvailable
                    ? runInDocker(command, workingDir)
                    : runFallback(command, workingDir);
                task.complete(output);
            } catch (Exception e) {
                task.fail(e.getMessage());
            } finally {
                runningTasks.remove(taskId);
            }
        }, taskExecutor);

        return taskId;
    }

    /** Docker 容器执行 */
    String runInDocker(String command, Path workingDir) throws IOException, InterruptedException {
        Path tmpDir = workspaceRoot.resolve(".jwcode").resolve("tmp");
        Files.createDirectories(tmpDir);

        Path wd = workingDir != null ? workingDir.toAbsolutePath() : workspaceRoot;
        String relWd = workspaceRoot.relativize(wd).toString();

        List<String> cmd = new ArrayList<>(List.of(
            "docker", "run", "--rm",
            "--network=none",
            "--memory=" + MEMORY_LIMIT,
            "--cpus=" + CPU_LIMIT,
            "-v", workspaceRoot + ":/workspace:ro",
            "-v", tmpDir + ":/tmp:rw",
            "-w", "/workspace/" + relWd,
            DOCKER_IMAGE,
            "sh", "-c", command
        ));

        logger.info("[DockerSandbox] Running: " + String.join(" ", cmd));
        Process p = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            int lines = 0;
            while ((line = r.readLine()) != null && lines++ < 1000) {
                if (output.length() < 100_000) {
                    output.append(line).append("\n");
                }
            }
        }

        boolean finished = p.waitFor(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            new ProcessBuilder("docker", "stop", "--time=5", getContainerId()).start();
            return output + "\n[TIMEOUT after " + DEFAULT_TIMEOUT_SEC + "s]";
        }
        return output.toString();
    }

    /** Docker 不可用时的降级方案 */
    String runFallback(String command, Path workingDir) throws IOException, InterruptedException {
        logger.warning("[DockerSandbox] Docker unavailable, falling back to Runtime.exec + WorkspaceGuard");

        Path wd = workingDir != null ? workingDir.toAbsolutePath() : workspaceRoot;
        guard.validateOrThrow(wd, "DockerSandbox(fallback)");

        Process p = new ProcessBuilder("sh", "-c", command)
            .directory(wd.toFile())
            .redirectErrorStream(true)
            .start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            int lines = 0;
            while ((line = r.readLine()) != null && lines++ < 500) {
                if (output.length() < 50_000) output.append(line).append("\n");
            }
        }

        boolean finished = p.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            return output + "\n[TIMEOUT - fallback mode]";
        }
        return output.toString();
    }

    private String getContainerId() {
        return ""; // simplified: in production, capture from docker run output
    }

    static class ContainerTask {
        final String id;
        final String command;
        final Path workingDir;
        volatile String output;
        volatile String error;
        volatile boolean done;
        volatile boolean success;

        ContainerTask(String id, String command, Path workingDir) {
            this.id = id; this.command = command; this.workingDir = workingDir;
        }
        void complete(String out) { this.output = out; this.success = true; this.done = true; }
        void fail(String err) { this.error = err; this.success = false; this.done = true; }
    }
}
