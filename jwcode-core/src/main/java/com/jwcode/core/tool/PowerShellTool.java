package com.jwcode.core.tool;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PowerShellTool implements Tool<PowerShellTool.Input, PowerShellTool.Output, PowerShellTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_FILE_LIST_RESULTS = 100;
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
            
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            try {
                // 使用 ProcessBuilder 执行 PowerShell 命令
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
                pb.redirectErrorStream(false);
                Process process = pb.start();
                
                // 读取标准输出
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                
                // 读取错误输出
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    Output result = new Output();
                    result.success = true;
                    result.output = filterFileListOutput(output.toString(), command);
                    result.error = error.toString();
                    result.exitCode = exitCode;
                    return ToolResult.success(result);
                } else {
                    String errorMsg = error.toString();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = output.toString();
                    }
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = "PowerShell 命令执行失败，无输出";
                    }
                    return ToolResult.error(errorMsg);
                }
                
            } catch (Exception e) {
                return ToolResult.error("执行 PowerShell 命令失败: " + e.getMessage());
            }
        });
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
