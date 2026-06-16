package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 技能来源存储 — 将技能的 provenance 信息持久化到侧边文件。
 *
 * <p>存储位置：~/.jwcode/skills/.provenance.json
 * 使用原子写入防止文件损坏。
 *
 * @deprecated 来源信息已内置于 {@link SkillDefinition} 和 {@link Skill} 的 provenance 字段中。
 *   保留此类用于向后兼容，但新代码应直接使用 Skill.getProvenance()。
 */
@Deprecated
public class ProvenanceStore {
    private static final Logger logger = Logger.getLogger(ProvenanceStore.class.getName());

    private final Path storePath;
    private final Map<String, Skill.Provenance> cache = new ConcurrentHashMap<>();

    public ProvenanceStore() {
        this(Path.of(System.getProperty("user.home"), ".jwcode", "skills", ".provenance.json"));
    }

    public ProvenanceStore(Path storePath) {
        this.storePath = storePath;
        load();
    }

    public void setProvenance(String skillId, Skill.Provenance provenance) {
        cache.put(skillId, provenance);
    }

    public Skill.Provenance getProvenance(String skillId) {
        return cache.getOrDefault(skillId, Skill.Provenance.USER_MANUAL);
    }

    public Set<String> getByProvenance(Skill.Provenance provenance) {
        Set<String> result = new HashSet<>();
        for (var entry : cache.entrySet()) {
            if (entry.getValue() == provenance) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(storePath.getParent());
            Path tmp = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            var sb = new StringBuilder();
            sb.append("{\n");
            var iter = cache.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                sb.append("  \"").append(escape(entry.getKey())).append("\": \"")
                  .append(entry.getValue().name()).append("\"");
                if (iter.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append("}\n");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, storePath, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("[ProvenanceStore] 持久化失败: " + e.getMessage());
        }
    }

    private synchronized void load() {
        if (!Files.isRegularFile(storePath)) return;
        try {
            String content = Files.readString(storePath, StandardCharsets.UTF_8);
            String json = content.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1).trim();
                if (!json.isEmpty()) {
                    for (var pair : json.split(",")) {
                        int colon = pair.indexOf(':');
                        if (colon > 0) {
                            String key = unescape(pair.substring(0, colon).trim());
                            String value = unescape(pair.substring(colon + 1).trim());
                            if (key.startsWith("\"") && key.endsWith("\"")) {
                                key = key.substring(1, key.length() - 1);
                            }
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            try {
                                cache.put(key, Skill.Provenance.valueOf(value));
                            } catch (IllegalArgumentException e) {
                                logger.fine("[ProvenanceStore] 未知来源: " + value);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("[ProvenanceStore] 加载失败: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\t", "\t").replace("\\r", "\r")
                .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
