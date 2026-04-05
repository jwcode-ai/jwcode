package com.jwcode.core.query;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.model.Message;
import com.jwcode.core.planner.ai.AITaskPlanner;
import com.jwcode.core.planner.ai.AutoAIPlannerTrigger;
import com.jwcode.core.service.ApiClient;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * SmartQueryEngine - 智能查询引擎
 * 
 * 自动判断任务复杂度并选择最佳处理方式：
 * - 简单任务 -> 使用普通 QueryEngine（单 Agent）
 * - 复杂任务 -> 使用 AI Task Planner（多 Agent 并行）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SmartQueryEngine {
    
    private static final Logger log = LoggerFactory.getLogger(SmartQueryEngine.class);
    
    private final QueryEngine queryEngine;
    private final AITaskPlanner aiTaskPlanner;
    private final AutoAIPlannerTrigger autoTrigger;
    private final ToolRegistry toolRegistry;
    private final ApiClient apiClient;
    
    // 当前 Agent 和 Session
    private Agent currentAgent;
    private Session currentSession;
    
    public SmartQueryEngine(QueryEngine queryEngine, AITaskPlanner aiTaskPlanner, 
                           ToolRegistry toolRegistry, ApiClient apiClient) {
        this.queryEngine = queryEngine;
        this.aiTaskPlanner = aiTaskPlanner;
        this.toolRegistry = toolRegistry;
        this.apiClient = apiClient;
        this.autoTrigger = new AutoAIPlannerTrigger(aiTaskPlanner);
        
        // 从 QueryEngine 获取 Session
        this.currentSession = queryEngine.getSession();
    }
    
    /**
     * 设置当前 Agent
     */
    public void setCurrentAgent(Agent agent) {
        this.currentAgent = agent;
    }
    
    /**
     * 智能查询 - 自动选择最佳处理方式
     * 
     * @param prompt 用户输入
     * @return 查询结果
     */
    public CompletableFuture<SmartQueryResult> query(String prompt) {
        log.info("[SmartQueryEngine] 智能处理查询: " + prompt.substring(0, Math.min(50, prompt.length())) + "...");
        
        // 1. 自动分析任务复杂度
        AutoAIPlannerTrigger.TriggerAnalysis analysis = autoTrigger.analyze(prompt, currentSession);
        
        log.info("[SmartQueryEngine] 复杂度分析: " + analysis.getComplexityLevel() + 
            ", 分数=" + analysis.getScore() + ", 使用AI规划=" + analysis.isShouldUseAIPlanner());
        
        // 2. 根据复杂度选择处理方式
        if (analysis.isShouldUseAIPlanner() && currentAgent != null) {
            // 使用 AI 规划模式
            return handleWithAIPlanner(prompt, analysis);
        } else {
            // 使用普通模式
            return handleWithNormalEngine(prompt, analysis);
        }
    }
    
    /**
     * 使用 AI 规划器处理
     */
    private CompletableFuture<SmartQueryResult> handleWithAIPlanner(
            String prompt, 
            AutoAIPlannerTrigger.TriggerAnalysis analysis) {
        
        log.info("[SmartQueryEngine] 使用 AI 规划模式");
        
        // 添加系统提示，告知用户正在使用 AI 规划
        String enhancedPrompt = "[AI 规划模式] " + prompt;
        
        return aiTaskPlanner.planAndExecute(enhancedPrompt, new HashMap<>(), currentAgent, currentSession)
            .thenApply(result -> {
                String response;
                if (result.isSuccess()) {
                    response = result.formatReport();
                } else {
                    response = "AI 规划执行失败: " + result.getErrorMessage();
                }
                
                // 添加 AI 响应到会话
                Message assistantMessage = Message.createAssistantMessage(response);
                currentSession.addMessage(assistantMessage);
                
                return SmartQueryResult.builder()
                    .mode(QueryMode.AI_PLANNER)
                    .triggerAnalysis(analysis)
                    .aiResult(result)
                    .response(response)
                    .success(result.isSuccess())
                    .build();
            })
            .exceptionally(throwable -> {
                // AI 规划失败，回退到普通模式
                log.warn("[SmartQueryEngine] AI 规划失败，回退到普通模式: " + throwable.getMessage());
                return handleWithNormalEngineFallback(prompt, analysis, throwable.getMessage()).join();
            });
    }
    
    /**
     * 使用普通 QueryEngine 处理
     */
    private CompletableFuture<SmartQueryResult> handleWithNormalEngine(
            String prompt,
            AutoAIPlannerTrigger.TriggerAnalysis analysis) {
        
        log.info("[SmartQueryEngine] 使用普通模式");
        
        return queryEngine.query(prompt)
            .thenApply(result -> {
                String response;
                if (result.isSuccess()) {
                    // 从 Message 中提取文本内容
                    response = extractTextFromMessage(result.getMessage());
                } else {
                    response = "错误: " + result.getErrorMessage();
                }
                
                return SmartQueryResult.builder()
                    .mode(QueryMode.NORMAL)
                    .triggerAnalysis(analysis)
                    .queryResult(result)
                    .response(response)
                    .success(result.isSuccess())
                    .build();
            });
    }
    
    /**
     * 普通模式回退
     */
    private CompletableFuture<SmartQueryResult> handleWithNormalEngineFallback(
            String prompt,
            AutoAIPlannerTrigger.TriggerAnalysis analysis,
            String errorMessage) {
        
        return queryEngine.query(prompt)
            .thenApply(result -> {
                String response;
                if (result.isSuccess()) {
                    response = extractTextFromMessage(result.getMessage());
                } else {
                    response = "AI 规划失败（" + errorMessage + "），回退到普通模式也失败: " + result.getErrorMessage();
                }
                
                return SmartQueryResult.builder()
                    .mode(QueryMode.NORMAL_FALLBACK)
                    .triggerAnalysis(analysis)
                    .queryResult(result)
                    .response(response)
                    .success(result.isSuccess())
                    .fallbackReason(errorMessage)
                    .build();
            });
    }
    
    /**
     * 分析但不执行（用于预览）
     */
    public AutoAIPlannerTrigger.TriggerAnalysis analyze(String prompt) {
        return autoTrigger.analyze(prompt, currentSession);
    }
    
    /**
     * 切换自动触发模式
     */
    public boolean toggleAutoTrigger() {
        return autoTrigger.toggleAutoTrigger();
    }
    
    /**
     * 检查自动触发是否启用
     */
    public boolean isAutoTriggerEnabled() {
        return autoTrigger.isAutoTriggerEnabled();
    }
    
    /**
     * 强制使用 AI 规划模式
     */
    public CompletableFuture<SmartQueryResult> forceAIPlanner(String prompt) {
        AutoAIPlannerTrigger.TriggerAnalysis forcedAnalysis = 
            AutoAIPlannerTrigger.TriggerAnalysis.builder()
                .userInput(prompt)
                .complexityLevel(AutoAIPlannerTrigger.ComplexityLevel.HIGH)
                .score(10)
                .shouldUseAIPlanner(true)
                .reasoning("强制使用 AI 规划模式")
                .build();
        
        return handleWithAIPlanner(prompt, forcedAnalysis);
    }
    
    /**
     * 强制使用普通模式
     */
    public CompletableFuture<SmartQueryResult> forceNormalMode(String prompt) {
        AutoAIPlannerTrigger.TriggerAnalysis forcedAnalysis = 
            AutoAIPlannerTrigger.TriggerAnalysis.builder()
                .userInput(prompt)
                .complexityLevel(AutoAIPlannerTrigger.ComplexityLevel.LOW)
                .score(0)
                .shouldUseAIPlanner(false)
                .reasoning("强制使用普通模式")
                .build();
        
        return handleWithNormalEngine(prompt, forcedAnalysis);
    }
    
    /**
     * 从 Message 提取文本
     */
    private String extractTextFromMessage(Message message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : message.getContent()) {
            if (block instanceof Message.TextContent) {
                sb.append(((Message.TextContent) block).getText());
            }
        }
        return sb.toString();
    }
    
    // ==================== 数据类 ====================
    
    public static class SmartQueryResult {
        private QueryMode mode;
        private AutoAIPlannerTrigger.TriggerAnalysis triggerAnalysis;
        private AITaskPlanner.Result aiResult;
        private QueryResult queryResult;
        private String response;
        private boolean success;
        private String fallbackReason;
        
        public QueryMode getMode() { return mode; }
        public AutoAIPlannerTrigger.TriggerAnalysis getTriggerAnalysis() { return triggerAnalysis; }
        public AITaskPlanner.Result getAiResult() { return aiResult; }
        public QueryResult getQueryResult() { return queryResult; }
        public String getResponse() { return response; }
        public boolean isSuccess() { return success; }
        public String getFallbackReason() { return fallbackReason; }
        
        public void setMode(QueryMode mode) { this.mode = mode; }
        public void setTriggerAnalysis(AutoAIPlannerTrigger.TriggerAnalysis triggerAnalysis) { this.triggerAnalysis = triggerAnalysis; }
        public void setAiResult(AITaskPlanner.Result aiResult) { this.aiResult = aiResult; }
        public void setQueryResult(QueryResult queryResult) { this.queryResult = queryResult; }
        public void setResponse(String response) { this.response = response; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
        
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            
            sb.append("╔══════════════════════════════════════════════════════════╗\n");
            sb.append("║              🤖 智能查询结果                             ║\n");
            sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
            
            sb.append("执行模式: ").append(mode).append("\n");
            sb.append("复杂度: ").append(triggerAnalysis.getComplexityLevel()).append("\n");
            sb.append("分数: ").append(triggerAnalysis.getScore()).append("/10\n\n");
            
            if (fallbackReason != null) {
                sb.append("⚠️ 回退原因: ").append(fallbackReason).append("\n\n");
            }
            
            sb.append("响应:\n").append(response).append("\n");
            
            return sb.toString();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private QueryMode mode;
            private AutoAIPlannerTrigger.TriggerAnalysis triggerAnalysis;
            private AITaskPlanner.Result aiResult;
            private QueryResult queryResult;
            private String response;
            private boolean success;
            private String fallbackReason;
            
            public Builder mode(QueryMode v) { this.mode = v; return this; }
            public Builder triggerAnalysis(AutoAIPlannerTrigger.TriggerAnalysis v) { this.triggerAnalysis = v; return this; }
            public Builder aiResult(AITaskPlanner.Result v) { this.aiResult = v; return this; }
            public Builder queryResult(QueryResult v) { this.queryResult = v; return this; }
            public Builder response(String v) { this.response = v; return this; }
            public Builder success(boolean v) { this.success = v; return this; }
            public Builder fallbackReason(String v) { this.fallbackReason = v; return this; }
            
            public SmartQueryResult build() {
                SmartQueryResult r = new SmartQueryResult();
                r.mode = this.mode;
                r.triggerAnalysis = this.triggerAnalysis;
                r.aiResult = this.aiResult;
                r.queryResult = this.queryResult;
                r.response = this.response;
                r.success = this.success;
                r.fallbackReason = this.fallbackReason;
                return r;
            }
        }
    }
    
    public enum QueryMode {
        NORMAL,          // 普通单 Agent 模式
        AI_PLANNER,      // AI 规划模式
        NORMAL_FALLBACK  // 普通模式（AI 失败回退）
    }
    
    /**
     * 创建 SmartQueryEngine 的构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private QueryEngine queryEngine;
        private AITaskPlanner aiTaskPlanner;
        private ToolRegistry toolRegistry;
        private ApiClient apiClient;
        private Agent currentAgent;
        
        public Builder queryEngine(QueryEngine queryEngine) {
            this.queryEngine = queryEngine;
            return this;
        }
        
        public Builder aiTaskPlanner(AITaskPlanner aiTaskPlanner) {
            this.aiTaskPlanner = aiTaskPlanner;
            return this;
        }
        
        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }
        
        public Builder apiClient(ApiClient apiClient) {
            this.apiClient = apiClient;
            return this;
        }
        
        public Builder currentAgent(Agent currentAgent) {
            this.currentAgent = currentAgent;
            return this;
        }
        
        public SmartQueryEngine build() {
            if (queryEngine == null) {
                throw new IllegalStateException("QueryEngine is required");
            }
            if (aiTaskPlanner == null) {
                if (toolRegistry == null || apiClient == null) {
                    throw new IllegalStateException("Either aiTaskPlanner or (toolRegistry + apiClient) is required");
                }
                aiTaskPlanner = new AITaskPlanner(apiClient, toolRegistry);
            }
            
            SmartQueryEngine engine = new SmartQueryEngine(queryEngine, aiTaskPlanner, 
                toolRegistry, apiClient);
            if (currentAgent != null) {
                engine.setCurrentAgent(currentAgent);
            }
            return engine;
        }
    }
}
