package com.jwcode.core.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.plugin.api.PluginManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 插件清单读取器 — 从 plugin.json 解析 PluginManifest。
 */
public class PluginManifestReader {
    private static final Logger logger = Logger.getLogger(PluginManifestReader.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从目录读取 plugin.json。
     *
     * @param pluginDir 插件目录
     * @return 解析后的 PluginManifest，失败返回 null
     */
    public static PluginManifest read(Path pluginDir) {
        Path manifestFile = pluginDir.resolve("plugin.json");
        if (!Files.exists(manifestFile)) {
            logger.fine("[PluginManifestReader] 未找到 plugin.json: " + pluginDir);
            return null;
        }

        try {
            String json = Files.readString(manifestFile);
            return mapper.readValue(json, PluginManifest.class);
        } catch (IOException e) {
            logger.warning("[PluginManifestReader] 解析失败: " + manifestFile + " — " + e.getMessage());
            return null;
        }
    }

    /**
     * 验证清单的必要字段。
     */
    public static boolean isValid(PluginManifest manifest) {
        if (manifest == null) return false;
        if (manifest.id() == null || manifest.id().isBlank()) return false;
        if (manifest.name() == null || manifest.name().isBlank()) return false;
        if (manifest.version() == null || manifest.version().isBlank()) return false;
        if (manifest.entryClass() == null || manifest.entryClass().isBlank()) return false;
        return true;
    }
}
