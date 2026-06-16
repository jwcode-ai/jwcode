package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能使用追踪器 — 记录每个技能的使用/查看/修改情况。
 *
 * <p>数据持久化到 {@code ~/.jwcode/skills/.usage.json}，使用原子写入防止文件损坏。
 */
public class SkillUsageTracker {
    private static final Logger logger = Logger.getLogger(SkillUsageTracker.class.getName());

    private final Path storePath;
    private final Map<String, UsageRecord> records = new ConcurrentHashMap<>();

    public SkillUsageTracker() {
        this(Path.of(System.getProperty("user.home"), ".jwcode", "skills", ".usage.json"));
    }

    public SkillUsageTracker(Path storePath) {
        this.storePath = storePath;
        load();
    }

    /**
     * 记录一次技能使用。
     */
    public void recordUse(String skillId) {
        UsageRecord rec = getOrCreate(skillId);
        rec.recordUse();
        save();
    }

    /**
     * 记录一次技能查看。
     */
    public void recordView(String skillId) {
        UsageRecord rec = getOrCreate(skillId);
        rec.recordView();
        save();
    }

    /**
     * 记录一次技能修改。
     */
    public void recordPatch(String skillId) {
        UsageRecord rec = getOrCreate(skillId);
        rec.recordPatch();
        save();
    }

    /**
     * 获取技能的使用记录。
     */
    public Optional<UsageRecord> getRecord(String skillId) {
        return Optional.ofNullable(records.get(skillId));
    }

    /**
     * 获取所有使用记录。
     */
    public Collection<UsageRecord> getAllRecords() {
        return records.values();
    }

    /**
     * 获取使用次数最多的技能 ID（降序）。
     */
    public List<String> getMostUsed(int limit) {
        return records.values().stream()
            .sorted((a, b) -> Integer.compare(b.getUseCount(), a.getUseCount()))
            .map(UsageRecord::getId)
            .limit(limit)
            .toList();
    }

    /**
     * 获取指定来源类型的所有使用记录。
     */
    public List<UsageRecord> getByState(String state) {
        return records.values().stream()
            .filter(r -> state.equals(r.getState()))
            .toList();
    }

    private UsageRecord getOrCreate(String skillId) {
        return records.computeIfAbsent(skillId, UsageRecord::new);
    }

    private synchronized void save() {
        try {
            Files.createDirectories(storePath.getParent());
            Path tmp = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            var iter = records.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                UsageRecord r = entry.getValue();
                sb.append("  \"").append(escape(entry.getKey())).append("\": {");
                sb.append("\"useCount\":").append(r.getUseCount()).append(",");
                sb.append("\"viewCount\":").append(r.getViewCount()).append(",");
                sb.append("\"patchCount\":").append(r.getPatchCount()).append(",");
                sb.append("\"lastUsedAt\":").append(r.getLastUsedAt()).append(",");
                sb.append("\"lastViewedAt\":").append(r.getLastViewedAt()).append(",");
                sb.append("\"lastPatchedAt\":").append(r.getLastPatchedAt()).append(",");
                sb.append("\"createdAt\":").append(r.getCreatedAt()).append(",");
                sb.append("\"state\":\"").append(r.getState()).append("\",");
                sb.append("\"pinned\":").append(r.isPinned());
                sb.append("}");
                if (iter.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append("}\n");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, storePath, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("[SkillUsageTracker] 持久化失败: " + e.getMessage());
        }
    }

    private synchronized void load() {
        if (!Files.isRegularFile(storePath)) return;
        try {
            String content = Files.readString(storePath, StandardCharsets.UTF_8);
            // 简易 JSON 对象解析
            String json = content.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1).trim();
                if (!json.isEmpty()) {
                    parseRecords(json);
                }
            }
        } catch (IOException e) {
            logger.warning("[SkillUsageTracker] 加载失败: " + e.getMessage());
        }
    }

    private void parseRecords(String json) {
        // 匹配 "skillId": {...}
        Pattern entryPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher m = entryPattern.matcher(json);
        while (m.find()) {
            String id = unescape(m.group(1));
            String fields = m.group(2);
            UsageRecord rec = new UsageRecord(id);
            // 解析字段
            Pattern fieldPattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*([^,\"}]+)");
            Matcher fm = fieldPattern.matcher(fields);
            while (fm.find()) {
                String key = fm.group(1);
                String val = fm.group(2).trim();
                switch (key) {
                    case "useCount" -> rec.setUseCount(parseInt(val, 0));
                    case "viewCount" -> rec.setViewCount(parseInt(val, 0));
                    case "patchCount" -> rec.setPatchCount(parseInt(val, 0));
                    case "lastUsedAt" -> rec.setLastUsedAt(parseLong(val, 0));
                    case "lastViewedAt" -> rec.setLastViewedAt(parseLong(val, 0));
                    case "lastPatchedAt" -> rec.setLastPatchedAt(parseLong(val, 0));
                    case "createdAt" -> rec.setCreatedAt(parseLong(val, 0));
                    case "state" -> rec.setState(val.replace("\"", ""));
                    case "pinned" -> rec.setPinned("true".equals(val));
                }
            }
            records.put(id, rec);
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
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
}
