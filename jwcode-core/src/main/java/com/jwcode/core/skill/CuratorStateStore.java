package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 策展人状态存储 — 记录技能生命周期状态、最后检查时间和计数。
 *
 * <p>持久化到 {@code ~/.jwcode/skills/.curator_state}。
 */
public class CuratorStateStore {

    private static final Logger logger = Logger.getLogger(CuratorStateStore.class.getName());

    private final Path storePath;
    private final Map<String, SkillLifecycleState> states = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUsedMap = new ConcurrentHashMap<>();
    private long lastCuratorRun = 0;
    private int totalRuns = 0;

    public CuratorStateStore() {
        this(Path.of(System.getProperty("user.home"), ".jwcode", "skills", ".curator_state"));
    }

    public CuratorStateStore(Path storePath) {
        this.storePath = storePath;
        load();
    }

    public void setState(String skillId, SkillLifecycleState state) {
        states.put(skillId, state);
    }

    public SkillLifecycleState getState(String skillId) {
        return states.getOrDefault(skillId, SkillLifecycleState.ACTIVE);
    }

    public void recordUse(String skillId) {
        lastUsedMap.put(skillId, Instant.now().toEpochMilli());
        states.putIfAbsent(skillId, SkillLifecycleState.ACTIVE);
    }

    public long getLastUsed(String skillId) {
        return lastUsedMap.getOrDefault(skillId, 0L);
    }

    public Set<String> getSkillsInState(SkillLifecycleState state) {
        Set<String> result = new HashSet<>();
        for (var entry : states.entrySet()) {
            if (entry.getValue() == state) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public List<String> getStaleCandidates(long staleThresholdMs, long archivedThresholdMs) {
        long now = Instant.now().toEpochMilli();
        List<String> candidates = new ArrayList<>();
        for (var entry : states.entrySet()) {
            String id = entry.getKey();
            SkillLifecycleState state = entry.getValue();
            if (state == SkillLifecycleState.PINNED) continue;
            long lastUsed = lastUsedMap.getOrDefault(id, 0L);
            long age = now - lastUsed;
            if (state == SkillLifecycleState.STALE && age > archivedThresholdMs) {
                candidates.add(id);
            } else if (state == SkillLifecycleState.ACTIVE && age > staleThresholdMs) {
                candidates.add(id);
            }
        }
        return candidates;
    }

    public long getLastCuratorRun() { return lastCuratorRun; }
    public void setLastCuratorRun(long t) { this.lastCuratorRun = t; }
    public int getTotalRuns() { return totalRuns; }

    public synchronized void save() {
        try {
            Files.createDirectories(storePath.getParent());
            Path tmp = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"lastCuratorRun\": ").append(lastCuratorRun).append(",\n");
            sb.append("  \"totalRuns\": ").append(totalRuns).append(",\n");
            sb.append("  \"states\": {\n");
            var sIter = states.entrySet().iterator();
            while (sIter.hasNext()) {
                var e = sIter.next();
                sb.append("    \"").append(escape(e.getKey())).append("\": \"")
                  .append(e.getValue().name()).append("\"");
                if (sIter.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append("  },\n");
            sb.append("  \"lastUsed\": {\n");
            var uIter = lastUsedMap.entrySet().iterator();
            while (uIter.hasNext()) {
                var e = uIter.next();
                sb.append("    \"").append(escape(e.getKey())).append("\": ").append(e.getValue());
                if (uIter.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append("  }\n");
            sb.append("}\n");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, storePath, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("[CuratorStateStore] 保存失败: " + e.getMessage());
        }
    }

    public synchronized void load() {
        if (!Files.isRegularFile(storePath)) return;
        try {
            String content = Files.readString(storePath, StandardCharsets.UTF_8);
            // 简易 JSON 解析
            int lcr = content.indexOf("\"lastCuratorRun\":");
            if (lcr >= 0) {
                int vStart = content.indexOf(':', lcr) + 1;
                int vEnd = content.indexOf(',', vStart);
                if (vEnd < 0) vEnd = content.indexOf('}', vStart);
                lastCuratorRun = Long.parseLong(content.substring(vStart, vEnd).trim());
            }
            int tr = content.indexOf("\"totalRuns\":");
            if (tr >= 0) {
                int vStart = content.indexOf(':', tr) + 1;
                int vEnd = content.indexOf(',', vStart);
                if (vEnd < 0) vEnd = content.indexOf('}', vStart);
                totalRuns = Integer.parseInt(content.substring(vStart, vEnd).trim());
            }
            // 解析 states
            int ss = content.indexOf("\"states\"");
            if (ss >= 0) {
                int ob = content.indexOf('{', ss);
                int cb = content.indexOf('}', ob);
                if (ob >= 0 && cb > ob) {
                    String block = content.substring(ob + 1, cb);
                    for (var pair : block.split(",")) {
                        pair = pair.trim();
                        if (pair.isEmpty()) continue;
                        int colon = pair.indexOf(':');
                        if (colon < 0) continue;
                        String k = unescape(pair.substring(0, colon).trim());
                        String v = unescape(pair.substring(colon + 1).trim());
                        k = stripQuotes(k);
                        v = stripQuotes(v);
                        try {
                            states.put(k, SkillLifecycleState.valueOf(v));
                        } catch (IllegalArgumentException ignored) {
                            // Skip invalid enum value — may be from older format
                        }
                    }
                }
            }
            // 解析 lastUsed
            int lu = content.indexOf("\"lastUsed\"");
            if (lu >= 0) {
                int ob = content.indexOf('{', lu);
                int cb = content.indexOf('}', ob);
                if (ob >= 0 && cb > ob) {
                    String block = content.substring(ob + 1, cb);
                    for (var pair : block.split(",")) {
                        pair = pair.trim();
                        if (pair.isEmpty()) continue;
                        int colon = pair.indexOf(':');
                        if (colon < 0) continue;
                        String k = unescape(pair.substring(0, colon).trim());
                        String v = pair.substring(colon + 1).trim();
                        k = stripQuotes(k);
                        lastUsedMap.put(k, Long.parseLong(v));
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[CuratorStateStore] 加载失败: " + e.getMessage());
        }
    }

    public synchronized void incrementRuns() {
        totalRuns++;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r");
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
