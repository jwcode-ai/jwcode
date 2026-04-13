package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.agent.parallel.ParallelAgentExecutor;
import com.jwcode.core.agent.parallel.ParallelExecutionContext;
import com.jwcode.core.agent.parallel.ParallelExecutionResult;
import com.jwcode.core.agent.parallel.SubAgentResult;
import com.jwcode.core.agent.parallel.SubAgentTask;
import com.jwcode.core.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Parallel 命令 - 演示子 Agent 并行执行
 * 
 * 用法:
 *   parallel demo           - 运行并行执行演示
 *   parallel demo-dep       - 运行带依赖的演示
 *   parallel demo-scale <n> - 运行大规模并行演示
 */
public class ParallelCmd implements Command {
    
    private final AgentRegistry registry;
    
    public ParallelCmd() {
        this.registry = AgentRegistry.createDefault();
    }
    
    @Override
    public String getName() {
        return "parallel";
    }
    
    @Override
    public String getDescription() {
        return "子 Agent 并行执行演示";
    }
    
    @Override
    public String getUsage() {
        return "parallel demo | parallel demo-dep | parallel demo-scale <n>";
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
            case "demo":
                return runBasicDemo();
            case "demo-dep":
                return runDependencyDemo();
            case "demo-scale":
                int count = subArgs.isEmpty() ? 10 : Integer.parseInt(subArgs);
                return runScaleDemo(count);
            default:
                return CommandResult.error("未知子命令: " + subCommand);
        }
    }
    
    /**
     * 基础并行演示
     */
    private CommandResult runBasicDemo() {
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     子 Agent 并行执行演示                              ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        // 创建执行器
        ParallelAgentExecutor executor = new ParallelAgentExecutor(registry, null, 4);
        Session session = new Session(UUID.randomUUID().toString(), System.getProperty("user.dir"));
        
        // 创建 5 个独立任务
        List<SubAgentTask> tasks = List.of(
            createTask("analyzer-1", "分析项目结构", "default", 5),
            createTask("debugger-1", "查找潜在 Bug", "debug", 8),
            createTask("coder-1", "优化代码性能", "coder", 6),
            createTask("doc-1", "生成代码文档", "default", 3),
            createTask("tester-1", "编写单元测试", "default", 7)
        );
        
        output.append("创建任务:\n");
        for (SubAgentTask task : tasks) {
            output.append(String.format("  [%d] %s - %s (%s)%n", 
                task.getPriority(), task.getTaskId(), task.getInstruction(), task.getAgentType()));
        }
        output.append("\n开始并行执行...\n\n");
        
        long startTime = System.currentTimeMillis();
        
        try {
            ParallelExecutionResult result = executor.execute(tasks, session, 30000);
            
            long duration = System.currentTimeMillis() - startTime;
            
            output.append(CliLogger.GREEN + "✓ 执行完成!" + CliLogger.RESET).append("\n\n");
            output.append("统计:\n");
            output.append(String.format("  总任务: %d%n", result.getTotalCount()));
            output.append(String.format("  成功: %d%n", result.getSuccessCount()));
            output.append(String.format("  失败: %d%n", result.getFailureCount()));
            output.append(String.format("  成功率: %.1f%%%n", result.getSuccessRate()));
            output.append(String.format("  总耗时: %dms%n", duration));
            output.append(String.format("  平均每个任务: %.1fms%n", (double) duration / result.getTotalCount()));
            
            output.append("\n详细结果:\n");
            for (SubAgentResult r : result.getResults()) {
                String icon = r.isSuccess() ? CliLogger.GREEN + "✓" + CliLogger.RESET : CliLogger.RED + "✗" + CliLogger.RESET;
                output.append(String.format("  %s %-15s %5dms%n", 
                    icon, r.getTaskId(), r.getExecutionTimeMs()));
            }
            
            output.append("\n" + CliLogger.GRAY + "说明: 5个任务并行执行，总耗时远小于串行执行（串行预计500-1500ms）" + CliLogger.RESET).append("\n");
            
        } catch (Exception e) {
            output.append(CliLogger.RED + "执行失败: " + e.getMessage() + CliLogger.RESET).append("\n");
        } finally {
            executor.shutdown();
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 带依赖的任务演示
     */
    private CommandResult runDependencyDemo() {
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     带依赖关系的任务链演示                             ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        ParallelAgentExecutor executor = new ParallelAgentExecutor(registry, null, 4);
        Session session = new Session(UUID.randomUUID().toString(), System.getProperty("user.dir"));
        
        // 创建依赖链: A -> B -> C,  D -> E (C 和 E 都依赖完成后才执行 F)
        output.append("任务依赖图:\n");
        output.append("  step-a (需求分析) -> step-b (架构设计) -> step-c (编码实现)\n");
        output.append("  step-d (环境准备) -> step-e (配置部署)\n");
        output.append("  step-c + step-e -> step-f (集成测试)\n\n");
        
        List<SubAgentTask> tasks = List.of(
            SubAgentTask.builder()
                .taskId("step-a")
                .instruction("步骤A: 分析需求")
                .agentType("default")
                .priority(10)
                .build(),
            SubAgentTask.builder()
                .taskId("step-b")
                .instruction("步骤B: 设计架构")
                .agentType("default")
                .dependencies(List.of("step-a"))
                .priority(9)
                .build(),
            SubAgentTask.builder()
                .taskId("step-c")
                .instruction("步骤C: 编写代码")
                .agentType("coder")
                .dependencies(List.of("step-b"))
                .priority(8)
                .build(),
            SubAgentTask.builder()
                .taskId("step-d")
                .instruction("步骤D: 准备环境")
                .agentType("default")
                .priority(10)
                .build(),
            SubAgentTask.builder()
                .taskId("step-e")
                .instruction("步骤E: 部署配置")
                .agentType("default")
                .dependencies(List.of("step-d"))
                .priority(8)
                .build(),
            SubAgentTask.builder()
                .taskId("step-f")
                .instruction("步骤F: 集成测试")
                .agentType("default")
                .dependencies(List.of("step-c", "step-e"))
                .priority(7)
                .build()
        );
        
        output.append("开始执行（自动处理依赖关系）...\n\n");
        
        long startTime = System.currentTimeMillis();
        
        try {
            ParallelExecutionContext ctx = executor.executeWithDependencies(tasks, session);
            ParallelExecutionContext.BatchResult result = ctx.awaitCompletion(30, TimeUnit.SECONDS)
                .orElseThrow(() -> new RuntimeException("执行超时"));
            
            long duration = System.currentTimeMillis() - startTime;
            
            output.append(CliLogger.GREEN + "✓ 任务链执行完成!" + CliLogger.RESET).append("\n\n");
            output.append(result.formatReport()).append("\n");
            output.append(String.format("实际耗时: %dms%n", duration));
            output.append(CliLogger.GRAY + "说明: 两个并行链（A->B->C 和 D->E）完成后才执行 F" + CliLogger.RESET).append("\n");
            
        } catch (Exception e) {
            output.append(CliLogger.RED + "执行失败: " + e.getMessage() + CliLogger.RESET).append("\n");
        } finally {
            executor.shutdown();
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 大规模并行演示
     */
    private CommandResult runScaleDemo(int count) {
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║     大规模并行执行演示                                 ║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        if (count > 50) {
            output.append(CliLogger.YELLOW + "⚠ 任务数量过多，限制为 50" + CliLogger.RESET).append("\n");
            count = 50;
        }
        
        ParallelAgentExecutor executor = new ParallelAgentExecutor(registry, null, 8);
        Session session = new Session(UUID.randomUUID().toString(), System.getProperty("user.dir"));
        
        // 创建大量任务
        List<SubAgentTask> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(SubAgentTask.builder()
                .taskId("task-" + i)
                .instruction("任务 " + i + ": 处理数据")
                .agentType("default")
                .priority((int) (Math.random() * 10) + 1)
                .build());
        }
        
        output.append(String.format("创建 %d 个任务，使用 8 线程并行执行...%n%n", count));
        
        long startTime = System.currentTimeMillis();
        
        try {
            ParallelExecutionContext ctx = executor.executeWithDependencies(tasks, session);
            
            // 显示进度
            while (ctx.getStatus() == ParallelExecutionContext.ExecutionStatus.RUNNING) {
                int progress = ctx.getProgress();
                output.append(String.format("\r  进度: [%s] %d%% (%d/%d)", 
                    getProgressBar(progress), progress, 
                    ctx.getSuccessfulResults().size() + ctx.getFailedResults().size(), 
                    count));
                Thread.sleep(50);
            }
            
            ParallelExecutionContext.BatchResult result = ctx.getBatchResult();
            long duration = System.currentTimeMillis() - startTime;
            
            output.append("\n\n");
            output.append(CliLogger.GREEN + "✓ 执行完成!" + CliLogger.RESET).append("\n\n");
            output.append(String.format("总任务: %d%n", result.getTotalTasks()));
            output.append(String.format("成功: %d%n", result.getSuccessfulCount()));
            output.append(String.format("失败: %d%n", result.getFailedCount()));
            output.append(String.format("总耗时: %dms%n", duration));
            output.append(String.format("吞吐量: %.1f 任务/秒%n", (double) count * 1000 / duration));
            
        } catch (Exception e) {
            output.append(CliLogger.RED + "执行失败: " + e.getMessage() + CliLogger.RESET).append("\n");
        } finally {
            executor.shutdown();
        }
        
        return CommandResult.success(output.toString());
    }
    
    private SubAgentTask createTask(String id, String instruction, String agentType, int priority) {
        return SubAgentTask.builder()
            .taskId(id)
            .instruction(instruction)
            .agentType(agentType)
            .priority(priority)
            .build();
    }
    
    private String getProgressBar(int percent) {
        int filled = percent / 5;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        return bar.toString();
    }
}
