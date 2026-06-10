package com.jwcode.core.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jwcode.core.a2a.dispatcher.LocalAgentDispatcher;
import com.jwcode.core.agent.MainAgentStateMachine;
import com.jwcode.core.hook.executor.AgentHookExecutor;
import com.jwcode.core.hook.executor.PromptHookExecutor;
import com.jwcode.core.tool.ToolExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static volatile HookRegistry registry;

    private static final String HOOKS_DIR = ".jwcode";
    private static final String HOOKS_FILE = ".jwcode/hooks.json";

    // ──────────── 钩子实现 ────────────

    /**
     * ToolUsageStatsHook — 纯 Java 实现，统计工具调用次数。
     *
     * <p>BashSafetyHook 和 FileWriteAuditHook 已移至独立文件，
     * 通过 {@link BashSafetyHook} 和 {@link FileWriteAuditHook} 注册。</p>
     */
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
            // 0. 确保 .jwcode 目录存在
            Path hooksDir = projectRoot.resolve(HOOKS_DIR);
            Files.createDirectories(hooksDir);

            // 1. 自动创建 hooks.json 模板（如果不存在）
            Path hooksFile = projectRoot.resolve(HOOKS_FILE);
            if (!Files.exists(hooksFile) || Files.size(hooksFile) < 50) {
                Files.writeString(hooksFile, HOOKS_JSON_TEMPLATE);
                logger.info("[HookInit] Created default hooks.json template");
            }

            // 2. 创建 HookRegistry 并加载配置
            HookRegistry hookRegistry = new HookRegistry(hooksFile);
            HookSystemInitializer.registry = hookRegistry;

            // 3. 注册内置 Hook（纯 Java 实现，零外部依赖 — 无论 hooks.json 是否为空都生效）
            hookRegistry.register(new BashSafetyHook());
            hookRegistry.register(new FileWriteAuditHook());
            hookRegistry.register(new ToolUsageStatsExecutor());
            logger.info("[HookInit] Registered 3 built-in hooks (BashSafetyHook, FileWriteAuditHook, ToolUsageStatsHook)");

            // 4. 创建工厂并解析配置文件中的 Hook
            HookExecutorFactory factory = new HookExecutorFactory(llmCallback, agentCallback);
            int resolved = hookRegistry.resolveConfiguredExecutors(factory);
            if (resolved > 0) {
                logger.info("[HookInit] Resolved " + resolved + " configured hook(s)");
            }

            // 5. 统计总数
            hookCount = hookRegistry.getAllExecutors().size();

            // 6. 创建 HookChain + 审计日志
            HookAuditLogger auditLogger = new HookAuditLogger();
            HookChain chain = new HookChain(hookRegistry, auditLogger);

            // 7. 注入到各拦截点
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

            // 8. 广播
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
     * 获取全局 HookRegistry 实例（需先调用 initialize()）。
     */
    public static HookRegistry getRegistry() {
        return registry;
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

}
