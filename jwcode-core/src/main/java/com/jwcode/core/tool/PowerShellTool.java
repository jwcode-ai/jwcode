package com.jwcode.core.tool;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * PowerShell 工具 — 执行 Windows PowerShell 命令。
 *
 * 与 BashTool 类似，但固定使用 powershell.exe -NoProfile -Command 执行。
 * Linux/macOS 上会检测是否安装了 pwsh (PowerShell Core)，未安装则报错。
 */
public class PowerShellTool implements Tool<PowerShellTool.Input, PowerShellTool.Output, PowerShellTool.Progress> {

    private static final Logger logger = Logger.getLogger(PowerShellTool.class.getName());

    private static final int DEFAULT_TIMEOUT_MS = 120_000; // 默认 2 分钟
    private static final int MAX_OUTPUT_CHARS = 100_000;
    private static final int MAX_EXECUTION_TIMEOUT_MS = 600_000; // 硬上限 10 分钟

    @Override
    public String getName() { return "PowerShell"; }

    @Override
    public String getDescription() { return "执行 Windows PowerShell 命令。在 Linux/macOS 上使用 pwsh (PowerShell Core)。"; }

    @Override
    public String getPrompt() {
        return """
               使用 PowerShellTool 执行 PowerShell 命令。

               参数:
               - command: 要执行的 PowerShell 命令（必需）
               - description: 命令描述（可选）
               - timeout: 超时时间（毫秒，可选，默认 120000）

               示例:
               - {"command": "Get-ChildItem -Recurse -Filter *.java"}
               - {"command": "Select-String -Pattern 'TODO' -Recurse *.java"}
               - {"command": "Write-Host \\"Hello World\\""}
               """;
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return new ObjectMapper().readTree("""
                {
                    "type": "object",
                    "properties": {
                        "command": {"type": "string", "description": "要执行的 PowerShell 命令"}
                    },
                    "required": ["command"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TypeReference<Input> getInputType() { return new TypeReference<Input>() {}; }

    @Override
    public TypeReference<Output> getOutputType() { return new TypeReference<Output>() {}; }

    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error("输入验证失败: " + validation.getFormattedErrors());
                }

                // 检查权限
                if (!context.hasPermission("execute", input.command)) {
                    return ToolResult.error("没有权限执行 PowerShell 命令: " + input.command);
                }

                return executeCommand(input);
            } catch (Exception e) {
                logger.severe("PowerShell 执行失败: " + e.getMessage());
                return ToolResult.error("PowerShell 执行失败: " + e.getMessage());
            }
        });
    }

    @Override
    public ToolValidationResult validate(Input input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        if (input.command == null || input.command.trim().isEmpty()) {
            builder.addError("command 是必需的");
        }
        return builder.build();
    }

    @Override
    public boolean isReadOnly(Input input) {
        // 启发式判断：不含写操作的命令视为只读
        if (input.command == null) return false;
        String lower = input.command.toLowerCase().trim();
        if (lower.startsWith("get-") || lower.startsWith("select-") || lower.startsWith("test-")
            || lower.startsWith("write-output") || lower.startsWith("write-host")
            || lower.contains("| select-") || lower.contains("| format-")
            || lower.contains("| where-") || lower.contains("| sort ")) {
            return true;
        }
        return false;
    }

    // ── 命令执行 ──────────────────────────────────────────────

    private ToolResult<Output> executeCommand(Input input) {
        long startTime = System.currentTimeMillis();

        // 检测可用的 PowerShell 命令
        String psCmd = detectPowerShellCommand();
        if (psCmd == null) {
            return ToolResult.error("未找到 PowerShell 可执行文件。"
                + " Windows 上需要 powershell.exe，Linux/macOS 上需要 pwsh (PowerShell Core)。");
        }

        Process process = null;
        try {
            List<String> commandParts = Arrays.asList(psCmd, "-NoProfile", "-Command", input.command);
            ProcessBuilder pb = new ProcessBuilder(commandParts);
            pb.redirectErrorStream(true);
            process = pb.start();

            // 使用独立线程读取输出（避免 reader.readLine 在主线程阻塞导致超时失效）
            // 用 final 变量捕获 process 引用供 lambda 使用
            final Process proc = process;
            StringBuilder outputBuilder = new StringBuilder();
            CompletableFuture<Void> outputFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (outputBuilder) {
                            outputBuilder.append(line).append("\n");
                            if (outputBuilder.length() > MAX_OUTPUT_CHARS) {
                                outputBuilder.setLength(MAX_OUTPUT_CHARS);
                                outputBuilder.append("\n...[输出被截断]");
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.fine("[PowerShell] 输出读取线程退出: " + e.getMessage());
                }
            });

            int timeout = input.timeout != null && input.timeout > 0
                ? Math.min(input.timeout, MAX_EXECUTION_TIMEOUT_MS)
                : DEFAULT_TIMEOUT_MS;
            boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            long executionTime = System.currentTimeMillis() - startTime;

            // 等待输出读取完成
            try {
                outputFuture.get(2000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                outputFuture.cancel(true);
            }

            if (!completed) {
                process.destroyForcibly();
                process.waitFor(3000, TimeUnit.MILLISECONDS);
                String partialOutput;
                synchronized (outputBuilder) {
                    partialOutput = outputBuilder.toString();
                }
                Output output = new Output();
                output.success = false;
                output.output = partialOutput;
                output.error = "命令执行超时 (" + timeout + "ms)";
                output.exitCode = -1;
                return ToolResult.success(output);
            }

            String stdout;
            synchronized (outputBuilder) {
                stdout = outputBuilder.toString();
            }

            Output output = new Output();
            output.success = true;
            output.output = stdout;
            output.exitCode = 0;
            return ToolResult.success(output);

        } catch (Exception e) {
            logger.severe("PowerShell 执行异常: " + e.getMessage());
            return ToolResult.error("PowerShell 执行异常: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 检测系统中可用的 PowerShell 命令。
     * Windows 上优先使用 powershell.exe，Linux/macOS 上尝试 pwsh。
     */
    private static String detectPowerShellCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows：检查 powershell.exe 是否存在
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "$PSVersionTable.PSVersion");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean finished = p.waitFor(5, TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    return "powershell.exe";
                }
            } catch (Exception e) {
                logger.fine("powershell.exe 检测失败: " + e.getMessage());
            }
            // 尝试 pwsh (PowerShell Core)
            try {
                ProcessBuilder pb = new ProcessBuilder("pwsh", "-NoProfile", "-Command", "$PSVersionTable.PSVersion");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean finished = p.waitFor(5, TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    return "pwsh";
                }
            } catch (Exception e) {
                logger.fine("pwsh 检测失败: " + e.getMessage());
            }
        } else {
            // Linux/macOS：检查 pwsh
            try {
                ProcessBuilder pb = new ProcessBuilder("pwsh", "-NoProfile", "-Command", "$PSVersionTable.PSVersion");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean finished = p.waitFor(5, TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    return "pwsh";
                }
            } catch (Exception e) {
                logger.fine("pwsh 检测失败: " + e.getMessage());
            }
        }
        return null;
    }

    // ── 数据类 ──────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Input {
        public String command;
        public String description;
        public Integer timeout;

        public Input() {}
        public Input(String command) { this.command = command; }
    }

    public static class Output {
        public boolean success;
        public String output;
        public String error;
        public int exitCode;
    }

    public static class Progress {}
}
