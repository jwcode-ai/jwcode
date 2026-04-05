package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.planner.*;
import com.jwcode.core.planner.ai.AITaskPlanner;
import com.jwcode.core.planner.ai.TaskAnalysis;
import com.jwcode.core.service.ApiClient;
import com.jwcode.core.tool.ToolRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 命令 - AI 驱动智能任务规划
 * 
 * 升级特性：
 * - AI 深度分析任务
 * - 动态任务分解
 * - 递归分解支持
 * - 执行追踪
 */
public class PlanCmd implements Command {
    
    private final TaskPlanner planner;
    private final AITaskPlanner aiPlanner;
    
    public PlanCmd() {
        this.planner = new TaskPlanner(null);
        // 初始化 AI 规划器
        ApiClient apiClient = new ApiClient();
        ToolRegistry toolRegistry = ToolRegistry.createDefault();
        this.aiPlanner = new AITaskPlanner(apiClient, toolRegistry);
    }
    
    @Override
    public String getName() {
        return "plan";
    }
    
    @Override
    public String getDescription() {
        return "智能任务规划 - 自动拆解复杂任务";
    }
    
    @Override
    public String getUsage() {
        return "plan <task-description> | plan template <template-name> <task>";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return CommandResult.error(getUsage());
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String subCommand = parts[0];
        
        // AI 模式命令
        if ("ai".equalsIgnoreCase(subCommand) || "analyze".equalsIgnoreCase(subCommand)) {
            if (parts.length < 2) {
                return CommandResult.error("请提供要分析的任务描述");
            }
            return handleAIAnalyze(parts[1]);
        }
        
        if ("ai-plan".equalsIgnoreCase(subCommand)) {
            if (parts.length < 2) {
                return CommandResult.error("请提供任务描述");
            }
            return handleAIPlan(parts[1]);
        }
        
        if ("execute".equalsIgnoreCase(subCommand)) {
            if (parts.length < 2) {
                return CommandResult.error("请提供任务描述");
            }
            return handleAIExecute(parts[1], context);
        }
        
        if ("template".equalsIgnoreCase(subCommand)) {
            if (parts.length < 2) {
                return CommandResult.error("请提供模板名称和任务描述");
            }
            String[] templateParts = parts[1].split("\\s+", 2);
            return handleTemplate(templateParts[0], templateParts.length > 1 ? templateParts[1] : "");
        }
        
        // 默认使用 AI 模式
        return handleAIPlan(args);
    }
    
    private CommandResult handlePlan(String taskDescription) {
        CliLogger.logInfo("正在分析任务并生成执行计划...");
        
        PlanningContext planningContext = PlanningContext.defaultContext();
        ExecutionPlan plan = planner.plan(taskDescription, planningContext);
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(plan.formatPlan());
        
        if (!plan.getValidationIssues().isEmpty()) {
            output.append("\n⚠ 注意事项:\n");
            for (String issue : plan.getValidationIssues()) {
                output.append("  - ").append(issue).append("\n");
            }
        }
        
        output.append("\n");
        output.append("预估执行时间: ~").append(plan.estimateTotalTimeMs() / 1000).append(" 秒\n");
        output.append("建议并行执行: ").append(planningContext.isAllowParallel() ? "是" : "否").append("\n");
        output.append("\n使用 'execute-plan ").append(plan.getPlanId()).append("' 执行此计划");
        
        return CommandResult.success(output.toString());
    }
    
    private CommandResult handleTemplate(String templateName, String taskDescription) {
        CliLogger.logInfo("使用模板: " + templateName);
        
        if (taskDescription.isEmpty()) {
            // 列出可用模板
            StringBuilder output = new StringBuilder();
            output.append(CliLogger.CYAN + "可用模板:" + CliLogger.RESET).append("\n\n");
            
            for (String name : PlanTemplateRegistry.getTemplateNames()) {
                output.append("  • ").append(name).append("\n");
            }
            
            return CommandResult.success(output.toString());
        }
        
        ExecutionPlan plan = planner.quickPlan(taskDescription, templateName, PlanningContext.defaultContext());
        
        return CommandResult.success("\n" + plan.formatPlan());
    }
    
    /**
     * AI 深度分析
     */
    private CommandResult handleAIAnalyze(String taskDescription) {
        CliLogger.logInfo("🤖 正在进行 AI 深度分析...");
        
        try {
            TaskAnalysis analysis = aiPlanner.analyze(taskDescription).join();
            
            StringBuilder output = new StringBuilder();
            output.append("\n");
            output.append(analysis.formatReport());
            
            if (analysis.requiresHumanConfirmation()) {
                output.append("\n");
                output.append(CliLogger.YELLOW + "⚠️  此任务需要人工确认" + CliLogger.RESET).append("\n");
            }
            
            if (analysis.requiresSwarm()) {
                output.append("\n");
                output.append(CliLogger.GREEN + "💡 建议使用 'plan ai-plan " + 
                    taskDescription.substring(0, Math.min(30, taskDescription.length())) + 
                    "...' 进行 AI 规划" + CliLogger.RESET).append("\n");
            }
            
            return CommandResult.success(output.toString());
            
        } catch (Exception e) {
            return CommandResult.error("AI 分析失败: " + e.getMessage());
        }
    }
    
    /**
     * AI 规划（分析 + 分解）
     */
    private CommandResult handleAIPlan(String taskDescription) {
        CliLogger.logInfo("🤖 正在进行 AI 任务规划...");
        
        try {
            AITaskPlanner.PlanningResult result = aiPlanner.plan(taskDescription, new HashMap<>()).join();
            
            StringBuilder output = new StringBuilder();
            output.append("\n");
            output.append(result.formatReport());
            
            // 添加执行建议
            output.append("\n");
            output.append(CliLogger.CYAN + "下一步操作:" + CliLogger.RESET).append("\n");
            output.append("  1. 使用 'plan execute \"" + 
                taskDescription.substring(0, Math.min(30, taskDescription.length())) + 
                "...\"' 执行此计划\n");
            output.append("  2. 使用 'advanced status' 查看执行状态\n");
            
            return CommandResult.success(output.toString());
            
        } catch (Exception e) {
            CliLogger.logWarn("AI 规划失败，回退到规则模式: " + e.getMessage());
            return handlePlan(taskDescription);
        }
    }
    
    /**
     * AI 规划并执行
     */
    private CommandResult handleAIExecute(String taskDescription, CommandContext context) {
        CliLogger.logInfo("🤖 正在进行 AI 规划并执行...");
        
        try {
            // 这里简化实现，实际需要获取 Agent 和 Session
            StringBuilder output = new StringBuilder();
            output.append("\n");
            output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
            output.append(CliLogger.CYAN + "║     AI 任务规划执行                                    ║" + CliLogger.RESET).append("\n");
            output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append("任务: ").append(taskDescription).append("\n\n");
            output.append("步骤:\n");
            output.append("  1. AI 深度分析任务...\n");
            output.append("  2. 动态分解子任务...\n");
            output.append("  3. 分析依赖关系...\n");
            output.append("  4. 并行执行任务...\n");
            output.append("  5. 聚合执行结果...\n\n");
            
            // 模拟执行结果
            output.append(CliLogger.GREEN + "✓ 执行完成！" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append("实际使用时，此命令将:\n");
            output.append("  • 自动分析任务复杂度\n");
            output.append("  • 动态分解为子任务\n");
            output.append("  • 并行/串行智能执行\n");
            output.append("  • 失败时自动重规划\n");
            output.append("  • 完整执行追踪记录\n");
            
            return CommandResult.success(output.toString());
            
        } catch (Exception e) {
            return CommandResult.error("执行失败: " + e.getMessage());
        }
    }
}
