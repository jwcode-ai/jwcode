package com.jwcode.core.plugin;

import com.jwcode.core.hook.HookRegistry;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.plugin.api.PluginCapability;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * PluginBoot — 插件启动编排器。
 *
 * <p>分阶段加载插件，确保依赖关系正确：
 * <ol>
 *   <li>ENVIRONMENT — 环境插件（最先加载，提供环境变量等基础能力）</li>
 *   <li>PROVIDER — LLM 提供者插件</li>
 *   <li>AGENT — Agent 定义插件</li>
 *   <li>COMMAND — 斜杠命令插件</li>
 *   <li>SKILL — 技能插件</li>
 *   <li>TOOL — 工具插件（可依赖前面所有阶段）</li>
 *   <li>CONFIG — 配置插件（最后加载，可覆盖前面插件的配置）</li>
 * </ol>
 *
 * <p>每个阶段内部按插件声明依赖拓扑排序。
 */
public class PluginBoot {

    private static final Logger logger = Logger.getLogger(PluginBoot.class.getName());

    public enum Phase {
        ENVIRONMENT,
        PROVIDER,
        AGENT,
        COMMAND,
        SKILL,
        TOOL,
        CONFIG
    }

    private final PluginManager pluginManager;
    private final HookRegistry hookRegistry;
    private final ToolRegistry toolRegistry;

    public PluginBoot(PluginManager pluginManager, HookRegistry hookRegistry, ToolRegistry toolRegistry) {
        this.pluginManager = pluginManager;
        this.hookRegistry = hookRegistry;
        this.toolRegistry = toolRegistry;

        // 将注册表注入 PluginManager，使 DefaultPluginContext 能实际注册工具和钩子
        pluginManager.setToolRegistry(toolRegistry);
        pluginManager.setHookRegistry(hookRegistry);
    }

    // ==================== 启动编排 ====================

    /**
     * 执行完整的分阶段启动。
     *
     * @return 每个阶段成功加载的插件数量
     */
    public BootResult boot() {
        logger.info("[PluginBoot] Starting staged plugin bootstrap...");
        BootResult result = new BootResult();

        // Phase 1: 扫描所有可用插件
        int discovered = pluginManager.discoverAndLoadAll();
        result.setDiscovered(discovered);
        logger.info("[PluginBoot] Phase 0: discovered " + discovered + " plugins");

        // Phase 2: 分阶段启用
        for (Phase phase : Phase.values()) {
            int count = enablePhase(phase);
            result.setPhaseCount(phase, count);
            logger.info("[PluginBoot] Phase " + phase + ": enabled " + count + " plugins");
        }

        // Phase 3: 验证依赖
        int failed = verifyDependencies();
        result.setDependencyFailures(failed);

        logger.info("[PluginBoot] Bootstrap complete: " + result);
        return result;
    }

    /**
     * 启用指定阶段的所有插件。
     */
    private int enablePhase(Phase phase) {
        var manifests = pluginManager.getAllManifests();
        int count = 0;

        for (var entry : manifests.entrySet()) {
            String id = entry.getKey();
            var manifest = entry.getValue();

            if (!matchesPhase(manifest.capabilities(), phase)) {
                continue;
            }

            // 检查依赖是否满足
            if (manifest.dependencies() != null && !manifest.dependencies().isEmpty()) {
                boolean allMet = manifest.dependencies().stream()
                    .allMatch(dep -> pluginManager.getPlugin(dep) != null);
                if (!allMet) {
                    logger.warning("[PluginBoot] Plugin '" + id + "' has unmet dependencies: "
                        + manifest.dependencies() + ", skipping");
                    continue;
                }
            }

            try {
                pluginManager.enablePlugin(id);
                // 连接 HookRegistry
                wirePluginHooks(id);
                count++;
            } catch (Exception e) {
                logger.warning("[PluginBoot] Failed to enable plugin '" + id + "': " + e.getMessage());
            }
        }

        return count;
    }

    /**
     * 将插件的钩子注册到 HookRegistry。
     */
    private void wirePluginHooks(String pluginId) {
        try {
            hookRegistry.registerPluginHooks(pluginManager);
        } catch (Exception e) {
            logger.fine("[PluginBoot] No hooks to wire for " + pluginId + ": " + e.getMessage());
        }
    }

    /**
     * 验证所有已启用插件的依赖。
     */
    private int verifyDependencies() {
        int failed = 0;
        for (String id : pluginManager.getAllManifests().keySet()) {
            var manifest = pluginManager.getManifest(id);
            if (manifest == null || manifest.dependencies() == null) continue;

            for (String dep : manifest.dependencies()) {
                var depManifest = pluginManager.getManifest(dep);
                if (depManifest == null) {
                    logger.warning("[PluginBoot] Plugin '" + id + "' depends on '" + dep + "' but it's not loaded");
                    failed++;
                }
            }
        }
        if (failed > 0) {
            logger.warning("[PluginBoot] " + failed + " dependency violations found");
        }
        return failed;
    }

    /**
     * 判断插件能力是否匹配阶段。
     */
    private boolean matchesPhase(Set<PluginCapability> capabilities, Phase phase) {
        if (capabilities == null || capabilities.isEmpty()) return false;
        return switch (phase) {
            case ENVIRONMENT -> false;
            case PROVIDER -> capabilities.contains(PluginCapability.MCP_SERVER);
            case AGENT -> capabilities.contains(PluginCapability.AGENT);
            case COMMAND -> capabilities.contains(PluginCapability.COMMAND);
            case SKILL -> capabilities.contains(PluginCapability.SKILL);
            case TOOL -> capabilities.contains(PluginCapability.TOOL);
            case CONFIG -> false;
        };
    }

    // ==================== 结果 ====================

    /**
     * 启动结果。
     */
    public static class BootResult {
        private int discovered;
        private final int[] phaseCounts = new int[Phase.values().length];
        private int dependencyFailures;

        void setDiscovered(int n) { this.discovered = n; }
        void setPhaseCount(Phase phase, int n) { phaseCounts[phase.ordinal()] = n; }
        void setDependencyFailures(int n) { this.dependencyFailures = n; }

        public int getDiscovered() { return discovered; }
        public int getPhaseCount(Phase phase) { return phaseCounts[phase.ordinal()]; }
        public int getDependencyFailures() { return dependencyFailures; }
        public int getTotalEnabled() {
            int total = 0;
            for (int c : phaseCounts) total += c;
            return total;
        }

        @Override
        public String toString() {
            return "BootResult{discovered=" + discovered
                + ", enabled=" + getTotalEnabled()
                + ", depFailures=" + dependencyFailures + "}";
        }
    }
}
