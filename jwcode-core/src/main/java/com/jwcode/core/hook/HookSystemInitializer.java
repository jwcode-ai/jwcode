package com.jwcode.core.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jwcode.core.a2a.dispatcher.LocalAgentDispatcher;
import com.jwcode.core.agent.MainAgentStateMachine;
import com.jwcode.core.hook.executor.AgentHookExecutor;
import com.jwcode.core.hook.executor.PromptHookExecutor;
import com.jwcode.core.tool.ToolExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * HookSystemInitializer — Hook 系统一键初始化器。
 *
 * <p>负责整个 Hook 系统从"代码存在"到"运行时生效"的启动接线。</p>
 *
 * <h3>内置默认 Hook（纯 Java 实现，零外部依赖）</h3>
 * <ul>
 *   <li><b>BashSafetyHook</b> — 拦截危险 shell 命令（SECURITY，fail-closed）</li>
 *   <li><b>FileWriteAuditHook</b> — 记录文件写入到 .jwcode/hook-audit.log</li>
 *   <li><b>ToolUsageStatsHook</b> — 统计工具调用到 .jwcode/hook-stats.json</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 2.1.1
 */
public class HookSystemInitializer {

    private static final Logger logger = Logger.getLogger(HookSystemInitializer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static volatile boolean initialized = false;

    private static final String HOOKS_DIR = ".jwcode";
    private static final String HOOKS_FILE = ".jwcode/hooks.json";

    // ──────────── 危险命令正则 ────────────

    private static final Pattern DANGEROUS_CMD = Pattern.compile(
        "rm\\s+-rf\\s+/|mkfs\\.|: *\\( *\\) *\\{ *:|DROP\\s+TABLE|DELETE\\s+FROM|" +
        "format\\s+[a-z]:|del\\s+/[fF]\\s+/[sS]|shutdown\\s+-|>\\s*/dev/sd",
        Pattern.CASE_INSENSITIVE);

    // ──────────── 钩子实现 ────────────

    /** BashSafetyHook — 纯 Java 实现，拦截危险 shell 命令 */
    private static class BashSafetyExecutor implements HookExecutor {
        private static final Set<String> TARGET_TOOLS = Set.of("BashTool", "PowerShellTool");

        @Override
        public CompletableFuture<HookResult> execute(HookContext context) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 仅对目标工具生效
                    if (context.getToolName() == null
                        || !TARGET_TOOLS.contains(context.getToolName())) {
                        return HookResult.allow("BashSafetyHook");
                    }
                    String toolInput = context.getToolInput() != null
                        ? context.getToolInput().toString() : "";
                    if (DANGEROUS_CMD.matcher(toolInput).find()) {
                        return HookResult.deny("BashSafetyHook",
                            "Dangerous command pattern detected: " + truncate(toolInput, 80));
                    }
                    // Claude Code 风格: 所有 shell 命令需审批
                    return HookResult.ask("BashSafetyHook",
                        truncate(toolInput, 100), "审批 shell 命令执行");
                } catch (Exception e) {
                    return HookResult.allow("BashSafetyHook", "Hook error, fail-open");
                }
            });
        }
        @Override public HookImplementationType getType() { return HookImplementationType.SHELL; }
        @Override public String getName() { return "BashSafetyHook"; }
        @Override public HookPriority getPriority() { return HookPriority.SECURITY; }
        @Override public boolean isFailOpen() { return false; }
        @Override public long getTimeoutMs() { return 5000; }
        @Override public boolean supportsEvent(HookEventType eventType) {
            return eventType == HookEventType.PRE_TOOL_USE;
        }
    }

    /** FileWriteApprovalHook — Claude Code 风格: 文件修改前审批 */
    private static class FileWriteAuditExecutor implements HookExecutor {
        private static final Set<String> TARGET_TOOLS = Set.of("FileWriteTool", "FileEditTool");

        @Override
        public CompletableFuture<HookResult> execute(HookContext context) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (context.getToolName() == null
                        || !TARGET_TOOLS.contains(context.getToolName())) {
                        return HookResult.allow("FileWriteApprovalHook");
                    }
                    String fp = extractFilePath(context);
                    String tool = context.getToolName();
                    return HookResult.ask("FileWriteApprovalHook",
                        fp != null ? fp : "(unknown file)",
                        "审批文件修改: " + tool);
                } catch (Exception e) {
                    return HookResult.allow("FileWriteApprovalHook", "Hook error, fail-open");
                }
            });
        }
        private String extractFilePath(HookContext ctx) {
            if (ctx.getToolResult() != null) {
                try {
                    var fpNode = ctx.getToolResult().get("filePath");
                    if (fpNode != null) return fpNode.asText();
                } catch (Exception ignored) {}
            }
            if (ctx.getToolInput() != null) {
                try {
                    var fpNode = ctx.getToolInput().get("filePath");
                    if (fpNode == null) fpNode = ctx.getToolInput().get("file_path");
                    if (fpNode == null) fpNode = ctx.getToolInput().get("path");
                    if (fpNode != null) return fpNode.asText();
                } catch (Exception ignored) {}
            }
            return null;
        }
        @Override public HookImplementationType getType() { return HookImplementationType.SHELL; }
        @Override public String getName() { return "FileWriteAuditHook"; }
        @Override public HookPriority getPriority() { return HookPriority.PROJECT; }
        @Override public boolean isFailOpen() { return true; }
        @Override public long getTimeoutMs() { return 3000; }
        @Override public boolean supportsEvent(HookEventType eventType) {
            return eventType == HookEventType.PRE_TOOL_USE;
        }
    }

    /** ToolUsageStatsHook — 纯 Java 实现，统计工具调用次数 */
    private static class ToolUsageStatsExecutor implements HookExecutor {
        private final Path statsFile = Path.of(HOOKS_DIR, "hook-stats.json");
        @Override
        public CompletableFuture<HookResult> execute(HookContext context) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String toolName = context.getToolName() != null
                        ? context.getToolName() : "unknown";
                    Map<String, Object> stats = new LinkedHashMap<>();
                    if (Files.exists(statsFile)) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> existing = MAPPER.readValue(
                                statsFile.toFile(), Map.class);
                            stats.putAll(existing);
                        } catch (Exception ignored) {}
                    }
                    Object val = stats.getOrDefault(toolName, 0);
                    long count = val instanceof Number ? ((Number) val).longValue() + 1 : 1;
                    stats.put(toolName, count);
                    Files.createDirectories(statsFile.getParent());
                    MAPPER.writeValue(statsFile.toFile(), stats);
                } catch (Exception e) {
                    logger.fine("[Hook] ToolUsageStats failed: " + e.getMessage());
                }
                return HookResult.allow("ToolUsageStatsHook");
            });
        }
        @Override public HookImplementationType getType() { return HookImplementationType.SHELL; }
        @Override public String getName() { return "ToolUsageStatsHook"; }
        @Override public HookPriority getPriority() { return HookPriority.USER; }
        @Override public boolean isFailOpen() { return true; }
        @Override public long getTimeoutMs() { return 3000; }
        @Override public boolean supportsEvent(HookEventType eventType) {
            return eventType == HookEventType.POST_TOOL_USE;
        }
    }

    // ──────────── 配置文件模板 ────────────

    private static final String HOOKS_JSON_TEMPLATE = """
{
  "_comment": "JWCode Hook 配置文件",
  "_docs": "编辑此文件添加自定义 Hook 拦截规则。enabled=false 可禁用。",
  "_builtin": "BashSafetyHook、FileWriteAuditHook、ToolUsageStatsHook 已内置启用",
  "hooks": []
}""";

    // ──────────── 初始化方法 ────────────

    /**
     * 初始化 Hook 系统（简化版）。
     */
    public static HookInitResult initialize(Path projectRoot,
                                             ToolExecutor toolExecutor,
                                             MainAgentStateMachine stateMachine,
                                             LocalAgentDispatcher dispatcher) {
        return initialize(projectRoot, toolExecutor, stateMachine, dispatcher, null, null);
    }

    /**
     * 完整初始化（含 LLM/Agent 回调）。
     */
    public static HookInitResult initialize(Path projectRoot,
                                             ToolExecutor toolExecutor,
                                             MainAgentStateMachine stateMachine,
                                             LocalAgentDispatcher dispatcher,
                                             PromptHookExecutor.LlmCallback llmCallback,
                                             AgentHookExecutor.AgentCallback agentCallback) {
        long startTime = System.currentTimeMillis();
        int hookCount = 0;
        String status = "OK";

        try {
            // 0. 按需初始化：无 hooks.json 配置时跳过（降低空转开销）
            Path hooksFile = projectRoot.resolve(HOOKS_FILE);
            boolean hasConfig = Files.exists(hooksFile) && Files.size(hooksFile) > 50;
            if (!hasConfig) {
                logger.fine("[HookInit] No hooks config found, skipping initialization");
                return new HookInitResult(0, "SKIPPED (no config)", System.currentTimeMillis() - startTime);
            }

            // 1. 确保 .jwcode 目录存在
            Path hooksDir = projectRoot.resolve(HOOKS_DIR);
            Files.createDirectories(hooksDir);

            // 3. 创建 HookRegistry 并加载配置
            HookRegistry registry = new HookRegistry(hooksFile);

            // 4. 注册内置 Hook（纯 Java 实现，零外部依赖）
            registry.register(new BashSafetyExecutor());
            registry.register(new FileWriteAuditExecutor());
            registry.register(new ToolUsageStatsExecutor());
            logger.info("[HookInit] Registered 3 built-in hooks");

            // 5. 创建工厂并解析配置文件中的 Hook
            HookExecutorFactory factory = new HookExecutorFactory(llmCallback, agentCallback);
            int resolved = registry.resolveConfiguredExecutors(factory);
            if (resolved > 0) {
                logger.info("[HookInit] Resolved " + resolved + " configured hook(s)");
            }

            // 6. 统计总数
            hookCount = registry.getAllExecutors().size();

            // 7. 创建 HookChain + 审计日志
            HookAuditLogger auditLogger = new HookAuditLogger();
            HookChain chain = new HookChain(registry, auditLogger);

            // 8. 注入到各拦截点
            if (toolExecutor != null) {
                toolExecutor.setHookChain(chain);
                logger.info("[HookInit] Injected → ToolExecutor");
            }
            if (stateMachine != null) {
                stateMachine.setHookChain(chain);
                logger.info("[HookInit] Injected → MainAgentStateMachine");
            }
            if (dispatcher != null) {
                dispatcher.setHookChain(chain);
                logger.info("[HookInit] Injected → LocalAgentDispatcher");
            }

            // 9. 广播
            HookEventBroadcaster.broadcastInit(hookCount, resolved);

        } catch (Exception e) {
            status = "ERROR: " + e.getMessage();
            logger.log(Level.WARNING, "[HookInit] Initialization failed", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        HookInitResult result = new HookInitResult(hookCount, status, duration);
        initialized = result.isSuccess();
        logger.info("[HookInit] Done: " + result);
        return result;
    }

    /**
     * 检查 Hook 系统是否已初始化。
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 初始化结果。
     */
    public record HookInitResult(int hookCount, String status, long durationMs) {
        public boolean isSuccess() { return "OK".equals(status); }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
