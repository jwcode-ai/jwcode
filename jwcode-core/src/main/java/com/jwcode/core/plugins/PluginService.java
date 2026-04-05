package com.jwcode.core.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PluginService - 插件服务
 * 
 * 功能说明：
 * 提供插件管理功能，包括插件加载、注册、启用/禁用等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class PluginService {
    
    private final Map<String, PluginInfo> plugins;
    private final Map<String, PluginLoader> loaders;
    private final PluginListener listener;
    private String pluginsDirectory;
    
    public PluginService() {
        this(null);
    }
    
    public PluginService(PluginListener listener) {
        this.plugins = new ConcurrentHashMap<>();
        this.loaders = new ConcurrentHashMap<>();
        this.listener = listener;
        this.pluginsDirectory = System.getProperty("user.home") + "/.jwcode/plugins";
    }
    
    /**
     * 设置插件目录
     */
    public void setPluginsDirectory(String directory) {
        this.pluginsDirectory = directory;
    }
    
    /**
     * 获取插件目录
     */
    public String getPluginsDirectory() {
        return pluginsDirectory;
    }
    
    /**
     * 注册插件
     */
    public void registerPlugin(PluginInfo plugin) {
        plugins.put(plugin.id, plugin);
        if (listener != null) {
            listener.onPluginRegistered(plugin);
        }
    }
    
    /**
     * 卸载插件
     */
    public void unregisterPlugin(String pluginId) {
        PluginInfo plugin = plugins.remove(pluginId);
        if (plugin != null) {
            disablePlugin(pluginId);
            if (listener != null) {
                listener.onPluginUnregistered(plugin);
            }
        }
    }
    
    /**
     * 获取插件信息
     */
    public PluginInfo getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }
    
    /**
     * 获取所有插件
     */
    public List<PluginInfo> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }
    
    /**
     * 获取已启用的插件
     */
    public List<PluginInfo> getEnabledPlugins() {
        List<PluginInfo> enabled = new ArrayList<>();
        for (PluginInfo plugin : plugins.values()) {
            if (plugin.enabled) {
                enabled.add(plugin);
            }
        }
        return enabled;
    }
    
    /**
     * 加载插件
     */
    public CompletableFuture<Boolean> loadPlugin(String pluginId) {
        PluginInfo plugin = plugins.get(pluginId);
        if (plugin == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                PluginLoader loader = new PluginLoader(plugin);
                loaders.put(pluginId, loader);
                loader.load();
                
                plugin.loaded = true;
                if (listener != null) {
                    listener.onPluginLoaded(plugin);
                }
                return true;
            } catch (Exception e) {
                if (listener != null) {
                    listener.onPluginError(plugin, e);
                }
                return false;
            }
        });
    }
    
    /**
     * 卸载插件
     */
    public CompletableFuture<Boolean> unloadPlugin(String pluginId) {
        PluginInfo plugin = plugins.get(pluginId);
        if (plugin == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            PluginLoader loader = loaders.remove(pluginId);
            if (loader != null) {
                loader.unload();
            }
            
            plugin.loaded = false;
            if (listener != null) {
                listener.onPluginUnloaded(plugin);
            }
            return true;
        });
    }
    
    /**
     * 启用插件
     */
    public CompletableFuture<Boolean> enablePlugin(String pluginId) {
        PluginInfo plugin = plugins.get(pluginId);
        if (plugin == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            plugin.enabled = true;
            
            if (!plugin.loaded) {
                loadPlugin(pluginId).join();
            }
            
            if (listener != null) {
                listener.onPluginEnabled(plugin);
            }
            return true;
        });
    }
    
    /**
     * 禁用插件
     */
    public CompletableFuture<Boolean> disablePlugin(String pluginId) {
        PluginInfo plugin = plugins.get(pluginId);
        if (plugin == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            plugin.enabled = false;
            
            if (plugin.loaded) {
                unloadPlugin(pluginId).join();
            }
            
            if (listener != null) {
                listener.onPluginDisabled(plugin);
            }
            return true;
        });
    }
    
    /**
     * 安装插件
     */
    public CompletableFuture<PluginInfo> installPlugin(String pluginId, String version) {
        return CompletableFuture.supplyAsync(() -> {
            // 模拟安装过程
            PluginInfo plugin = new PluginInfo();
            plugin.id = pluginId;
            plugin.name = pluginId;
            plugin.version = version != null ? version : "1.0.0";
            plugin.description = "插件 " + pluginId;
            plugin.enabled = true;
            plugin.loaded = false;
            
            plugins.put(pluginId, plugin);
            
            if (listener != null) {
                listener.onPluginInstalled(plugin);
            }
            
            return plugin;
        });
    }
    
    /**
     * 卸载插件（安装的反向操作）
     */
    public CompletableFuture<Boolean> uninstallPlugin(String pluginId) {
        return unloadPlugin(pluginId)
            .thenApply(unloaded -> {
                if (unloaded) {
                    PluginInfo plugin = plugins.remove(pluginId);
                    if (listener != null) {
                        listener.onPluginUninstalled(plugin);
                    }
                    return true;
                }
                return false;
            });
    }
    
    /**
     * 检查插件更新
     */
    public CompletableFuture<String> checkForUpdates(String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            PluginInfo plugin = plugins.get(pluginId);
            if (plugin == null) {
                return null;
            }
            // 模拟检查更新
            // 实际实现应该查询插件仓库
            return null; // 没有更新
        });
    }
    
    /**
     * 更新插件
     */
    public CompletableFuture<Boolean> updatePlugin(String pluginId, String newVersion) {
        return CompletableFuture.supplyAsync(() -> {
            PluginInfo plugin = plugins.get(pluginId);
            if (plugin == null) {
                return false;
            }
            
            // 模拟更新过程
            plugin.version = newVersion;
            
            if (listener != null) {
                listener.onPluginUpdated(plugin, newVersion);
            }
            
            return true;
        });
    }
    
    /**
     * 插件监听器接口
     */
    public interface PluginListener {
        void onPluginRegistered(PluginInfo plugin);
        void onPluginUnregistered(PluginInfo plugin);
        void onPluginLoaded(PluginInfo plugin);
        void onPluginUnloaded(PluginInfo plugin);
        void onPluginEnabled(PluginInfo plugin);
        void onPluginDisabled(PluginInfo plugin);
        void onPluginInstalled(PluginInfo plugin);
        void onPluginUninstalled(PluginInfo plugin);
        void onPluginUpdated(PluginInfo plugin, String newVersion);
        void onPluginError(PluginInfo plugin, Throwable error);
    }
    
    /**
     * 插件信息类
     */
    public static class PluginInfo {
        public String id;
        public String name;
        public String version;
        public String description;
        public String author;
        public List<String> keywords;
        public Map<String, Object> settings;
        public boolean enabled;
        public boolean loaded;
        public String dependencies;
        
        public PluginInfo() {
            this.keywords = new ArrayList<>();
            this.settings = new HashMap<>();
            this.enabled = false;
            this.loaded = false;
        }
        
        public void addKeyword(String keyword) {
            this.keywords.add(keyword);
        }
        
        public void setSetting(String key, Object value) {
            this.settings.put(key, value);
        }
        
        public Object getSetting(String key) {
            return this.settings.get(key);
        }
    }
}