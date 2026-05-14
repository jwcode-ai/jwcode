package com.jwcode.core.hook.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hook.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ShellHookExecutor — 基于外部 Shell 脚本的 Hook 执行器。
 *
 * <p>通过 {@link ProcessBuilder} 执行外部脚本：</p>
 * <ul>
 *   <li>stdin：传入 JSON 序列化的 {@link HookContext}</li>
 *   <li>stdout：读取 JSON 决策（{@link HookResult} 结构）</li>
 *   <li>stderr：记录到日志</li>
 *   <li>退出码：非0视为执行异常</li>
 * </ul>
 *
 * <h3>脚本契约</h3>
 * <p>脚本应以以下格式在 stdout 返回 JSON：</p>
 * <pre>{@code
 * {
 *   "decision": "ALLOW|DENY|ASK|MODIFY|DEFER|VOID",
 *   "reason": "决策原因",
 *   "modifiedInput": { ... },  // 仅 MODIFY 时需要
 *   "askPayload": "...",       // 仅 ASK 时需要
 *   "deferToken": "..."        // 仅 DEFER 时需要
 * }
 * }</pre>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class ShellHookExecutor implements HookExecutor {

    private static final Logger logger = Logger.getLogger(ShellHookExecutor.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String command;
    private final HookPriority priority;
    private final long timeoutMs;
    private final boolean failOpen;
    private final boolean enabled;

    /**
     * @param name    执行器名称
     * @param command Shell 命令（如 "python3 .jwcode/hooks/audit.py"）
     */
    public ShellHookExecutor(String name, String command) {
        this(name, command, HookPriority.USER, 30_000, true, true);
    }

    public ShellHookExecutor(String name, String command,
                              HookPriority priority, long timeoutMs,
                              boolean failOpen, boolean enabled) {
        this.name = name;
        this.command = command;
        this.priority = priority;
        this.timeoutMs = timeoutMs;
        this.failOpen = failOpen;
        this.enabled = enabled;
    }

    /**
     * 从配置创建。
     */
    public static ShellHookExecutor fromConfig(HookConfig config) {
        return new ShellHookExecutor(
            config.getName(),
            config.getCommand(),
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    @Override
    public CompletableFuture<HookResult> execute(HookContext context) {
        return CompletableFuture.supplyAsync(() -> {
            Process process = null;
            try {
                // 1. 构建进程
                ProcessBuilder pb = buildProcess(command);
                process = pb.start();

                // 2. 写入 stdin
                try (OutputStream stdin = process.getOutputStream();
                     BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(stdin, StandardCharsets.UTF_8))) {
                    String json = MAPPER.writeValueAsString(context.toJson());
                    writer.write(json);
                    writer.flush();
                }

                // 3. 等待进程完成
                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    logger.warning("[ShellHook] " + name + " timed out after " + timeoutMs + "ms");
                    return failOpen
                        ? HookResult.timeout(name)
                        : HookResult.deny(name, "Hook timed out (fail-closed)");
                }

                // 4. 读取 stdout
                String stdout = readStream(process.getInputStream());
                String stderr = readStream(process.getErrorStream());

                if (!stderr.isEmpty()) {
                    logger.fine("[ShellHook] " + name + " stderr: " + stderr);
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    logger.warning("[ShellHook] " + name + " exited with code " + exitCode
                        + ": " + stderr);
                    return failOpen
                        ? HookResult.error(name, "Script exit code " + exitCode)
                        : HookResult.errorFailClosed(name, "Script exit code " + exitCode);
                }

                // 5. 解析 stdout 为 HookResult
                if (stdout == null || stdout.trim().isEmpty()) {
                    logger.warning("[ShellHook] " + name + " returned empty stdout, defaulting to ALLOW");
                    return HookResult.allow(name, "Empty stdout from script");
                }

                return parseResult(stdout.trim());

            } catch (Exception e) {
                logger.log(Level.WARNING, "[ShellHook] " + name + " execution error", e);
                return failOpen
                    ? HookResult.error(name, e.getMessage())
                    : HookResult.errorFailClosed(name, e.getMessage());
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });
    }

    /**
     * 解析 stdout JSON 为 HookResult。
     */
    private HookResult parseResult(String stdout) {
        try {
            JsonNode root = MAPPER.readTree(stdout);

            String decisionStr = root.has("decision")
                ? root.get("decision").asText().toUpperCase()
                : "ALLOW";

            HookDecision decision;
            try {
                decision = HookDecision.valueOf(decisionStr);
            } catch (IllegalArgumentException e) {
                logger.warning("[ShellHook] " + name + " unknown decision: " + decisionStr);
                decision = HookDecision.ALLOW;
            }

            String reason = root.has("reason") ? root.get("reason").asText() : "";

            HookResult.Builder builder = new HookResult.Builder(decision, name).reason(reason);

            if (decision == HookDecision.MODIFY && root.has("modifiedInput")) {
                builder.modifiedInput(root.get("modifiedInput"));
            }
            if (decision == HookDecision.ASK && root.has("askPayload")) {
                builder.askPayload(root.get("askPayload").asText());
            }
            if (decision == HookDecision.DEFER && root.has("deferToken")) {
                builder.deferToken(root.get("deferToken").asText());
            }

            return builder.build();
        } catch (Exception e) {
            logger.warning("[ShellHook] " + name + " failed to parse stdout: " + stdout);
            return HookResult.allow(name, "Failed to parse script output, defaulting to ALLOW");
        }
    }

    /**
     * 构建进程（根据平台适配）。
     */
    private ProcessBuilder buildProcess(String cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new ProcessBuilder("cmd.exe", "/c", cmd);
        } else {
            return new ProcessBuilder("sh", "-c", cmd);
        }
    }

    /**
     * 读取流内容。
     */
    private String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @Override
    public HookImplementationType getType() { return HookImplementationType.SHELL; }

    @Override
    public String getName() { return name; }

    @Override
    public HookPriority getPriority() { return priority; }

    @Override
    public long getTimeoutMs() { return timeoutMs; }

    @Override
    public boolean isFailOpen() { return failOpen; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() {
        return String.format("ShellHookExecutor{name='%s', cmd='%s', priority=%s}",
            name, command, priority);
    }
}
