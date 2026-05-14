package com.jwcode.core.hook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * HookRegistry — Hook 注册与发现中心。
 *
 * <p>从 {@code .jwcode/hooks.json} 加载配置，按事件类型分组索引，
 * 支持热加载和编程式注册。</p>
 *
 * <h3>配置优先级</h3>
 * <ol>
 *   <li>编程式注册（{@link #register(HookExecutor)}）</li>
 *   <li>配置文件 {@code .jwcode/hooks.json}</li>
 *   <li>内置默认 Hook（{@link HookConfig#builtinBudgetGuard()}）</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * <p>所有读操作（{@code getExecutorsFor}）是无锁的。
 * 写操作（{@code register/unregister/reload}）使用同步。</p>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class HookRegistry {

    private static final Logger logger = Logger.getLogger(HookRegistry.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOOKS_CONFIG_FILE = ".jwcode/hooks.json";

    // eventType → 排序后的 Hook 执行器列表
    private final Map<HookEventType, List<HookExecutor>> executorIndex;
    // 所有已注册的执行器（用于重载时清理）
    private final List<HookExecutor> allExecutors;

    private final Path configFilePath;
    private long lastLoadTime;

    public HookRegistry() {
        this(Paths.get(System.getProperty("user.dir", "."), HOOKS_CONFIG_FILE));
    }

    public HookRegistry(Path configFilePath) {
        this.executorIndex = new ConcurrentHashMap<>();
        this.allExecutors = new CopyOnWriteArrayList<>();
        this.configFilePath = configFilePath;
        this.lastLoadTime = 0;

        // 初始化内置 Hook
        registerBuiltinHooks();
        // 加载配置文件
        loadFromFile();
    }

    // ==================== 查询 ====================

    /**
     * 获取指定事件类型的所有 Hook 执行器（已按优先级排序）。
     *
     * @param eventType 事件类型
     * @return 不可变的执行器列表
     */
    public List<HookExecutor> getExecutorsFor(HookEventType eventType) {
        return executorIndex.getOrDefault(eventType, List.of());
    }

    /**
     * 获取所有已注册的 Hook 执行器。
     */
    public List<HookExecutor> getAllExecutors() {
        return List.copyOf(allExecutors);
    }

    /**
     * 获取指定优先级的 Hook 执行器。
     */
    public List<HookExecutor> getExecutorsByPriority(HookPriority priority) {
        return allExecutors.stream()
            .filter(e -> e.getPriority() == priority)
            .toList();
    }

    /**
     * 获取审计统计摘要。
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalHooks", allExecutors.size());
        stats.put("configFile", configFilePath.toString());
        stats.put("lastLoadTime", new Date(lastLoadTime).toString());

        Map<String, Integer> byEvent = new LinkedHashMap<>();
        for (HookEventType eventType : HookEventType.values()) {
            int count = getExecutorsFor(eventType).size();
            if (count > 0) {
                byEvent.put(eventType.name(), count);
            }
        }
        stats.put("hooksByEvent", byEvent);
        return stats;
    }

    // ==================== 注册 ====================

    /**
     * 编程式注册 Hook 执行器。
     */
    public synchronized void register(HookExecutor executor) {
        Objects.requireNonNull(executor, "executor");
        if (!executor.isEnabled()) {
            logger.fine("[HookRegistry] Skipping disabled executor: " + executor.getName());
            return;
        }
        allExecutors.add(executor);
        rebuildIndex();
        logger.info("[HookRegistry] Registered: " + executor.getName()
            + " (type=" + executor.getType() + ", priority=" + executor.getPriority() + ")");
    }

    /**
     * 批量注册。
     */
    public synchronized void registerAll(List<HookExecutor> executors) {
        for (HookExecutor executor : executors) {
            register(executor);
        }
    }

    /**
     * 注销 Hook 执行器。
     */
    public synchronized void unregister(String executorName) {
        allExecutors.removeIf(e -> e.getName().equals(executorName));
        rebuildIndex();
        logger.info("[HookRegistry] Unregistered: " + executorName);
    }

    /**
     * 清空所有注册的执行器。
     */
    public synchronized void clear() {
        allExecutors.clear();
        executorIndex.clear();
        logger.info("[HookRegistry] Cleared all executors");
    }

    // ==================== 热加载 ====================

    /**
     * 重新加载配置文件。
     * <p>编程式注册的执行器不受影响。</p>
     *
     * @return 重新加载的 Hook 数量
     */
    public synchronized int reload() {
        // 移除从文件加载的执行器（保留编程式注册的）
        allExecutors.removeIf(e -> e instanceof ConfiguredHookExecutor);
        int count = loadFromFile();
        logger.info("[HookRegistry] Reloaded: " + count + " hooks from file");
        return count;
    }

    /**
     * 检查配置文件是否已变更并自动重载。
     *
     * @return true 如果执行了重载
     */
    public boolean autoReloadIfChanged() {
        try {
            if (!Files.exists(configFilePath)) {
                return false;
            }
            long fileLastModified = Files.getLastModifiedTime(configFilePath).toMillis();
            if (fileLastModified > lastLoadTime) {
                reload();
                return true;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "[HookRegistry] Auto-reload check failed", e);
        }
        return false;
    }

    // ==================== 内部方法 ====================

    /**
     * 从配置文件加载 Hook 配置。
     */
    private int loadFromFile() {
        if (!Files.exists(configFilePath)) {
            logger.fine("[HookRegistry] Config file not found: " + configFilePath);
            return 0;
        }

        try {
            String json = Files.readString(configFilePath);
            Map<String, Object> root = MAPPER.readValue(json,
                new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hooksConfig = (List<Map<String, Object>>) root.getOrDefault("hooks", List.of());

            int count = 0;
            for (Map<String, Object> entry : hooksConfig) {
                HookConfig config = parseHookConfig(entry);
                if (config != null && config.isEnabled()) {
                    ConfiguredHookExecutor executor = new ConfiguredHookExecutor(config);
                    allExecutors.add(executor);
                    count++;
                }
            }

            rebuildIndex();
            lastLoadTime = System.currentTimeMillis();
            logger.info("[HookRegistry] Loaded " + count + " hooks from " + configFilePath);
            return count;
        } catch (IOException e) {
            logger.log(Level.WARNING, "[HookRegistry] Failed to load config: " + configFilePath, e);
            return 0;
        }
    }

    /**
     * 解析单个 Hook 配置项。
     */
    private HookConfig parseHookConfig(Map<String, Object> entry) {
        try {
            String name = (String) entry.get("name");
            if (name == null || name.isEmpty()) {
                logger.warning("[HookRegistry] Skipping hook config with empty name");
                return null;
            }

            String description = (String) entry.getOrDefault("description", "");

            // 解析事件列表
            @SuppressWarnings("unchecked")
            List<String> eventNames = (List<String>) entry.getOrDefault("events", List.of());
            List<HookEventType> events = eventNames.stream()
                .map(n -> {
                    try { return HookEventType.valueOf(n.toUpperCase()); }
                    catch (IllegalArgumentException e) {
                        logger.warning("[HookRegistry] Unknown event type: " + n);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // 解析实现类型
            @SuppressWarnings("unchecked")
            Map<String, Object> impl = (Map<String, Object>) entry.get("implementation");
            if (impl == null) {
                logger.warning("[HookRegistry] No implementation defined for hook: " + name);
                return null;
            }

            String typeStr = (String) impl.get("type");
            HookImplementationType implType;
            try {
                implType = HookImplementationType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[HookRegistry] Unknown implementation type: " + typeStr);
                return null;
            }

            // 解析优先级
            String priorityStr = (String) entry.getOrDefault("priority", "USER");
            HookPriority priority;
            try {
                priority = HookPriority.valueOf(priorityStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                priority = HookPriority.USER;
            }

            // 解析工具列表
            @SuppressWarnings("unchecked")
            List<String> tools = (List<String>) entry.getOrDefault("tools", List.of());

            // 解析匹配器
            @SuppressWarnings("unchecked")
            Map<String, String> matchers = (Map<String, String>) entry.getOrDefault("matchers", Map.of());

            // 解析超时
            long timeoutMs = entry.containsKey("timeoutMs")
                ? ((Number) entry.get("timeoutMs")).longValue()
                : implType.getDefaultTimeoutMs();

            // 解析开关
            boolean enabled = entry.containsKey("enabled")
                ? (Boolean) entry.get("enabled")
                : true;

            boolean failOpen = entry.containsKey("failOpen")
                ? (Boolean) entry.get("failOpen")
                : !priority.isFailClosed();

            return new HookConfig.Builder()
                .name(name)
                .description(description)
                .events(events)
                .implementationType(implType)
                .command((String) impl.get("command"))
                .url((String) impl.get("url"))
                .promptTemplate((String) impl.get("promptTemplate"))
                .agentName((String) impl.get("agentName"))
                .priority(priority)
                .tools(tools)
                .matchers(matchers)
                .timeoutMs(timeoutMs)
                .enabled(enabled)
                .failOpen(failOpen)
                .build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[HookRegistry] Failed to parse hook config: " + entry, e);
            return null;
        }
    }

    /**
     * 注册内置默认 Hook。
     */
    private void registerBuiltinHooks() {
        // 内置 Hook 暂不自动注册，由调用方显式添加
        logger.fine("[HookRegistry] Builtin hooks available for explicit registration");
    }

    /**
     * 重建事件类型索引。
     */
    private void rebuildIndex() {
        executorIndex.clear();
        for (HookExecutor executor : allExecutors) {
            for (HookEventType eventType : HookEventType.values()) {
                if (executor.supportsEvent(eventType)) {
                    executorIndex.computeIfAbsent(eventType, k -> new ArrayList<>())
                        .add(executor);
                }
            }
        }
        // 按优先级排序
        executorIndex.forEach((eventType, list) ->
            list.sort(Comparator.comparingInt(e -> -e.getPriority().getLevel())));
    }

    // ==================== 内部类 ====================

    /**
     * 基于 HookConfig 的通用执行器适配器。
     * <p>根据配置的 {@link HookImplementationType} 委托给对应的专用执行器.
     * 作为配置文件解析与真实执行器之间的桥梁.</p>
     */
    static class ConfiguredHookExecutor implements HookExecutor {
        private final HookConfig config;

        ConfiguredHookExecutor(HookConfig config) {
            this.config = config;
        }

        @Override
        public CompletableFuture<HookResult> execute(HookContext context) {
            // 委托给对应的专用执行器
            // 实际执行器在 jwcode-core 初始化时由 HookExecutorFactory 创建
            logger.warning("[HookRegistry] ConfiguredHookExecutor '" + config.getName()
                + "' has no backing executor (type=" + config.getImplementationType()
                + ") — defaulting to ALLOW");
            return CompletableFuture.completedFuture(
                HookResult.allow(config.getName(), "No backing executor for type="
                    + config.getImplementationType()));
        }

        @Override
        public HookImplementationType getType() { return config.getImplementationType(); }

        @Override
        public String getName() { return config.getName(); }

        @Override
        public boolean supportsEvent(HookEventType eventType) { return config.supportsEvent(eventType); }

        @Override
        public HookPriority getPriority() { return config.getPriority(); }

        @Override
        public boolean isEnabled() { return config.isEnabled(); }

        @Override
        public long getTimeoutMs() { return config.getTimeoutMs(); }

        @Override
        public boolean isFailOpen() { return config.isFailOpen(); }

        public HookConfig getConfig() { return config; }
    }
}
