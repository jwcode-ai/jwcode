package com.jwcode.core.skill.executor;

import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.skill.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 默认技能执行器 - 使用 LLM 服务执行技能
 */
public class DefaultSkillExecutor implements SkillExecutor {
    
    private final LLMService llmService;
    private final SkillExecutorConfig config;
    private final SkillTemplateResolver templateResolver;

    public DefaultSkillExecutor(LLMService llmService) {
        this(llmService, SkillExecutorConfig.defaultConfig());
    }

    public DefaultSkillExecutor(LLMService llmService, SkillExecutorConfig config) {
        this.llmService = llmService;
        this.config = config;
        this.templateResolver = new SkillTemplateResolver();
    }

    /**
     * 获取模板变量解析器，可用于设置会话 ID、项目根目录等。
     */
    public SkillTemplateResolver getTemplateResolver() {
        return templateResolver;
    }
    
    @Override
    public CompletableFuture<SkillResult> execute(Skill skill, SkillContext context) {
        try {
            // 构建消息列表
            List<LLMMessage> messages = buildMessages(skill, context);
            
            // 调用 LLM
            return llmService.chat(messages)
                .thenApply(llmResponse -> processLLMResponse(llmResponse, skill, context))
                .exceptionally(ex -> {
                    return SkillResult.builder()
                        .success(false)
                        .output("")
                        .error("技能执行失败: " + ex.getMessage())
                        .build();
                });
                
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                SkillResult.builder()
                    .success(false)
                    .output("")
                    .error("技能执行异常: " + e.getMessage())
                    .build()
            );
        }
    }
    
    /**
     * 构建 LLM 消息列表
     */
    private List<LLMMessage> buildMessages(Skill skill, SkillContext context) {
        List<LLMMessage> messages = new ArrayList<>();
        
        // 1. 系统提示词
        String systemPrompt = buildSystemPrompt(skill);
        messages.add(LLMMessage.system(systemPrompt));
        
        // 2. 用户输入
        String userInput = context.getInput();
        if (userInput == null || userInput.isEmpty()) {
            userInput = "请执行技能任务";
        }
        messages.add(LLMMessage.user(userInput));
        
        return messages;
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(Skill skill) {
        StringBuilder prompt = new StringBuilder();
        String source = skill.getSource();

        // 技能描述（支持模板变量）
        if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
            prompt.append("# 任务描述\n")
                  .append(templateResolver.resolve(skill.getDescription(), source))
                  .append("\n\n");
        }

        // 系统提示词（支持模板变量）
        if (skill.getSystemPrompt() != null && !skill.getSystemPrompt().isEmpty()) {
            prompt.append("# 执行指南\n")
                  .append(templateResolver.resolve(skill.getSystemPrompt(), source))
                  .append("\n\n");
        }
        
        // 输出格式要求
        prompt.append("# 输出要求\n");
        prompt.append("请直接输出结果，不要添加额外的解释性文字。\n");
        prompt.append("保持输出简洁、专业、可执行。\n");
        
        // 技能特定提示
        if (skill.getTags() != null && skill.getTags().contains("code")) {
            prompt.append("\n代码要求：\n");
            prompt.append("- 使用标准格式\n");
            prompt.append("- 添加必要的注释\n");
            prompt.append("- 遵循最佳实践\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * 处理 LLM 响应
     */
    private SkillResult processLLMResponse(LLMResponse llmResponse, Skill skill, SkillContext context) {
        if (llmResponse.hasError()) {
            return SkillResult.builder()
                .success(false)
                .output("")
                .error(llmResponse.getErrorMessage())
                .build();
        }
        
        // 使用 TokenUsage 记录 token 使用情况
        SkillResult.TokenUsage tokenUsage = null;
        if (llmResponse.getPromptTokens() > 0 || llmResponse.getCompletionTokens() > 0) {
            tokenUsage = SkillResult.TokenUsage.builder()
                .promptTokens(llmResponse.getPromptTokens())
                .completionTokens(llmResponse.getCompletionTokens())
                .totalTokens(llmResponse.getTotalTokens())
                .build();
        }
        
        return SkillResult.builder()
            .success(true)
            .output(llmResponse.getContent())
            .tokenUsage(tokenUsage)
            .build();
    }
    
    /**
     * 执行器配置
     */
    public static class SkillExecutorConfig {
        private boolean streaming = false;
        private int maxTokens = 4096;
        private double temperature = 0.7;
        
        public static SkillExecutorConfig defaultConfig() {
            return new SkillExecutorConfig();
        }
        
        public SkillExecutorConfig streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }
        
        public SkillExecutorConfig maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        public SkillExecutorConfig temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        // Getters
        public boolean isStreaming() { return streaming; }
        public int getMaxTokens() { return maxTokens; }
        public double getTemperature() { return temperature; }
    }
}
