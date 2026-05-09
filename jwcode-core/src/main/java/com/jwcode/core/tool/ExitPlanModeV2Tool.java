package com.jwcode.core.tool;

import com.jwcode.core.plan.PlanModeManager;
import com.jwcode.core.tool.input.ExitPlanModeInput;
import com.jwcode.core.tool.output.ExitPlanModeOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * ExitPlanModeV2Tool — 退出计划模式工具（增强版）。
 * 
 * <p>退出 Plan Mode 后：</p>
 * <ul>
 *   <li>恢复所有工具的写权限</li>
 *   <li>Plan 文件内容由 ExitPlanMode 从文件系统读取（不接受 plan 内容作为参数）</li>
 *   <li>退出后自动请求用户审批 plan</li>
 * </ul>
 * 
 * <p>设计取舍：</p>
 * <ul>
 *   <li><b>不接受 plan 内容作为参数</b> — 避免重复（plan 已写进文件），强制持久化，强制结构化</li>
 *   <li><b>需要 confirmed=true</b> — 防止误退出</li>
 *   <li><b>不要用 AskUserQuestion 问"我的 plan 行不行"</b> — ExitPlanMode 本身就请求用户审批</li>
 * </ul>
 */
public class ExitPlanModeV2Tool implements Tool<ExitPlanModeInput, ExitPlanModeOutput, Void> {
    
    private static final Logger logger = Logger.getLogger(ExitPlanModeV2Tool.class.getName());
    
    @Override
    public String getName() {
        return "ExitPlanModeV2";
    }
    
    @Override
    public String getDescription() {
        return "Exit from plan mode and return to normal mode. " +
               "The plan content is read from the plan file (not passed as parameter). " +
               "This inherently requests user approval of the plan.";
    }
    
    @Override
    public String getPrompt() {
        return """
            **ExitPlanModeV2** — 退出计划模式
            
            退出 Plan Mode 并返回正常模式。Plan 内容从文件系统读取，不接受 plan 内容作为参数。
            
            **重要**：
            - 不要用 AskUserQuestion 问"我的 plan 行不行" — ExitPlanMode 本身就请求用户审批
            - 需要 confirmed=true 才能退出
            - Plan 文件必须在调用前已写入
            
            **标准流程**：
            1. 写 plan 到指定文件
            2. 调用 ExitPlanModeV2(confirmed=true, summary="...")
            3. 等待用户审批
            """;
    }
    
    @Override
    public CompletableFuture<ToolResult<ExitPlanModeOutput>> call(
            ExitPlanModeInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检查是否在计划模式
                PlanModeManager modeManager = PlanModeManager.getInstance();
                if (!modeManager.isPlanMode()) {
                    return ToolResult.<ExitPlanModeOutput>success(ExitPlanModeOutput.failure("当前不在计划模式"));
                }
                
                // 检查是否确认
                Boolean confirmed = input != null ? input.confirmed() : null;
                if (confirmed == null || !confirmed) {
                    return ToolResult.<ExitPlanModeOutput>success(ExitPlanModeOutput.failure("需要确认才能退出计划模式"));
                }
                
                // 执行退出
                String summary = input != null && input.summary() != null 
                    ? input.summary() 
                    : "计划完成";
                
                boolean success = modeManager.exitPlanMode(summary);
                
                if (success) {
                    logger.info("Exited Plan Mode: " + summary);
                    
                    // 读取 plan 文件内容（如果有）
                    String planContent = readPlanFile();
                    
                    return ToolResult.<ExitPlanModeOutput>success(ExitPlanModeOutput.success("normal", summary, planContent));
                } else {
                    return ToolResult.<ExitPlanModeOutput>error("退出计划模式失败");
                }
                
            } catch (Exception e) {
                logger.warning("ExitPlanMode failed: " + e.getMessage());
                return ToolResult.<ExitPlanModeOutput>error("退出计划模式失败: " + e.getMessage());
            }
        });
    }

    
    /**
     * 从 plan 文件读取内容
     * ExitPlanMode 不接受 plan 内容作为参数，而是从文件系统读取
     */
    private String readPlanFile() {
        try {
            java.nio.file.Path planPath = java.nio.file.Paths.get(
                    System.getProperty("user.dir"), ".jwcode", "plan.md");
            if (java.nio.file.Files.exists(planPath)) {
                return java.nio.file.Files.readString(planPath);
            }
        } catch (Exception e) {
            logger.fine("No plan file found: " + e.getMessage());
        }
        return null;
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
    
    @Override
    public Set<SideEffect> getSideEffects() {
        return Set.of(SideEffect.SESSION_MUTATION);
    }
    
    @Override
    public ToolCategory getCategory() {
        return ToolCategory.METACOGNITION;
    }
}
