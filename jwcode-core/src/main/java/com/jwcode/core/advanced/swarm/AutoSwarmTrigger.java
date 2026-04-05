package com.jwcode.core.advanced.swarm;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Auto Swarm Trigger - 自动触发 Agent Swarm
 * 
 * 自动检测复杂任务并触发 Agent Swarm 执行
 * 无需手动输入 advanced swarm 命令
 */
public class AutoSwarmTrigger {
    
    // 复杂任务特征模式
    private static final List<TaskPattern> COMPLEX_TASK_PATTERNS = List.of(
        new TaskPattern("refactor.*", "重构任务通常涉及多个文件，适合使用 Swarm"),
        new TaskPattern("implement.*feature.*", "功能开发需要多步骤协作"),
        new TaskPattern("migrate.*", "迁移任务通常需要批量处理"),
        new TaskPattern("optimize.*performance.*", "性能优化需要多维度分析"),
        new TaskPattern("add.*support.*", "添加支持通常需要修改多处"),
        new TaskPattern("update.*all.*", "批量更新适合并行处理"),
        new TaskPattern("analyze.*project.*", "项目分析需要全面扫描"),
        new TaskPattern("fix.*all.*bugs.*", "批量修复 Bug 可以并行"),
        new TaskPattern("create.*tests.*", "生成测试可以按模块并行")
    );
    
    // 任务复杂度阈值
    private static final int COMPLEXITY_THRESHOLD = 3;
    
    private final AgentSwarm agentSwarm;
    private boolean autoSwarmEnabled = false;
    
    public AutoSwarmTrigger(AgentSwarm agentSwarm) {
        this.agentSwarm = agentSwarm;
    }
    
    /**
     * 分析任务并决定是否使用 Swarm
     */
    public TaskAnalysis analyzeTask(String userInput) {
        int complexity = calculateComplexity(userInput);
        boolean shouldUseSwarm = complexity >= COMPLEXITY_THRESHOLD;
        String reason = shouldUseSwarm ? generateReason(userInput) : "简单任务，单 Agent 处理即可";
        
        return new TaskAnalysis(userInput, complexity, shouldUseSwarm, reason);
    }
    
    /**
     * 自动执行（如果任务复杂则使用 Swarm）
     */
    public Object autoExecute(String userInput, Object context) {
        TaskAnalysis analysis = analyzeTask(userInput);
        
        if (analysis.isShouldUseSwarm() && autoSwarmEnabled) {
            System.out.println("[AutoSwarm] 检测到复杂任务，自动使用 Agent Swarm");
            System.out.println("[AutoSwarm] 原因: " + analysis.getReason());
            
            return agentSwarm.executeComplexTask(userInput, context);
        } else {
            System.out.println("[AutoSwarm] 使用普通模式处理");
            // 普通单 Agent 执行
            return executeNormal(userInput, context);
        }
    }
    
    /**
     * 计算任务复杂度
     */
    private int calculateComplexity(String input) {
        int score = 0;
        String lowerInput = input.toLowerCase();
        
        // 1. 基于关键词评分
        for (TaskPattern pattern : COMPLEX_TASK_PATTERNS) {
            if (pattern.matches(lowerInput)) {
                score += 2;
            }
        }
        
        // 2. 任务范围评分
        if (lowerInput.contains("all") || lowerInput.contains("所有")) score += 2;
        if (lowerInput.contains("multiple") || lowerInput.contains("多个")) score += 2;
        if (lowerInput.contains("project") || lowerInput.contains("项目")) score += 1;
        
        // 3. 动作复杂度评分
        if (lowerInput.contains("refactor") || lowerInput.contains("重构")) score += 2;
        if (lowerInput.contains("migrate") || lowerInput.contains("迁移")) score += 2;
        if (lowerInput.contains("implement") || lowerInput.contains("实现")) score += 1;
        if (lowerInput.contains("analyze") || lowerInput.contains("分析")) score += 1;
        
        // 4. 长度评分（长描述通常更复杂）
        if (input.length() > 100) score += 1;
        
        return score;
    }
    
    /**
     * 生成使用 Swarm 的原因
     */
    private String generateReason(String input) {
        StringBuilder reasons = new StringBuilder();
        String lowerInput = input.toLowerCase();
        
        for (TaskPattern pattern : COMPLEX_TASK_PATTERNS) {
            if (pattern.matches(lowerInput)) {
                reasons.append(pattern.getReason()).append("; ");
            }
        }
        
        return reasons.length() > 0 ? 
            reasons.substring(0, reasons.length() - 2) : 
            "任务涉及多个步骤，适合并行处理";
    }
    
    /**
     * 普通执行（单 Agent）
     */
    private Object executeNormal(String userInput, Object context) {
        // 模拟单 Agent 执行
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "单 Agent 执行结果: " + userInput;
    }
    
    /**
     * 开启/关闭自动 Swarm
     */
    public boolean toggleAutoSwarm() {
        autoSwarmEnabled = !autoSwarmEnabled;
        System.out.println("[AutoSwarm] 自动 Swarm: " + (autoSwarmEnabled ? "开启" : "关闭"));
        return autoSwarmEnabled;
    }
    
    public boolean isAutoSwarmEnabled() {
        return autoSwarmEnabled;
    }
    
    // ==================== 内部类 ====================
    
    public static class TaskAnalysis {
        private final String input;
        private final int complexity;
        private final boolean shouldUseSwarm;
        private final String reason;
        
        public TaskAnalysis(String input, int complexity, boolean shouldUseSwarm, String reason) {
            this.input = input;
            this.complexity = complexity;
            this.shouldUseSwarm = shouldUseSwarm;
            this.reason = reason;
        }
        
        public String getInput() { return input; }
        public int getComplexity() { return complexity; }
        public boolean isShouldUseSwarm() { return shouldUseSwarm; }
        public String getReason() { return reason; }
        
        public String formatReport() {
            return String.format(
                "任务分析:\n输入: %s\n复杂度: %d/10\n建议使用 Swarm: %s\n原因: %s",
                input.substring(0, Math.min(50, input.length())),
                complexity,
                shouldUseSwarm ? "是" : "否",
                reason
            );
        }
    }
    
    private static class TaskPattern {
        private final Pattern pattern;
        private final String reason;
        
        TaskPattern(String regex, String reason) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.reason = reason;
        }
        
        boolean matches(String input) {
            return pattern.matcher(input).find();
        }
        
        String getReason() {
            return reason;
        }
    }
}
