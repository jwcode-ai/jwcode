package com.jwcode.core.plugin;

import com.jwcode.plugin.api.PluginManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 插件扫描器 — 扫描插件目录，发现可用插件。
 *
 * <p>扫描路径（优先级从高到低）：
 * <ol>
 *   <li>{@code ~/.jwcode/plugins/} — 用户级插件</li>
 *   <li>{@code ./.jwcode/plugins/} — 项目级插件</li>
 * </ol>
 */
public class PluginScanner {
    private static final Logger logger = Logger.getLogger(PluginScanner.class.getName());

    /**
     * 扫描结果：插件目录 → 清单
     */
    public record ScannedPlugin(Path directory, PluginManifest manifest) {}

    /**
     * 扫描所有插件目录。
     */
    public static List<ScannedPlugin> scanAll() {
        List<ScannedPlugin> result = new ArrayList<>();

        String userHome = System.getProperty("user.home");
        Path userPluginsDir = Path.of(userHome, ".jwcode", "plugins");
        scanDirectory(userPluginsDir, result);

        Path projectPluginsDir = Path.of(".jwcode", "plugins");
        scanDirectory(projectPluginsDir, result);

        logger.info("[PluginScanner] 发现 " + result.size() + " 个插件");
        return result;
    }

    /**
     * 扫描指定目录。
     */
    public static List<ScannedPlugin> scanDirectory(Path dir) {
        List<ScannedPlugin> result = new ArrayList<>();
        scanDirectory(dir, result);
        return result;
    }

    private static void scanDirectory(Path dir, List<ScannedPlugin> result) {
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isDirectory)
                .forEach(subDir -> {
                    PluginManifest manifest = PluginManifestReader.read(subDir);
                    if (manifest != null && PluginManifestReader.isValid(manifest)) {
                        result.add(new ScannedPlugin(subDir, manifest));
                        logger.fine("[PluginScanner] 发现: " + manifest.id()
                            + " v" + manifest.version() + " in " + subDir);
                    }
                });
        } catch (IOException e) {
            logger.warning("[PluginScanner] 扫描失败: " + dir + " — " + e.getMessage());
        }
    }
}
