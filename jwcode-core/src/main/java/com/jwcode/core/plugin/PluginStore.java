package com.jwcode.core.plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 插件状态存储 — 维护已加载/已启用/已禁用插件的状态。
 */
public class PluginStore {
    private static final Logger logger = Logger.getLogger(PluginStore.class.getName());

    public enum State { LOADED, ENABLED, DISABLED, ERROR }

    private final Map<String, State> pluginStates = new ConcurrentHashMap<>();
    private final Map<String, String> errorMessages = new ConcurrentHashMap<>();
    private final Set<String> loadedPluginIds = ConcurrentHashMap.newKeySet();

    public void markLoaded(String pluginId) {
        pluginStates.put(pluginId, State.LOADED);
        loadedPluginIds.add(pluginId);
    }

    public void markEnabled(String pluginId) {
        pluginStates.put(pluginId, State.ENABLED);
    }

    public void markDisabled(String pluginId) {
        pluginStates.put(pluginId, State.DISABLED);
    }

    public void markError(String pluginId, String error) {
        pluginStates.put(pluginId, State.ERROR);
        errorMessages.put(pluginId, error);
        logger.warning("[PluginStore] 插件错误: " + pluginId + " — " + error);
    }

    public void remove(String pluginId) {
        pluginStates.remove(pluginId);
        errorMessages.remove(pluginId);
        loadedPluginIds.remove(pluginId);
    }

    public State getState(String pluginId) {
        return pluginStates.getOrDefault(pluginId, null);
    }

    public String getError(String pluginId) {
        return errorMessages.get(pluginId);
    }

    public boolean isLoaded(String pluginId) {
        return loadedPluginIds.contains(pluginId);
    }

    public boolean isEnabled(String pluginId) {
        return pluginStates.get(pluginId) == State.ENABLED;
    }

    public Set<String> getLoadedPluginIds() {
        return Collections.unmodifiableSet(new HashSet<>(loadedPluginIds));
    }

    public Map<String, State> getAllStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pluginStates));
    }

    public void clear() {
        pluginStates.clear();
        errorMessages.clear();
        loadedPluginIds.clear();
    }
}
