package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 技能目录缓存 — 两层缓存架构。
 *
 * <p>第一层：内存 {@link ConcurrentHashMap}（快速访问）。
 * 第二层：磁盘快照 {@code ~/.jwcode/skills/.skills_prompt_cache.json}（跨会话复用）。
 *
 * <p>缓存键由技能列表哈希和禁用集合哈希组成。
 * 当技能注册发生变化时通过 {@link #invalidate()} 清除。
 */
public class SkillsPromptCache {

    private static final Logger logger = Logger.getLogger(SkillsPromptCache.class.getName());

    private final Path cacheFile;
    private final ConcurrentHashMap<String, String> memoryCache = new ConcurrentHashMap<>();
    private String lastSkillsHash = "";
    private String lastDisabledHash = "";

    public SkillsPromptCache() {
        this(Path.of(System.getProperty("user.home"), ".jwcode", "skills", ".skills_prompt_cache.json"));
    }

    public SkillsPromptCache(Path cacheFile) {
        this.cacheFile = cacheFile;
        loadFromDisk();
    }

    /**
     * 获取缓存的技能目录字符串。
     *
     * @param summaries     当前技能摘要列表
     * @param disabledSkills 被禁用的技能 ID 集合
     * @return 缓存的目录字符串，如果缓存未命中则返回 null
     */
    public String get(List<SkillSummary> summaries, Set<String> disabledSkills) {
        String key = buildKey(summaries, disabledSkills);
        String cached = memoryCache.get(key);
        if (cached != null) {
            return cached;
        }
        // 尝试从磁盘加载
        if (key.equals(lastSkillsHash + "|" + lastDisabledHash)) {
            return memoryCache.get(key); // disk cache already loaded into memory
        }
        return null;
    }

    /**
     * 设置缓存。
     */
    public void set(List<SkillSummary> summaries, Set<String> disabledSkills, String prompt) {
        String key = buildKey(summaries, disabledSkills);
        memoryCache.put(key, prompt);
        persistToDisk(key, prompt);
    }

    /**
     * 使所有缓存失效。
     */
    public void invalidate() {
        memoryCache.clear();
        lastSkillsHash = "";
        lastDisabledHash = "";
        try {
            Files.deleteIfExists(cacheFile);
        } catch (IOException e) {
            logger.fine("[SkillsPromptCache] 删除缓存文件失败: " + e.getMessage());
        }
    }

    private String buildKey(List<SkillSummary> summaries, Set<String> disabledSkills) {
        String skillsHash = Integer.toHexString(summaries.hashCode());
        String disabledHash = Integer.toHexString(disabledSkills.hashCode());
        return skillsHash + "|" + disabledHash;
    }

    private synchronized void persistToDisk(String key, String prompt) {
        try {
            Files.createDirectories(cacheFile.getParent());
            String json = "{\"key\":\"" + escape(key) + "\",\"prompt\":" +
                escape(prompt) + "}";
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.fine("[SkillsPromptCache] 持久化失败: " + e.getMessage());
        }
    }

    private synchronized void loadFromDisk() {
        if (!Files.isRegularFile(cacheFile)) return;
        try {
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            // 简易 JSON 解析: {"key":"...","prompt":"..."}
            int keyStart = json.indexOf("\"key\":\"");
            if (keyStart < 0) return;
            keyStart += 7;
            int keyEnd = json.indexOf("\"", keyStart);
            if (keyEnd < 0) return;
            String key = unescape(json.substring(keyStart, keyEnd));

            int promptStart = json.indexOf("\"prompt\":\"");
            if (promptStart < 0) return;
            promptStart += 10;
            int promptEnd = json.lastIndexOf("\"}");
            if (promptEnd < 0) return;
            String prompt = unescape(json.substring(promptStart, promptEnd));

            memoryCache.put(key, prompt);
            String[] parts = key.split("\\|");
            if (parts.length == 2) {
                lastSkillsHash = parts[0];
                lastDisabledHash = parts[1];
            }
        } catch (IOException e) {
            logger.fine("[SkillsPromptCache] 从磁盘加载失败: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\t", "\t").replace("\\r", "\r")
                .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
