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
     * 加载 Claude Code 兼容的 settings.json (三级优先级).
     * 1. ~/.jwcode/settings.json (用户级)
     * 2. .jwcode/settings.json (项目级)
     * 3. .jwcode/settings.local.json (本地级，最高优先级)
     */
    public synchronized int loadSettings() {
        int count = 0;
        // 按优先级从低到高加载，高优先级覆盖
        String[] paths = {
            System.getProperty("user.home") + "/.jwcode/hooks.json",
            ".jwcode/hooks.json",
            ".jwcode/hooks.local.json"
        };
        for (String p : paths) {
            try {
                Path path = Path.of(p);
                if (!Files.exists(path)) continue;
                String json = Files.readString(path);
                Map<String, Object> root = MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                Map<String, Object> hooks = (Map<String, Object>) root.getOrDefault("hooks", Map.of());
                for (var entry : hooks.entrySet()) {
                    String eventType = entry.getKey();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> matchers = (List<Map<String, Object>>) entry.getValue();
                    for (var matcherGroup : matchers) {
                        String matcher = (String) matcherGroup.getOrDefault("matcher", "*");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> hookList = (List<Map<String, Object>>) matcherGroup.getOrDefault("hooks", List.of());
                        for (var h : hookList) {
                            String type = (String) h.getOrDefault("type", "command");
                            String command = (String) h.get("command");
                            int timeout = h.containsKey("timeout") ? ((Number)h.get("timeout")).intValue() : 30;
                            // 注册为 SHELL hook
                            try {
                                HookEventType et = HookEventType.valueOf(eventType.toUpperCase());
                                HookConfig cfg = new HookConfig.Builder()
                                    .name("settings:" + eventType + "/" + matcher)
                                    .events(List.of(et))
                                    .implementationType(HookImplementationType.SHELL)
                                    .command(command)
                                    .tools(matcher.equals("*") ? List.of() : List.of(matcher))
                                    .timeoutMs(timeout * 1000L)
                                    .priority(HookPriority.USER)
                                    .enabled(true)
                                    .build();
                                register(new ConfiguredHookExecutor(cfg));
                                count++;
                            } catch (IllegalArgumentException e) {
                                logger.warning("[HookRegistry] Unknown event in settings: " + eventType);
                            }
                        }
                    }
                }
                logger.info("[HookRegistry] Loaded " + count + " hooks from " + p);
            } catch (Exception e) {
                logger.warning("[HookRegistry] Failed to load settings from " + p + ": " + e.getMessage());
            }
        }
        return count;
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

    /**
     * 使用工厂将 ConfiguredHookExecutor 替换为真实执行器。
     *
     * <p>必须在加载配置文件后调用。遍历所有注册的执行器，
     * 找到 {@link ConfiguredHookExecutor} 实例，通过
     * {@link HookExecutorFactory} 创建真实的执行器并替换。</p>
     *
     * @param factory Hook 执行器工厂
     * @return 成功替换的执行器数量
     */
    public synchronized int resolveConfiguredExecutors(HookExecutorFactory factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        int resolved = 0;
        List<HookExecutor> replacement = new ArrayList<>();

        for (HookExecutor executor : allExecutors) {
            if (executor instanceof ConfiguredHookExecutor cfg) {
                try {
                    HookExecutor real = factory.create(cfg.getConfig());
                    replacement.add(real);
                    logger.info("[HookRegistry] Resolved: " + cfg.getName()
                        + " → " + real.getClass().getSimpleName());
                    resolved++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[HookRegistry] Failed to resolve '"
                        + cfg.getName() + "': " + e.getMessage(), e);
                    // 保留原始 ConfiguredHookExecutor（将 fallback 到 ALLOW）
                    replacement.add(cfg);
                }
            } else {
                replacement.add(executor);
            }
        }

        allExecutors.clear();
        allExecutors.addAll(replacement);
        rebuildIndex();
        logger.info("[HookRegistry] Resolved " + resolved + " configured hook(s)");
        return resolved;
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
            // 如果到这里，说明 resolveConfiguredExecutors() 没有被调用或解析失败
            // 这是 fallback 路径：放行操作，确保系统可用性
            logger.fine("[HookRegistry] ConfiguredHookExecutor '" + config.getName()
                + "' not resolved (type=" + config.getImplementationType()
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
