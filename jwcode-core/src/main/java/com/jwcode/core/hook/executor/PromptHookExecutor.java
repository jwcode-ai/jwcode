package com.jwcode.core.hook.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hook.*;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PromptHookExecutor — 基于 LLM 单轮 Prompt 的 Hook 执行器。
 *
 * <p>构造单轮 Prompt 发送给 LLM 做 Yes/No 判断，由 AI 做动态风险评估。
 * 这是 Hook 体系中最具创新性的设计——策略不必写死为正则规则。</p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>根据 {@code promptTemplate} 和 {@link HookContext} 构造 Prompt</li>
 *   <li>通过 {@link LlmCallback} 发送给 LLM</li>
 *   <li>LLM 返回结构化决策（ALLOW/DENY + 原因）</li>
 *   <li>解析并返回 {@link HookResult}</li>
 * </ol>
 *
 * <h3>Prompt 模板变量</h3>
 * <table>
 *   <tr><td>{@code {{eventType}}}</td><td>事件类型</td></tr>
 *   <tr><td>{@code {{toolName}}}</td><td>工具名称</td></tr>
 *   <tr><td>{@code {{toolInput}}}</td><td>工具输入（JSON）</td></tr>
 *   <tr><td>{@code {{sessionId}}}</td><td>会话 ID</td></tr>
 *   <tr><td>{@code {{fromState}}}</td><td>转换前状态</td></tr>
 *   <tr><td>{@code {{toState}}}</td><td>转换后状态</td></tr>
 * </table>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class PromptHookExecutor implements HookExecutor {

    private static final Logger logger = Logger.getLogger(PromptHookExecutor.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String promptTemplate;
    private final HookPriority priority;
    private final long timeoutMs;
    private final boolean failOpen;
    private final boolean enabled;
    private final LlmCallback llmCallback;

    private static final String DEFAULT_PROMPT_TEMPLATE = """
        You are a security auditor for JWCode. Evaluate the following tool call and decide whether it should be allowed.

        Event: {{eventType}}
        Tool: {{toolName}}
        Input: {{toolInput}}
        Context: {{executionContext}}

        Respond with ONLY a valid JSON object:
        {
          "decision": "ALLOW or DENY",
          "reason": "Brief reason for your decision"
        }

        Security rules:
        - Reject any command that could kill system processes or delete critical files
        - Reject file writes outside the workspace
        - Reject any tool call that appears malicious or destructive
        - Allow legitimate development operations
        """;

    /**
     * LLM 调用回调接口（由外部注入，避免循环依赖）。
     */
    @FunctionalInterface
    public interface LlmCallback {
        /**
         * 向 LLM 发送 Prompt 并获取响应文本。
         *
         * @param prompt  Prompt 文本
         * @param timeoutMs 超时（毫秒）
         * @return LLM 响应文本
         */
        CompletableFuture<String> query(String prompt, long timeoutMs);
    }

    public PromptHookExecutor(String name, String promptTemplate, LlmCallback llmCallback) {
        this(name, promptTemplate, llmCallback, HookPriority.USER, 15_000, true, true);
    }

    public PromptHookExecutor(String name, String promptTemplate, LlmCallback llmCallback,
                               HookPriority priority, long timeoutMs,
                               boolean failOpen, boolean enabled) {
        this.name = name;
        this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.llmCallback = llmCallback;
        this.priority = priority;
        this.timeoutMs = timeoutMs;
        this.failOpen = failOpen;
        this.enabled = enabled;
    }

    public static PromptHookExecutor fromConfig(HookConfig config, LlmCallback llmCallback) {
        return new PromptHookExecutor(
            config.getName(),
            config.getPromptTemplate(),
            llmCallback,
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    @Override
    public CompletableFuture<HookResult> execute(HookContext context) {
        if (llmCallback == null) {
            logger.warning("[PromptHook] " + name + " has no LlmCallback, defaulting to ALLOW");
            return CompletableFuture.completedFuture(
                HookResult.allow(name, "No LLM callback configured"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 构造 Prompt
                String prompt = buildPrompt(context);

                // 2. 调用 LLM
                String response = llmCallback.query(prompt, timeoutMs)
                    .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

                // 3. 解析响应
                return parseLlmResponse(response);

            } catch (java.util.concurrent.TimeoutException e) {
                logger.warning("[PromptHook] " + name + " timed out after " + timeoutMs + "ms");
                return failOpen
                    ? HookResult.timeout(name)
                    : HookResult.deny(name, "Prompt LLM timed out (fail-closed)");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[PromptHook] " + name + " LLM query failed", e);
                return failOpen
                    ? HookResult.error(name, e.getMessage())
                    : HookResult.errorFailClosed(name, e.getMessage());
            }
        });
    }

    /**
     * 构造 Prompt（替换模板变量）。
     */
    private String buildPrompt(HookContext context) {
        String prompt = promptTemplate;
        prompt = prompt.replace("{{eventType}}", context.getEventType().name());
        prompt = prompt.replace("{{toolName}}", nvl(context.getToolName()));
        prompt = prompt.replace("{{toolInput}}", context.getToolInput() != null
            ? context.getToolInput().toString() : "null");
        prompt = prompt.replace("{{sessionId}}", nvl(context.getSessionId()));
        prompt = prompt.replace("{{fromState}}", nvl(context.getFromState()));
        prompt = prompt.replace("{{toState}}", nvl(context.getToState()));
        prompt = prompt.replace("{{executionContext}}", context.getExecutionContext() != null
            ? context.getExecutionContext().toString() : "null");
        return prompt;
    }

    private String nvl(String s) { return s != null ? s : "N/A"; }

    /**
     * 解析 LLM 响应为 HookResult。
     */
    private HookResult parseLlmResponse(String response) {
        try {
            // LLM 可能返回带 markdown 包裹的 JSON，尝试提取
            String json = extractJson(response);
            JsonNode root = MAPPER.readTree(json);

            String decisionStr = root.has("decision")
                ? root.get("decision").asText().toUpperCase().trim()
                : "ALLOW";

            HookDecision decision;
            try {
                decision = HookDecision.valueOf(decisionStr);
            } catch (IllegalArgumentException e) {
                logger.warning("[PromptHook] " + name + " unknown LLM decision: " + decisionStr);
                decision = HookDecision.ALLOW;
            }

            String reason = root.has("reason") ? root.get("reason").asText() : "AI assessment";
            return new HookResult.Builder(decision, name).reason(reason).build();

        } catch (Exception e) {
            logger.warning("[PromptHook] " + name + " failed to parse LLM response: " + response);
            return HookResult.allow(name, "Failed to parse AI response, defaulting to ALLOW");
        }
    }

    /**
     * 从 LLM 响应中提取 JSON（处理 markdown 包裹）。
     */
    private String extractJson(String response) {
        String trimmed = response.trim();
        // 移除 markdown 代码块标记
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        // 查找 JSON 对象
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return trimmed;
    }

    @Override
    public HookImplementationType getType() { return HookImplementationType.PROMPT; }

    @Override
    public String getName() { return name; }

    @Override
    public HookPriority getPriority() { return priority; }

    @Override
    public long getTimeoutMs() { return timeoutMs; }

    @Override
    public boolean isFailOpen() { return failOpen; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() {
        return String.format("PromptHookExecutor{name='%s', priority=%s}", name, priority);
    }
}
