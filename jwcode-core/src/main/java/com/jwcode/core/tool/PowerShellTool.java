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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class PowerShellTool implements Tool<PowerShellTool.Input, PowerShellTool.Output, PowerShellTool.Progress> {
    private static final Logger logger = Logger.getLogger(PowerShellTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_FILE_LIST_RESULTS = 100;
    private static final int PROCESS_TIMEOUT_SECONDS = 120;  // PowerShell 进程超时时间（2分钟）
    private static final int MAX_OUTPUT_LINES = 1000;
    private static final int MAX_OUTPUT_CHARS = 100000; // 100KB
    private static final Set<String> IGNORED_PATTERNS = new HashSet<>(Arrays.asList(
        "\\.git", "node_modules", "target", "\\.class", "\\.jar",
        "__pycache__", "\\.venv", "\\.idea", "\\.DS_Store"
    ));
    
    @Override public String getName() { return "PowerShell"; }
    @Override public String getDescription() { return "执行 PowerShell 命令"; }
    @Override public String getPrompt() { return "Execute PowerShell commands on Windows. Use this tool to run Windows-specific commands."; }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"command\": {\"type\": \"string\", \"description\": \"The PowerShell command to execute\"}}, \"required\": [\"command\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }
    
    /**
     * 新版 3 参数 call 方法（Tool 接口标准）
     */
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            String command = input.command;
            if (command == null || command.isEmpty()) {
                return ToolResult.error("命令不能为空");
            }
            
            Process process = null;
            try {
                // 使用 ProcessBuilder 执行 PowerShell 命令
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
                pb.redirectErrorStream(true); // 合并 stdout 和 stderr，避免管道死锁
                process = pb.start();
                
                // 在后台线程异步读取输出，避免主线程阻塞在 I/O 上
                StringBuilder outputBuilder = new StringBuilder();
                StringBuilder errorBuilder = new StringBuilder();
                CompletableFuture<Void> outputFuture = readProcessOutputAsync(process, outputBuilder, errorBuilder);
                
                // 主线程等待进程完成（带超时）
                boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                
                if (!finished) {
                    // 超时，强制终止进程
                    process.destroyForcibly();
                    try {
                        outputFuture.get(2000, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        outputFuture.cancel(true);
                    }
                    Output timeoutResult = new Output();
                    timeoutResult.success = false;
                    timeoutResult.output = truncateOutput(outputBuilder.toString());
                    timeoutResult.error = "PowerShell 命令执行超时（" + PROCESS_TIMEOUT_SECONDS + "秒）";
                    timeoutResult.exitCode = -1;
                    return ToolResult.success(timeoutResult);
                }
                
                // 等待输出读取完成
                outputFuture.get(2000, TimeUnit.MILLISECONDS);
                
                int exitCode = process.exitValue();
                String stdout = outputBuilder.toString();
                String stderr = errorBuilder.toString();
                
                Output result = new Output();
                result.exitCode = exitCode;
                result.error = stderr;
                
                if (exitCode == 0) {
                    result.success = true;
                    result.output = filterFileListOutput(stdout, command);
                    return ToolResult.success(result);
                } else {
                    // 非零退出码应该返回失败状态，同时包含Output数据
                    result.success = false;
                    String errorMsg = stderr;
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = stdout;
                    }
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = "PowerShell 命令执行失败（exit code: " + exitCode + "）";
                    }
                    result.output = truncateOutput(stdout);
                    result.error = errorMsg;
                    // 直接构造包含数据的失败结果
                    ToolResult<Output> errorResult = new ToolResult<>(result);
                    errorResult.setSuccess(false);
                    errorResult.setContent(errorMsg);
                    return errorResult;
                }
                
            } catch (Exception e) {
                logger.severe("执行 PowerShell 命令失败: " + e.getMessage());
                return ToolResult.error("执行 PowerShell 命令失败: " + e.getMessage());
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });
    }
    
    /**
     * 异步读取进程输出
     */
    private CompletableFuture<Void> readProcessOutputAsync(Process process, StringBuilder stdoutBuilder, StringBuilder stderrBuilder) {
        return CompletableFuture.runAsync(() -> {
            // 读取合并后的输出流（redirectErrorStream=true 后，errorStream 已无效，都从 inputStream 读）
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineCount < MAX_OUTPUT_LINES) {
                        stdoutBuilder.append(line).append("\n");
                        lineCount++;
                    } else {
                        break;
                    }
                    if (stdoutBuilder.length() > MAX_OUTPUT_CHARS) {
                        stdoutBuilder.setLength(MAX_OUTPUT_CHARS);
                        stdoutBuilder.append("\n...[输出被截断]");
                        break;
                    }
                }
            } catch (IOException e) {
                logger.warning("读取 PowerShell 输出失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 截断输出
     */
    private String truncateOutput(String output) {
        if (output == null) return "";
        if (output.length() <= MAX_OUTPUT_CHARS) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_CHARS) + "\n...[输出被截断，总长度: " + output.length() + " 字符]";
    }
    
    /**
     * 旧版 5 参数 call 方法（兼容旧代码）
     */
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolContext context,
            CanUseToolFn canUseTool,
            Object parentMessage,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        // 调用新版方法
        return call(input, null, onProgress);
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.command == null || input.command.isEmpty()) {
            return ToolValidationResult.invalid("命令不能为空");
        }
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        // PowerShell 命令可能是只读也可能是破坏性的，这里保守估计
        String cmd = input.command != null ? input.command.toLowerCase() : "";
        return cmd.startsWith("get-") || cmd.startsWith("test-") || cmd.startsWith("select-");
    }
    
    /**
     * 过滤文件列表输出，自动忽略无关文件并限制数量
     */
    private String filterFileListOutput(String output, String command) {
        String lowerCommand = command.toLowerCase();
        boolean isFileListCommand = lowerCommand.contains("get-childitem") ||
                                   lowerCommand.contains("gci") ||
                                   lowerCommand.contains("dir ") ||
                                   lowerCommand.contains("ls ");
        
        if (!isFileListCommand) {
            return output;
        }
        
        String[] lines = output.split("\n");
        List<String> resultLines = new ArrayList<>();
        int ignoredCount = 0;
        
        for (String line : lines) {
            if (shouldIgnoreLine(line)) {
                ignoredCount++;
                continue;
            }
            resultLines.add(line);
        }
        
        StringBuilder filtered = new StringBuilder();
        boolean truncated = false;
        int totalResults = resultLines.size();
        
        if (totalResults > MAX_FILE_LIST_RESULTS) {
            for (int i = 0; i < MAX_FILE_LIST_RESULTS; i++) {
                filtered.append(resultLines.get(i)).append("\n");
            }
            filtered.append("\n⚠️ 文件过多（共 ").append(totalResults).append(" 个，超过 ").append(MAX_FILE_LIST_RESULTS).append(" 个限制）。已只显示前 ").append(MAX_FILE_LIST_RESULTS).append(" 个。\n");
            filtered.append("💡 建议：请按子目录逐个扫描，或使用更精确的过滤条件（如 -Filter '*.md'、-Depth 2、-Exclude node_modules）。\n");
            truncated = true;
        } else {
            for (String line : resultLines) {
                filtered.append(line).append("\n");
            }
        }
        
        String result = filtered.toString();
        if (ignoredCount > 0) {
            if (truncated) {
                result = result.trim() + "\n[此外已自动过滤 " + ignoredCount + " 个无关文件/目录，如 .git、target、node_modules 等]";
            } else {
                result = result.trim() + "\n\n[已自动过滤 " + ignoredCount + " 个无关文件/目录，如 .git、target、node_modules 等]";
            }
        }
        
        return result;
    }
    
    /**
     * 检查行是否应该被忽略
     */
    private boolean shouldIgnoreLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String normalizedLine = line.replace('\\', '/');
        for (String pattern : IGNORED_PATTERNS) {
            try {
                String regex = pattern.contains("\\\\") ? pattern.replace("\\\\", "/") : pattern;
                if (normalizedLine.matches(".*" + regex + ".*")) {
                    return true;
                }
            } catch (Exception e) {
                // 正则表达式无效，跳过此模式
            }
        }
        return false;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Input { 
        public String command; 
        
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
