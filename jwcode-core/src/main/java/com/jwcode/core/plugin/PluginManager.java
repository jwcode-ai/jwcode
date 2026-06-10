package com.jwcode.core.plugin;

import com.jwcode.plugin.api.Plugin;
import com.jwcode.plugin.api.PluginCapability;
import com.jwcode.plugin.api.PluginContext;
import com.jwcode.plugin.api.PluginManifest;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 插件管理器 — 插件的发现、加载、启用、禁用和卸载。
 *
 * <p>线程安全。使用 PluginClassLoader 隔离每个插件。
 */
public class PluginManager {
    private static final Logger logger = Logger.getLogger(PluginManager.class.getName());

    private final Map<String, Plugin> loadedPlugins = new ConcurrentHashMap<>();
    private final Map<String, PluginClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final Map<String, PluginManifest> manifests = new ConcurrentHashMap<>();
    private final PluginStore store = new PluginStore();
    private final DefaultPluginContext pluginContext;

    private static volatile PluginManager instance;

    private PluginManager() {
        this.pluginContext = new DefaultPluginContext();
    }

    public static PluginManager getInstance() {
        if (instance == null) {
            synchronized (PluginManager.class) {
                if (instance == null) {
                    instance = new PluginManager();
                }
            }
        }
        return instance;
    }

    /**
     * 发现并加载所有可用插件。
     *
     * @return 成功加载的插件数量
     */
    public int discoverAndLoadAll() {
        var scanned = PluginScanner.scanAll();
        int count = 0;
        for (var sp : scanned) {
            try {
                loadPlugin(sp.directory(), sp.manifest());
                count++;
            } catch (Exception e) {
                logger.warning("[PluginManager] 加载失败: " + sp.manifest().id()
                    + " — " + e.getMessage());
                store.markError(sp.manifest().id(), e.getMessage());
            }
        }
        logger.info("[PluginManager] 发现并加载了 " + count + "/" + scanned.size() + " 个插件");
        return count;
    }

    /**
     * 加载单个插件。
     */
    public Plugin loadPlugin(Path pluginDir, PluginManifest manifest) throws Exception {
        String id = manifest.id();

        if (loadedPlugins.containsKey(id)) {
            logger.info("[PluginManager] 插件已加载，跳过: " + id);
            return loadedPlugins.get(id);
        }

        // 构建类路径
        List<URL> urls = new ArrayList<>();
        Path libDir = pluginDir.resolve("lib");
        if (java.nio.file.Files.isDirectory(libDir)) {
            for (File jar : libDir.toFile().listFiles(f -> f.getName().endsWith(".jar"))) {
                urls.add(jar.toURI().toURL());
            }
        }
        // 插件自身 JAR
        Path targetJar = pluginDir.resolve(manifest.id() + "-" + manifest.version() + ".jar");
        if (java.nio.file.Files.exists(targetJar)) {
            urls.add(targetJar.toUri().toURL());
        }
        // 类路径目录
        Path classesDir = pluginDir.resolve("classes");
        if (java.nio.file.Files.isDirectory(classesDir)) {
            urls.add(classesDir.toUri().toURL());
        }

        PluginClassLoader cl = new PluginClassLoader(id,
            urls.toArray(new URL[0]),
            PluginManager.class.getClassLoader());

        Class<?> pluginClass = cl.loadClass(manifest.entryClass());
        if (!Plugin.class.isAssignableFrom(pluginClass)) {
            throw new IllegalArgumentException("入口类未实现 Plugin 接口: " + manifest.entryClass());
        }

        Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
        plugin.init(pluginContext);

        loadedPlugins.put(id, plugin);
        classLoaders.put(id, cl);
        manifests.put(id, manifest);
        store.markLoaded(id);

        logger.info("[PluginManager] 已加载插件: " + id + " v" + manifest.version()
            + " | capabilities=" + manifest.capabilities());
        return plugin;
    }

    /** 启用插件 */
    public void enablePlugin(String pluginId) {
        Plugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) {
            logger.warning("[PluginManager] 插件未加载: " + pluginId);
            return;
        }
        if (store.isEnabled(pluginId)) {
            return;
        }
        plugin.start();
        store.markEnabled(pluginId);
        logger.info("[PluginManager] 已启用插件: " + pluginId);
    }

    /** 禁用插件 */
    public void disablePlugin(String pluginId) {
        Plugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) return;
        plugin.stop();
        store.markDisabled(pluginId);
        logger.info("[PluginManager] 已禁用插件: " + pluginId);
    }

    /** 卸载插件 */
    public void unloadPlugin(String pluginId) {
        Plugin plugin = loadedPlugins.remove(pluginId);
        if (plugin == null) return;

        try {
            plugin.destroy();
        } catch (Exception e) {
            logger.warning("[PluginManager] 销毁插件异常: " + pluginId + " — " + e.getMessage());
        }

        PluginClassLoader cl = classLoaders.remove(pluginId);
        if (cl != null) {
            try { cl.close(); } catch (Exception ignored) {}
        }

        manifests.remove(pluginId);
        store.remove(pluginId);
        logger.info("[PluginManager] 已卸载插件: " + pluginId);
    }

    /** 获取已加载的插件 */
    public Plugin getPlugin(String pluginId) {
        return loadedPlugins.get(pluginId);
    }

    /** 获取插件清单 */
    public PluginManifest getManifest(String pluginId) {
        return manifests.get(pluginId);
    }

    /** 获取所有已加载插件的清单 */
    public Map<String, PluginManifest> getAllManifests() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(manifests));
    }

    /** 获取所有已加载插件 */
    public Collection<Plugin> getLoadedPlugins() {
        return Collections.unmodifiableCollection(loadedPlugins.values());
    }

    /** 获取具有特定能力的插件 */
    public List<Plugin> getPluginsWithCapability(PluginCapability capability) {
        return loadedPlugins.entrySet().stream()
            .filter(e -> {
                PluginManifest m = manifests.get(e.getKey());
                return m != null && m.hasCapability(capability);
            })
            .map(Map.Entry::getValue)
            .toList();
    }

    /** 获取插件状态存储 */
    public PluginStore getStore() {
        return store;
    }

    /** 关闭所有插件 */
    public void shutdown() {
        for (String id : new ArrayList<>(loadedPlugins.keySet())) {
            unloadPlugin(id);
        }
        store.clear();
    }

    /**
     * 默认插件上下文实现。
     */
    private class DefaultPluginContext implements PluginContext {
        @Override
        public void registerTool(Object tool) {
            // 委托给 ToolRegistry（由外部注入）
            logger.info("[PluginContext] registerTool: " + tool.getClass().getSimpleName());
        }

        @Override
        public void unregisterTool(String toolName) {
            logger.info("[PluginContext] unregisterTool: " + toolName);
        }

        @Override
        public void registerHook(String eventType, Object hook) {
            logger.info("[PluginContext] registerHook: " + eventType);
        }

        @Override
        public void unregisterHook(String eventType, String hookId) {
            logger.info("[PluginContext] unregisterHook: " + eventType + "/" + hookId);
        }

        @Override
        public void registerSkill(Object skill) {
            logger.info("[PluginContext] registerSkill: " + skill.getClass().getSimpleName());
        }

        @Override
        public void unregisterSkill(String skillId) {
            logger.info("[PluginContext] unregisterSkill: " + skillId);
        }

        @Override
        public String getConfig(String key) {
            return com.jwcode.core.config.ConfigManager.getInstance().get(key);
        }

        @Override
        public String getConfig(String key, String defaultValue) {
            String v = getConfig(key);
            return v != null ? v : defaultValue;
        }

        @Override
        public java.nio.file.Path getPluginDataDir() {
            return java.nio.file.Path.of(
                System.getProperty("user.home"), ".jwcode", "plugins", "data");
        }

        @Override
        public void log(String level, String message) {
            java.util.logging.Level julLevel = switch (level.toLowerCase()) {
                case "error" -> java.util.logging.Level.SEVERE;
                case "warn" -> java.util.logging.Level.WARNING;
                case "debug" -> java.util.logging.Level.FINE;
                default -> java.util.logging.Level.INFO;
            };
            logger.log(julLevel, "[Plugin] " + message);
        }
    }
}
