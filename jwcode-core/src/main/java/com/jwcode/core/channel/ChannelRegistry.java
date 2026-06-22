package com.jwcode.core.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 渠道注册表：管理渠道配置的持久化与适配器实例的生命周期。
 * 配置持久化到 ~/.jwcode/channels.json
 */
public class ChannelRegistry {

    private static final Logger log = Logger.getLogger(ChannelRegistry.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path configFile;
    /** type -> 适配器工厂 */
    private final Map<String, Function<ChannelConfig, ChannelAdapter>> factories = new ConcurrentHashMap<>();
    /** id -> 配置 */
    private final Map<String, ChannelConfig> configs = new ConcurrentHashMap<>();
    /** id -> 运行中的适配器 */
    private final Map<String, ChannelAdapter> adapters = new ConcurrentHashMap<>();

    public ChannelRegistry() {
        this(Path.of(System.getProperty("user.home"), ".jwcode", "channels.json"));
    }

    public ChannelRegistry(Path configFile) {
        this.configFile = configFile;
    }

    public void registerFactory(String type, Function<ChannelConfig, ChannelAdapter> factory) {
        factories.put(type, factory);
    }

    /** 启动时加载配置并激活已启用的渠道 */
    public void load() {
        if (!Files.exists(configFile)) return;
        try {
            List<ChannelConfig> list = MAPPER.readValue(configFile.toFile(),
                    new TypeReference<>() {});
            for (ChannelConfig c : list) {
                configs.put(c.id, c);
                if (c.enabled) startAdapter(c);
            }
        } catch (IOException e) {
            log.warning("[ChannelRegistry] Failed to load config: " + e.getMessage());
        }
    }

    /** 新增渠道 */
    public ChannelConfig add(ChannelConfig config) {
        if (config.id == null) config.id = UUID.randomUUID().toString();
        configs.put(config.id, config);
        save();
        if (config.enabled) startAdapter(config);
        return config;
    }

    /** 更新渠道（重启适配器） */
    public boolean update(ChannelConfig config) {
        if (!configs.containsKey(config.id)) return false;
        stopAdapter(config.id);
        configs.put(config.id, config);
        save();
        if (config.enabled) startAdapter(config);
        return true;
    }

    /** 删除渠道 */
    public boolean remove(String id) {
        if (!configs.containsKey(id)) return false;
        stopAdapter(id);
        configs.remove(id);
        save();
        return true;
    }

    /** 启用/停用渠道 */
    public boolean toggle(String id, boolean enabled) {
        ChannelConfig c = configs.get(id);
        if (c == null) return false;
        c.enabled = enabled;
        save();
        if (enabled) startAdapter(c);
        else stopAdapter(id);
        return true;
    }

    public List<ChannelConfig> listAll() {
        return new ArrayList<>(configs.values());
    }

    public Optional<ChannelConfig> getConfig(String id) {
        return Optional.ofNullable(configs.get(id));
    }

    public Optional<ChannelAdapter> getAdapter(String id) {
        return Optional.ofNullable(adapters.get(id));
    }

    public Collection<ChannelAdapter> allAdapters() {
        return adapters.values();
    }

    /** 供 ChannelMessageDispatcher 回调：发送消息到指定渠道用户 */
    public void send(String channelId, String recipientId, String text) {
        ChannelAdapter a = adapters.get(channelId);
        if (a != null) {
            try {
                a.send(recipientId, text);
            } catch (Exception e) {
                log.log(Level.FINE, "[ChannelRegistry] send failed silently: channel=" + channelId
                    + " to=" + recipientId, e);
            }
        }
    }

    /** 持久化当前配置（适配器运行时更新了 config.extra，如微信 bot_token，需落盘） */
    public void persist() {
        save();
    }

    public void shutdown() {
        adapters.values().forEach(a -> {
            try { a.shutdown(); } catch (Exception e) {
                log.log(Level.WARNING, "[ChannelRegistry] Adapter shutdown error", e);
            }
        });
        adapters.clear();
    }

    // ── private ──────────────────────────────────────────────

    private void startAdapter(ChannelConfig config) {
        Function<ChannelConfig, ChannelAdapter> factory = factories.get(config.type);
        if (factory == null) {
            log.warning("[ChannelRegistry] No factory for type: " + config.type);
            return;
        }
        try {
            ChannelAdapter adapter = factory.apply(config);
            adapter.initialize(config);
            adapters.put(config.id, adapter);
            log.info("[ChannelRegistry] Started adapter: " + config.name + " (" + config.type + ")");
        } catch (Exception e) {
            log.warning("[ChannelRegistry] Failed to start adapter " + config.name + ": " + e.getMessage());
        }
    }

    private void stopAdapter(String id) {
        ChannelAdapter a = adapters.remove(id);
        if (a != null) {
            try { a.shutdown(); } catch (Exception e) {
                log.log(Level.WARNING, "[ChannelRegistry] Error stopping adapter " + id, e);
            }
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(configFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(configFile.toFile(), new ArrayList<>(configs.values()));
            log.fine("[ChannelRegistry] Config saved (" + configs.size() + " channels)");
        } catch (IOException e) {
            log.log(Level.WARNING, "[ChannelRegistry] Failed to save config", e);
        }
    }
}
