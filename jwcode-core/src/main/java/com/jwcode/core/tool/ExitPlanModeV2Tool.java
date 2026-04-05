package com.jwcode.core.tool;

import com.jwcode.core.tool.input.ExitPlanModeInput;
import com.jwcode.core.tool.output.ExitPlanModeOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;

/**
 * ExitPlanModeV2 工具
 * 用于退出计划模式，返回正常对话模式
 */
public class ExitPlanModeV2Tool implements Tool<ExitPlanModeInput, ExitPlanModeOutput, Void> {
    
    private static final String STATE_FILE = ".jwcode/state.json";
    private volatile String currentMode = "normal"; // normal, plan, act
    
    public ExitPlanModeV2Tool() {
        // 初始化时读取当前模式
        loadCurrentMode();
    }
    
    private void loadCurrentMode() {
        // 从状态文件读取当前模式
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(
                    System.getProperty("user.dir"), STATE_FILE);
            if (java.nio.file.Files.exists(path)) {
                String content = java.nio.file.Files.readString(path);
                // 简单解析（实际使用 Jackson）
                if (content.contains("\"mode\"")) {
                    int start = content.indexOf("\"mode\"") + 7;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        currentMode = content.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误，使用默认值
        }
    }
    
    @Override
    public String getName() {
        return "ExitPlanModeV2";
    }
    
    @Override
    public String getDescription() {
        return "Exit from plan mode and return to normal mode with a summary of the plan.";
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool when you want to exit plan mode. " +
               "This tool requires confirmation (confirmed=true) to actually exit. " +
               "Provide a summary of what was planned.";
    }
    
    @Override
    public CompletableFuture<ToolResult<ExitPlanModeOutput>> call(
            ExitPlanModeInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            // 检查是否在计划模式
            if (!"plan".equals(currentMode)) {
                return ToolResult.success(ExitPlanModeOutput.failure("当前不在计划模式"));
            }
            
            // 检查是否确认
            Boolean confirmed = input.confirmed();
            if (confirmed == null || !confirmed) {
                return ToolResult.success(ExitPlanModeOutput.failure("需要确认才能退出计划模式"));
            }
            
            // 执行退出
            try {
                // 保存摘要
                String summary = input.summary() != null ? input.summary() : "计划完成";
                saveSummary(summary);
                
                // 更新模式
                currentMode = "normal";
                saveMode("normal");
                
                return ToolResult.success(ExitPlanModeOutput.success("normal", summary));
                
            } catch (Exception e) {
                return ToolResult.error("退出计划模式失败: " + e.getMessage());
            }
        });
    }
    
    private void saveSummary(String summary) throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get(
                System.getProperty("user.dir"), STATE_FILE);
        java.nio.file.Files.createDirectories(path.getParent());
        
        String content = "{\"mode\":\"normal\",\"summary\":\"" + 
                summary.replace("\"", "\\\"") + "\"}";
        java.nio.file.Files.writeString(path, content);
    }
    
    private void saveMode(String mode) throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get(
                System.getProperty("user.dir"), STATE_FILE);
        String content = "{\"mode\":\"" + mode + "\"}";
        java.nio.file.Files.writeString(path, content);
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<ExitPlanModeInput> getInputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<ExitPlanModeOutput> getOutputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(ExitPlanModeInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(ExitPlanModeInput input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(ExitPlanModeInput input) {
        return false;
    }
    
    @Override
    public boolean requiresApproval(ExitPlanModeInput input) {
        return false;
    }
    
    /**
     * 获取当前模式
     */
    public String getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 设置为计划模式
     */
    public void enterPlanMode() {
        currentMode = "plan";
        try {
            saveMode("plan");
        } catch (Exception e) {
            // 忽略错误
        }
    }
}