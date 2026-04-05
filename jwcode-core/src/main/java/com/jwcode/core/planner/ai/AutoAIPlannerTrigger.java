package com.jwcode.core.planner.ai;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.session.Session;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * AutoAIPlannerTrigger - AI 规划自动触发器
 * 
 * 自动检测用户输入的任务复杂度，决定是否使用 AI 规划：
 * - 复杂任务 -> 自动使用 AI 规划
 * - 简单任务 -> 使用普通单 Agent 模式
 * 
 * 检测维度：
 * 1. 关键词匹配（refactor, implement, migrate 等）
 * 2. 描述长度
 * 3. 涉及文件数量
 * 4. 操作类型复杂度
 * 5. 上下文复杂度
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Slf4j
public class AutoAIPlannerTrigger {
    
    private final AITaskPlanner aiTaskPlanner;
    private final TriggerConfig config;
    
    // 复杂度关键词
    private static final List<Pattern> HIGH_COMPLEXITY_PATTERNS = Arrays.asList(
        Pattern.compile("\\b(refactor|重构|重写|rewrite)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(migrate|迁移|升级|upgrade)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(implement|实现|开发).*\\b(feature|功能|模块)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(architecture|架构|design|设计)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(optimize|优化|性能|performance)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(security|安全|auth|认证|权限)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(multiple|多个|all|所有|batch|批量)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(project|项目|system|系统|framework|框架)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(integrate|集成|connect|连接|api)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(test|测试).*\\b(coverage|覆盖|suite|套件)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    private static final List<Pattern> MEDIUM_COMPLEXITY_PATTERNS = Arrays.asList(
        Pattern.compile("\\b(add|添加|create|创建).*\\b(class|类|method|方法|function|函数)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(fix|修复|bug|debug|调试)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(update|更新|modify|修改)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(document|文档|comment|注释)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(error|错误|exception|异常|handle|处理)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    // 简单任务关键词
    private static final List<Pattern> SIMPLE_PATTERNS = Arrays.asList(
        Pattern.compile("^(hi|hello|hey|你好|您好).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\b(help|帮助|question|问题|what|什么|how|如何)\\b.*\\?", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\b(thanks|谢谢|thank you)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(explain|解释|说明).*", Pattern.CASE_INSENSITIVE)
    );
    
    public AutoAIPlannerTrigger(AITaskPlanner aiTaskPlanner) {
        this(aiTaskPlanner, TriggerConfig.defaultConfig());
    }
    
    public AutoAIPlannerTrigger(AITaskPlanner aiTaskPlanner, TriggerConfig config) {
        this.aiTaskPlanner = aiTaskPlanner;
        this.config = config;
    }
    
    /**
     * 分析用户输入，判断是否使用 AI 规划
     * 
     * @param userInput 用户输入
     * @param context 会话上下文
     * @return 分析结果
     */
    public TriggerAnalysis analyze(String userInput, Session context) {
        log.info("[AutoAIPlannerTrigger] 分析任务复杂度: " + userInput.substring(0, Math.min(50, userInput.length())) + "...");
        
        long startTime = System.currentTimeMillis();
        
        // 1. 快速规则检测
        ComplexityLevel ruleBasedLevel = quickAnalyze(userInput);
        
        // 2. 如果规则检测为简单任务，直接返回
        if (ruleBasedLevel == ComplexityLevel.SIMPLE) {
            return TriggerAnalysis.builder()
                .userInput(userInput)
                .complexityLevel(ComplexityLevel.SIMPLE)
                .score(0)
                .shouldUseAIPlanner(false)
                .reasoning("简单任务（问候/问答/解释）")
                .analysisTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
        
        // 3. 计算复杂度分数
        int score = calculateComplexityScore(userInput, context);
        
        // 4. 根据分数确定复杂度等级
        ComplexityLevel level = determineComplexityLevel(score);
        
        // 5. 判断是否使用 AI 规划
        boolean shouldUseAI = shouldUseAIPlanner(level, score);
        
        // 6. 生成推理说明
        String reasoning = generateReasoning(level, score, userInput);
        
        TriggerAnalysis analysis = TriggerAnalysis.builder()
            .userInput(userInput)
            .complexityLevel(level)
            .score(score)
            .shouldUseAIPlanner(shouldUseAI)
            .threshold(config.getThreshold())
            .reasoning(reasoning)
            .analysisTimeMs(System.currentTimeMillis() - startTime)
            .build();
        
        log.info("[AutoAIPlannerTrigger] 分析完成: 复杂度=" + level + ", 分数=" + score + 
            ", 使用AI规划=" + shouldUseAI);
        
        return analysis;
    }
    
    /**
     * 快速规则分析
     */
    private ComplexityLevel quickAnalyze(String userInput) {
        String lower = userInput.toLowerCase();
        
        // 检查简单模式
        for (Pattern pattern : SIMPLE_PATTERNS) {
            if (pattern.matcher(userInput).matches()) {
                return ComplexityLevel.SIMPLE;
            }
        }
        
        // 检查高复杂度模式
        int highMatches = 0;
        for (Pattern pattern : HIGH_COMPLEXITY_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                highMatches++;
            }
        }
        
        if (highMatches >= 2) {
            return ComplexityLevel.HIGH;
        }
        if (highMatches == 1) {
            return ComplexityLevel.MEDIUM;
        }
        
        // 检查中等复杂度模式
        int mediumMatches = 0;
        for (Pattern pattern : MEDIUM_COMPLEXITY_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                mediumMatches++;
            }
        }
        
        if (mediumMatches >= 2) {
            return ComplexityLevel.MEDIUM;
        }
        
        // 默认中等
        return ComplexityLevel.MEDIUM;
    }
    
    /**
     * 计算复杂度分数
     */
    private int calculateComplexityScore(String userInput, Session context) {
        int score = 0;
        String lower = userInput.toLowerCase();
        
        // 1. 关键词加分
        for (Pattern pattern : HIGH_COMPLEXITY_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                score += 2;
            }
        }
        
        for (Pattern pattern : MEDIUM_COMPLEXITY_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                score += 1;
            }
        }
        
        // 2. 描述长度加分
        int length = userInput.length();
        if (length > 200) score += 3;
        else if (length > 100) score += 2;
        else if (length > 50) score += 1;
        
        // 3. 文件引用加分
        int fileRefs = countFileReferences(userInput);
        if (fileRefs > 5) score += 3;
        else if (fileRefs > 2) score += 2;
        else if (fileRefs > 0) score += 1;
        
        // 4. 步骤暗示加分
        int steps = countStepIndicators(userInput);
        score += steps;
        
        // 5. 上下文复杂度
        if (context != null) {
            int messageCount = context.getMessageCount();
            if (messageCount > 20) score += 2;
            else if (messageCount > 10) score += 1;
        }
        
        return score;
    }
    
    /**
     * 确定复杂度等级
     */
    private ComplexityLevel determineComplexityLevel(int score) {
        if (score >= config.getHighThreshold()) return ComplexityLevel.HIGH;
        if (score >= config.getMediumThreshold()) return ComplexityLevel.MEDIUM;
        return ComplexityLevel.LOW;
    }
    
    /**
     * 判断是否使用 AI 规划
     */
    private boolean shouldUseAIPlanner(ComplexityLevel level, int score) {
        switch (level) {
            case HIGH:
                return true;
            case MEDIUM:
                return score >= config.getThreshold();
            case LOW:
            case SIMPLE:
            default:
                return false;
        }
    }
    
    /**
     * 生成推理说明
     */
    private String generateReasoning(ComplexityLevel level, int score, String userInput) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("复杂度评分: ").append(score).append("分\n");
        sb.append("复杂度等级: ").append(level).append("\n");
        sb.append("原因:\n");
        
        // 关键词匹配
        String lower = userInput.toLowerCase();
        List<String> matchedKeywords = new ArrayList<>();
        
        for (Pattern pattern : HIGH_COMPLEXITY_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                matchedKeywords.add("高复杂度操作");
                break;
            }
        }
        
        int fileRefs = countFileReferences(userInput);
        if (fileRefs > 0) {
            matchedKeywords.add("涉及 " + fileRefs + " 个文件");
        }
        
        if (userInput.length() > 100) {
            matchedKeywords.add("详细描述（" + userInput.length() + " 字符）");
        }
        
        int steps = countStepIndicators(userInput);
        if (steps > 0) {
            matchedKeywords.add("暗示多步骤（" + steps + " 个）");
        }
        
        for (String reason : matchedKeywords) {
            sb.append("  • ").append(reason).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 统计文件引用数量
     */
    private int countFileReferences(String text) {
        Pattern filePattern = Pattern.compile("[\\w/\\\\]+\\.(java|py|js|ts|go|rs|cpp|c|h|xml|json|yaml|yml)");
        int count = 0;
        var matcher = filePattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
    
    /**
     * 统计步骤暗示
     */
    private int countStepIndicators(String text) {
        int count = 0;
        String[] indicators = {"first", "second", "third", "then", "next", "after", "finally", 
            "第一步", "第二步", "然后", "接着", "最后", "firstly", "secondly"};
        String lower = text.toLowerCase();
        for (String indicator : indicators) {
            if (lower.contains(indicator)) {
                count++;
            }
        }
        return Math.min(count, 3); // 最多计 3 分
    }
    
    /**
     * 智能处理用户输入
     * 
     * @param userInput 用户输入
     * @param parentAgent 父 Agent
     * @param parentSession 父 Session
     * @return 处理结果
     */
    public CompletableFuture<SmartProcessResult> smartProcess(
            String userInput, 
            Agent parentAgent, 
            Session parentSession) {
        
        // 1. 分析任务复杂度
        TriggerAnalysis analysis = analyze(userInput, parentSession);
        
        // 2. 根据复杂度选择处理方式
        if (analysis.isShouldUseAIPlanner()) {
            log.info("[AutoAIPlannerTrigger] 使用 AI 规划模式处理任务");
            
            // 使用 AI 规划
            return aiTaskPlanner.planAndExecute(userInput, new HashMap<>(), parentAgent, parentSession)
                .thenApply(result -> SmartProcessResult.builder()
                    .mode(ProcessMode.AI_PLANNER)
                    .triggerAnalysis(analysis)
                    .aiResult(result)
                    .success(result.isSuccess())
                    .build());
        } else {
            log.info("[AutoAIPlannerTrigger] 使用普通模式处理任务");
            
            // 返回普通模式建议
            return CompletableFuture.completedFuture(SmartProcessResult.builder()
                .mode(ProcessMode.NORMAL)
                .triggerAnalysis(analysis)
                .success(true)
                .build());
        }
    }
    
    /**
     * 切换自动触发模式
     */
    public boolean toggleAutoTrigger() {
        config.setAutoTriggerEnabled(!config.isAutoTriggerEnabled());
        log.info("[AutoAIPlannerTrigger] 自动触发模式: " + 
            (config.isAutoTriggerEnabled() ? "开启" : "关闭"));
        return config.isAutoTriggerEnabled();
    }
    
    /**
     * 检查是否启用自动触发
     */
    public boolean isAutoTriggerEnabled() {
        return config.isAutoTriggerEnabled();
    }
    
    // ==================== 数据类 ====================
    
    @Data
    @Builder
    public static class TriggerConfig {
        private int threshold;          // 触发阈值（默认 5）
        private int mediumThreshold;    // 中等复杂度阈值（默认 3）
        private int highThreshold;      // 高复杂度阈值（默认 7）
        private boolean autoTriggerEnabled; // 是否启用自动触发
        
        public static TriggerConfig defaultConfig() {
            return TriggerConfig.builder()
                .threshold(5)
                .mediumThreshold(3)
                .highThreshold(7)
                .autoTriggerEnabled(true)
                .build();
        }
    }
    
    @Data
    @Builder
    public static class TriggerAnalysis {
        private String userInput;
        private ComplexityLevel complexityLevel;
        private int score;
        private boolean shouldUseAIPlanner;
        private int threshold;
        private String reasoning;
        private long analysisTimeMs;
        
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("╔══════════════════════════════════════════════════════════╗\n");
            sb.append("║              🤖 自动复杂度分析                           ║\n");
            sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
            
            sb.append("任务: ").append(userInput.substring(0, Math.min(50, userInput.length()))).append("...\n\n");
            sb.append("复杂度: ").append(complexityLevel).append(" (").append(score).append("/10)\n");
            sb.append("阈值: ").append(threshold).append("\n");
            sb.append("建议模式: ").append(shouldUseAIPlanner ? "AI 规划" : "普通模式").append("\n\n");
            sb.append(reasoning);
            sb.append("\n分析耗时: ").append(analysisTimeMs).append("ms");
            
            return sb.toString();
        }
    }
    
    @Data
    @Builder
    public static class SmartProcessResult {
        private ProcessMode mode;
        private TriggerAnalysis triggerAnalysis;
        private AITaskPlanner.Result aiResult;
        private boolean success;
        private String errorMessage;
    }
    
    public enum ComplexityLevel {
        SIMPLE,     // 简单（问候、问答）
        LOW,        // 低复杂度
        MEDIUM,     // 中等复杂度
        HIGH        // 高复杂度
    }
    
    public enum ProcessMode {
        NORMAL,     // 普通单 Agent 模式
        AI_PLANNER  // AI 规划模式
    }
}
