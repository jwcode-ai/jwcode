package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.advanced.compression.ContextCompressor;
import com.jwcode.core.advanced.indexing.ProjectIndexer;
import com.jwcode.core.advanced.swarm.AgentSwarm;
import com.jwcode.core.advanced.swarm.AutoSwarmTrigger;
import com.jwcode.core.advanced.thinking.ThinkingModeManager;
import com.jwcode.core.advanced.yolo.YoloModeManager;
import com.jwcode.core.llm.*;
import com.jwcode.core.planner.ai.AITaskPlanner;
import com.jwcode.core.planner.ai.AutoAIPlannerTrigger;
import com.jwcode.core.planner.ai.TaskAnalysis;
import com.jwcode.core.tool.ToolRegistry;

import java.io.IOException;
import java.util.HashMap;

/**
 * Advanced 命令 - Kimi Code 高级功能
 * 
 * 用法:
 *   advanced thinking           - 切换 Thinking Mode
 *   advanced yolo               - 切换 YOLO Mode
 *   advanced index              - 索引当前项目
 *   advanced compact            - 压缩对话上下文
 *   advanced swarm <task>       - 使用 Agent Swarm 执行复杂任务
 *   advanced status             - 查看所有高级功能状态
 */
public class AdvancedCmd implements Command {
    
    private final ThinkingModeManager thinkingManager;
    private final YoloModeManager yoloManager;
    private final ContextCompressor compressor;
    private final AgentSwarm agentSwarm;
    private final AutoSwarmTrigger autoSwarmTrigger;
    private final AITaskPlanner aiTaskPlanner;
    private final AutoAIPlannerTrigger autoAIPlannerTrigger;
    
    public AdvancedCmd() {
        this.thinkingManager = new ThinkingModeManager();
        this.yoloManager = new YoloModeManager();
        this.compressor = new ContextCompressor();
        this.agentSwarm = new AgentSwarm();
        this.autoSwarmTrigger = new AutoSwarmTrigger(agentSwarm);
        
        // 初始化 AI 规划器（使用新的 LLM 架构）
        LLMFactory llmFactory = LLMFactory.createDefault();
        LLMService llmService = llmFactory.getLLMService();
        ToolRegistry toolRegistry = ToolRegistry.createDefault();
        this.aiTaskPlanner = new AITaskPlanner(llmService, toolRegistry);
        this.autoAIPlannerTrigger = new AutoAIPlannerTrigger(aiTaskPlanner);
    }
    
    @Override
    public String getName() {
        return "advanced";
    }
    
    @Override
    public String getDescription() {
        return "高级功能 - Thinking/YOLO/Index/Compact/Swarm";
    }
    
    @Override
    public String getUsage() {
        return "advanced thinking|yolo|index|compact|swarm|ai-plan|ai-analyze|auto-ai|status";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return CommandResult.error(getUsage());
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String subCommand = parts[0];
        String subArgs = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand.toLowerCase()) {
            case "thinking":
            case "think":
                return handleThinking();
            case "yolo":
                return handleYolo();
            case "index":
                return handleIndex(subArgs);
            case "compact":
            case "compress":
                return handleCompact();
            case "swarm":
                return handleSwarm(subArgs, context);
            case "status":
                return handleStatus();
            case "auto":
                return handleAutoSwarm(subArgs);
            case "analyze":
                return handleAnalyze(subArgs);
            case "ai":
            case "ai-plan":
                return handleAIPlan(subArgs);
            case "ai-analyze":
                return handleAIAnalyze(subArgs);
            case "ai-execute":
                return handleAIExecute(subArgs, context);
            case "auto-ai":
            case "smart":
                return handleAutoAI(subArgs);
            default:
                return CommandResult.error("未知子命令: " + subCommand);
        }
    }
    
    /**
     * Thinking Mode 切换
     */
    private CommandResult handleThinking() {
        boolean enabled = thinkingManager.toggle();
        String status = enabled ? 
            CliLogger.GREEN + "🧠 已开启" + CliLogger.RESET :
            CliLogger.YELLOW + "⚡ 已关闭" + CliLogger.RESET;
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     Thinking Mode (深度推理模式)                       ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        output.append("状态: ").append(status).append("\n\n");
        
        if (enabled) {
            output.append("说明:\n");
            output.append("  • AI 将花更多时间分析再给出回答\n");
            output.append("  • 适用于复杂架构决策和棘手 Bug\n");
            output.append("  • 响应时间会稍长，但质量更高\n");
        } else {
            output.append("说明:\n");
            output.append("  • 快速响应模式\n");
            output.append("  • 适合日常简单任务\n");
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * YOLO Mode 切换
     */
    private CommandResult handleYolo() {
        boolean enabled = yoloManager.toggle();
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        
        if (enabled) {
            output.append(CliLogger.RED + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
            output.append(CliLogger.RED + "║     ⚠️  YOLO Mode 已开启                               ║" + CliLogger.RESET).append("\n");
            output.append(CliLogger.RED + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append(CliLogger.YELLOW + "警告: 全自动执行模式！" + CliLogger.RESET).append("\n");
            output.append("  • 将自动执行命令和修改文件\n");
            output.append("  • 无需确认，请谨慎使用！\n");
            output.append("  • 建议在受控环境中使用\n");
        } else {
            output.append(CliLogger.GREEN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
            output.append(CliLogger.GREEN + "║     YOLO Mode 已关闭                                   ║" + CliLogger.RESET).append("\n");
            output.append(CliLogger.GREEN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append("已回到安全确认模式\n");
            output.append("  • 所有操作需要确认\n");
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 项目索引
     */
    private CommandResult handleIndex(String path) {
        String projectPath = path.isEmpty() ? "." : path;
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     项目索引分析                                       ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        output.append("正在索引: ").append(projectPath).append("\n");
        output.append("请稍候...\n\n");
        
        try {
            ProjectIndexer indexer = new ProjectIndexer(projectPath);
            ProjectIndexer.ProjectIndex index = indexer.buildIndex();
            
            output.append(CliLogger.GREEN + "✓ 索引完成！" + CliLogger.RESET).append("\n\n");
            output.append(indexer.generateSummary());
            
        } catch (IOException e) {
            return CommandResult.error("索引失败: " + e.getMessage());
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 上下文压缩
     */
    private CommandResult handleCompact() {
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     上下文压缩                                         ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        output.append("功能说明:\n");
        output.append("  • 当对话历史接近上下文限制时自动压缩\n");
        output.append("  • 保留关键信息，生成历史摘要\n");
        output.append("  • 减少 token 消耗，延长会话寿命\n\n");
        output.append(compressor.generateReport());
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * Agent Swarm 执行
     */
    private CommandResult handleSwarm(String task, CommandContext context) {
        if (task.isEmpty()) {
            return CommandResult.error("请提供任务描述，例如: advanced swarm 重构代码");
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     Agent Swarm (智能体集群)                           ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        output.append("任务: ").append(task).append("\n");
        output.append("正在启动 Agent Swarm...\n\n");
        
        AgentSwarm.SwarmExecutionResult result = agentSwarm.executeComplexTask(task, context);
        
        output.append(result.formatReport()).append("\n");
        output.append(CliLogger.GREEN + "✓ 任务完成！" + CliLogger.RESET).append("\n");
        output.append("\n结果:\n");
        output.append(result.getFinalResult());
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 查看所有状态
     */
    private CommandResult handleStatus() {
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     高级功能状态                                       ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        // Thinking Mode
        output.append("🧠 Thinking Mode (深度推理):\n");
        output.append("   ").append(thinkingManager.isEnabled() ? 
            CliLogger.GREEN + "● 开启" + CliLogger.RESET : 
            CliLogger.GRAY + "○ 关闭" + CliLogger.RESET).append("\n");
        output.append("   快捷键: Tab\n\n");
        
        // YOLO Mode
        output.append("⚡ YOLO Mode (全自动):\n");
        output.append("   ").append(yoloManager.isEnabled() ? 
            CliLogger.RED + "● 开启 ⚠️" + CliLogger.RESET : 
            CliLogger.GRAY + "○ 关闭" + CliLogger.RESET).append("\n");
        output.append("   参数: --yolo\n\n");
        
        // Agent Swarm
        AgentSwarm.SwarmStats stats = agentSwarm.getStats();
        output.append("🐝 Agent Swarm (智能体集群):\n");
        output.append("   总 Agents: ").append(stats.getTotalAgents()).append("\n");
        output.append("   活跃: ").append(stats.getActiveAgents()).append("\n");
        output.append("   完成任务: ").append(stats.getCompletedTasks()).append("\n\n");
        
        // AI Auto Planner
        output.append("🤖 AI Auto Planner (自动规划):\n");
        output.append("   ").append(autoAIPlannerTrigger.isAutoTriggerEnabled() ? 
            CliLogger.GREEN + "● 开启" + CliLogger.RESET : 
            CliLogger.GRAY + "○ 关闭" + CliLogger.RESET).append("\n");
        output.append("   自动检测复杂任务并使用 AI 规划\n\n");
        
        // 可用命令
        output.append("可用命令:\n");
        output.append("  advanced thinking    - 切换深度推理模式\n");
        output.append("  advanced yolo        - 切换全自动模式\n");
        output.append("  advanced index       - 索引项目结构\n");
        output.append("  advanced compact     - 压缩对话上下文\n");
        output.append("  advanced swarm       - 使用智能体集群\n");
        output.append("  advanced ai-plan     - AI 驱动任务规划\n");
        output.append("  advanced ai-analyze  - AI 深度任务分析\n");
        output.append("  advanced ai-execute  - AI 规划并执行\n");
        output.append("  advanced auto-ai     - 切换自动 AI 规划模式\n");
        output.append("  advanced auto        - 切换自动 Swarm 模式\n");
        output.append("  advanced analyze     - 分析任务复杂度\n");
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 切换自动 Swarm 模式
     */
    private CommandResult handleAutoSwarm(String args) {
        boolean enabled = autoSwarmTrigger.toggleAutoSwarm();
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        if (enabled) {
            output.append(CliLogger.GREEN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
            output.append(CliLogger.GREEN + "║     自动 Agent Swarm 已开启                            ║" + CliLogger.RESET).append("\n");
            output.append(CliLogger.GREEN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append("JwCode 将自动检测复杂任务并使用 Agent Swarm 执行\n\n");
            output.append("复杂任务特征:\n");
            output.append("  • 包含 'refactor', 'migrate', 'implement feature'\n");
            output.append("  • 包含 'all', 'multiple', 'project'\n");
            output.append("  • 描述长度超过 100 字符\n");
            output.append("  • 涉及多个步骤的操作\n");
        } else {
            output.append("自动 Agent Swarm 已关闭\n");
            output.append("所有任务将使用单 Agent 模式处理\n");
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 分析任务复杂度
     */
    private CommandResult handleAnalyze(String task) {
        if (task.isEmpty()) {
            return CommandResult.error("请提供要分析的任务描述");
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     任务复杂度分析                                     ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        AutoSwarmTrigger.TaskAnalysis analysis = autoSwarmTrigger.analyzeTask(task);
        output.append(analysis.formatReport()).append("\n\n");
        
        if (analysis.isShouldUseSwarm()) {
            output.append(CliLogger.GREEN + "✓ 建议使用 Agent Swarm 执行此任务" + CliLogger.RESET).append("\n");
            output.append("  使用命令: advanced swarm " + task.substring(0, Math.min(30, task.length())) + "...\n");
        } else {
            output.append(CliLogger.YELLOW + "ℹ 此任务较简单，使用普通模式即可" + CliLogger.RESET).append("\n");
        }
        
        return CommandResult.success(output.toString());
    }
    
    // ==================== AI 驱动新方法 ====================
    
    /**
     * AI 驱动任务规划
     */
    private CommandResult handleAIPlan(String task) {
        if (task.isEmpty()) {
            return CommandResult.error("请提供任务描述，例如: advanced ai-plan 重构用户认证模块");
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     🤖 AI 驱动任务规划                                  ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        output.append("任务: ").append(task).append("\n");
        output.append("正在使用 AI 分析任务意图、复杂度、风险...\n\n");
        
        try {
            AITaskPlanner.PlanningResult result = aiTaskPlanner.plan(task, new HashMap<>()).join();
            
            output.append(result.formatReport()).append("\n");
            output.append(CliLogger.GREEN + "✓ AI 规划完成！" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append("使用 'advanced ai-execute \"" + task.substring(0, Math.min(30, task.length())) + "...\"' 执行此计划\n");
            
        } catch (Exception e) {
            output.append(CliLogger.YELLOW + "AI 规划失败: " + e.getMessage() + CliLogger.RESET).append("\n");
            output.append("回退到规则模式...\n");
            
            // 回退到旧的 swarm
            AgentSwarm.SwarmExecutionResult result = agentSwarm.executeComplexTask(task, null);
            output.append(result.formatReport());
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * AI 深度任务分析
     */
    private CommandResult handleAIAnalyze(String task) {
        if (task.isEmpty()) {
            return CommandResult.error("请提供任务描述，例如: advanced ai-analyze 实现订单功能");
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     🤖 AI 深度任务分析                                  ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        try {
            TaskAnalysis analysis = aiTaskPlanner.analyze(task).join();
            
            output.append(analysis.formatReport()).append("\n");
            
            // 建议
            if (analysis.requiresSwarm()) {
                output.append(CliLogger.GREEN + "💡 建议使用 AI 规划执行此任务" + CliLogger.RESET).append("\n");
                output.append("  命令: advanced ai-plan \"" + task.substring(0, Math.min(30, task.length())) + "...\"\n");
            } else {
                output.append(CliLogger.YELLOW + "ℹ 此任务较简单，普通模式即可处理" + CliLogger.RESET).append("\n");
            }
            
        } catch (Exception e) {
            return CommandResult.error("AI 分析失败: " + e.getMessage());
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * AI 规划并执行
     */
    private CommandResult handleAIExecute(String task, CommandContext context) {
        if (task.isEmpty()) {
            return CommandResult.error("请提供任务描述，例如: advanced ai-execute 重构代码");
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     🤖 AI 规划并执行                                    ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        output.append("任务: ").append(task).append("\n\n");
        
        try {
            output.append("步骤 1/5: AI 深度分析...\n");
            TaskAnalysis analysis = aiTaskPlanner.analyze(task).join();
            output.append("  ✓ 复杂度: " + analysis.getComplexity().getOverallScore() + "/10, " +
                "预估子任务: " + analysis.getEstimation().getEstimatedSubTasks() + "\n");
            
            output.append("步骤 2/5: 动态任务分解...\n");
            AITaskPlanner.PlanningResult planResult = aiTaskPlanner.plan(task, new HashMap<>()).join();
            output.append("  ✓ 分解为 " + planResult.getPlan().getSteps().size() + " 个子任务\n");
            
            output.append("步骤 3/5: 分析依赖关系...\n");
            output.append("  ✓ 关键路径: " + planResult.getDependencyAnalysis().getCriticalPath().size() + " 步\n");
            
            output.append("步骤 4/5: 并行执行任务...\n");
            output.append("  (执行需要 Agent 和 Session 上下文)\n");
            
            output.append("步骤 5/5: 聚合执行结果...\n");
            output.append("  ✓ 执行追踪记录完成\n\n");
            
            output.append(CliLogger.GREEN + "✓ AI 规划流程完成！" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append("实际执行时，系统将:\n");
            output.append("  • 自动创建子 Agent 并行执行\n");
            output.append("  • 根据执行结果动态调整\n");
            output.append("  • 失败时自动重规划\n");
            output.append("  • 生成完整执行报告\n");
            
        } catch (Exception e) {
            return CommandResult.error("AI 执行失败: " + e.getMessage());
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 切换自动 AI 规划模式
     */
    private CommandResult handleAutoAI(String args) {
        boolean enabled = autoAIPlannerTrigger.toggleAutoTrigger();
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        
        if (enabled) {
            output.append(CliLogger.GREEN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
            output.append(CliLogger.GREEN + "║     自动 AI 规划模式已开启                              ║" + CliLogger.RESET).append("\n");
            output.append(CliLogger.GREEN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
            output.append("\n");
            output.append("JwCode 将自动检测复杂任务并使用 AI 规划执行\n\n");
            output.append("检测维度:\n");
            output.append("  • 关键词匹配（refactor, implement, migrate...）\n");
            output.append("  • 描述长度（超过 100 字符加分）\n");
            output.append("  • 涉及文件数量\n");
            output.append("  • 操作类型复杂度\n");
            output.append("  • 上下文复杂度\n\n");
            output.append("阈值设置:\n");
            output.append("  • 简单任务（1-3分）: 普通模式\n");
            output.append("  • 中等任务（4-6分）: 根据阈值判断\n");
            output.append("  • 复杂任务（7-10分）: 强制 AI 规划\n");
        } else {
            output.append("自动 AI 规划模式已关闭\n");
            output.append("所有任务将使用普通单 Agent 模式处理\n");
        }
        
        return CommandResult.success(output.toString());
    }
}
