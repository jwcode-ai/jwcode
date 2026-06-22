package com.jwcode.core.hook.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hook.*;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AgentHookExecutor — 基于子 Agent 深度调查的 Hook 执行器。
 *
 * <p>启动一个仅带 Read/Grep/Glob 工具的子 Agent，对当前上下文做深度分析，
 * 然后返回结构化决策。适用于复杂安全审查场景（如代码注入模式检测）。</p>
 *
 * <h3>子 Agent 限制</h3>
 * <ul>
 *   <li>仅授予 <b>只读</b> 工具（Read/Grep/Glob/SemanticSearch）</li>
 *   <li>不得修改任何文件</li>
 *   <li>超时上限 60s，超时后 fallback 到 ALLOW</li>
 * </ul>
 *
 * <h3>调查任务示例</h3>
 * <pre>{@code
 * "检查以下文件操作是否安全：{toolName} 正在操作 {filePath}。
 *  分析是否涉及敏感配置文件、是否存在路径穿越风险、
 *  操作范围是否在工作区内。返回 {decision, reason} JSON。"
 * }</pre>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class AgentHookExecutor implements HookExecutor {

    private static final Logger logger = Logger.getLogger(AgentHookExecutor.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String agentName;
    private final HookPriority priority;
    private final long timeoutMs;
    private final boolean failOpen;
    private final boolean enabled;
    private final AgentCallback agentCallback;

    /**
     * 子 Agent 调用回调接口（由外部注入，避免循环依赖）。
     */
    @FunctionalInterface
    public interface AgentCallback {
        /**
         * 启动子 Agent 执行调查任务。
         *
         * @param investigationPrompt 调查任务描述
         * @param timeoutMs           超时（毫秒）
         * @return 子 Agent 的结构化调查结论（JSON 字符串）
         */
        CompletableFuture<String> investigate(String investigationPrompt, long timeoutMs);
    }

    public AgentHookExecutor(String name, String agentName, AgentCallback agentCallback) {
        this(name, agentName, agentCallback, HookPriority.SECURITY, 60_000, true, true);
    }

    public AgentHookExecutor(String name, String agentName, AgentCallback agentCallback,
                              HookPriority priority, long timeoutMs,
                              boolean failOpen, boolean enabled) {
        this.name = name;
        this.agentName = agentName != null ? agentName : "explorer";
        this.agentCallback = agentCallback;
        this.priority = priority;
        this.timeoutMs = timeoutMs;
        this.failOpen = failOpen;
        this.enabled = enabled;
    }

    public static AgentHookExecutor fromConfig(HookConfig config, AgentCallback agentCallback) {
        return new AgentHookExecutor(
            config.getName(),
            config.getAgentName(),
            agentCallback,
            config.getPriority(),
            config.getTimeoutMs(),
            config.isFailOpen(),
            config.isEnabled()
        );
    }

    @Override
    public CompletableFuture<HookResult> execute(HookContext context) {
        if (agentCallback == null) {
            logger.warning("[AgentHook] " + name + " has no AgentCallback, defaulting to ALLOW");
            return CompletableFuture.completedFuture(
                HookResult.allow(name, "No agent callback configured"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 构造调查任务
                String investigationPrompt = buildInvestigationPrompt(context);

                // 2. 启动子 Agent 调查
                String investigationResult = agentCallback.investigate(
                    investigationPrompt, timeoutMs)
                    .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

                // 3. 解析调查结论
                return parseInvestigationResult(investigationResult);

            } catch (java.util.concurrent.TimeoutException e) {
                logger.warning("[AgentHook] " + name + " investigation timed out after " + timeoutMs + "ms");
                return failOpen
                    ? HookResult.timeout(name)
                    : HookResult.deny(name, "Agent investigation timed out (fail-closed)");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[AgentHook] " + name + " investigation failed", e);
                return failOpen
                    ? HookResult.error(name, e.getMessage())
                    : HookResult.errorFailClosed(name, e.getMessage());
            }
        });
    }

    /**
     * 构造调查任务 Prompt。
     */
    private String buildInvestigationPrompt(HookContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("【安全审查任务】\n");
        sb.append("请对以下操作进行深度安全分析，并返回结构化决策。\n\n");

        sb.append("事件类型：").append(context.getEventType().name()).append("\n");
        if (context.getToolName() != null) {
            sb.append("工具名称：").append(context.getToolName()).append("\n");
        }
        if (context.getToolInput() != null) {
            sb.append("工具输入：").append(context.getToolInput().toString()).append("\n");
        }
        if (context.getFromState() != null && context.getToState() != null) {
            sb.append("状态转换：").append(context.getFromState())
                .append(" → ").append(context.getToState()).append("\n");
        }
        if (context.getSessionId() != null) {
            sb.append("会话 ID：").append(context.getSessionId()).append("\n");
        }

        sb.append("\n请检查以下风险点：\n");
        sb.append("1. 是否涉及敏感文件（如 /etc、.ssh、.env）？\n");
        sb.append("2. 是否存在路径穿越攻击（../）？\n");
        sb.append("3. 操作范围是否在工作目录内？\n");
        sb.append("4. 命令是否可能终止系统进程？\n");
        sb.append("5. 是否存在代码注入模式？\n");
        sb.append("6. 文件内容是否符合预期？\n");

        sb.append("\n请仅返回以下格式的 JSON（不要包含 markdown 标记）：\n");
        sb.append("{\n");
        sb.append("  \"decision\": \"ALLOW 或 DENY\",\n");
        sb.append("  \"reason\": \"具体的风险评估说明\",\n");
        sb.append("  \"riskLevel\": \"LOW 或 MEDIUM 或 HIGH 或 CRITICAL\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 解析子 Agent 的调查结论。
     */
    private HookResult parseInvestigationResult(String result) {
        try {
            // 处理可能的 markdown 包裹
            String json = result.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf("\n");
                int end = json.lastIndexOf("```");
                if (start >= 0 && end > start) {
                    json = json.substring(start + 1, end).trim();
                }
            }
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }

            JsonNode root = MAPPER.readTree(json);

            String decisionStr = root.has("decision")
                ? root.get("decision").asText().toUpperCase().trim()
                : "ALLOW";

            HookDecision decision;
            try {
                decision = HookDecision.valueOf(decisionStr);
            } catch (IllegalArgumentException e) {
                decision = HookDecision.ALLOW;
            }

            String reason = root.has("reason")
                ? root.get("reason").asText()
                : "Agent investigation completed";

            // 高风险/严重风险 → 强制 DENY
            if (root.has("riskLevel")) {
                String riskLevel = root.get("riskLevel").asText().toUpperCase();
                if ("CRITICAL".equals(riskLevel)) {
                    decision = HookDecision.DENY;
                    reason = "[CRITICAL] " + reason;
                }
            }

            return new HookResult.Builder(decision, name).reason(reason).build();

        } catch (Exception e) {
            logger.warning("[AgentHook] " + name + " failed to parse investigation result: " + result);
            return HookResult.allow(name, "Failed to parse agent investigation result");
        }
    }

    @Override
    public HookImplementationType getType() { return HookImplementationType.AGENT; }

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
        return String.format("AgentHookExecutor{name='%s', agent='%s', priority=%s}",
            name, agentName, priority);
    }
}
