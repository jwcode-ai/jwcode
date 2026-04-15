package com.jwcode.core.tool;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PowerShellTool implements Tool<PowerShellTool.Input, PowerShellTool.Output, PowerShellTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
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
                    result.output = output.toString();
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
