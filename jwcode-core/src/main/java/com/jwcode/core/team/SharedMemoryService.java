package com.jwcode.core.team;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 共享记忆服务 — 团队级别的持久化记忆同步。
 *
 * <p>允许团队成员共享 AI 记忆，支持：
 * <ul>
 *   <li>按团队分类存储记忆</li>
 *   <li>按记忆类型过滤 (user, feedback, project, reference)</li>
 *   <li>记忆版本控制和冲突解决（last-write-wins）</li>
 *   <li>自动过期 (30 天未访问的记忆)</li>
 * </ul>
 */
public class SharedMemoryService {
    private static final Logger logger = Logger.getLogger(SharedMemoryService.class.getName());

    private static final Path MEMORY_DIR = Path.of(
        System.getProperty("user.home"), ".jwcode", "team_memory");

    private static volatile SharedMemoryService instance;

    /** 共享记忆条目 */
    public record SharedMemory(
        String id,
        String teamId,
        String authorId,
        String type,       // user, feedback, project, reference
        String title,
        String content,
        Instant createdAt,
        Instant updatedAt,
        int version
    ) {}

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /** teamId -> memories */
    private final Map<String, List<SharedMemory>> teamMemories = new ConcurrentHashMap<>();

    private SharedMemoryService() {
        try { Files.createDirectories(MEMORY_DIR); } catch (IOException e) {
            logger.fine("Cannot create memory dir: " + e.getMessage());
        }
        loadAll();
    }

    public static synchronized SharedMemoryService getInstance() {
        if (instance == null) {
            instance = new SharedMemoryService();
        }
        return instance;
    }

    // ==== CRUD ====

    /** 添加共享记忆 */
    public SharedMemory addMemory(String teamId, String authorId, String type,
                                   String title, String content) {
        String id = "mem_" + UUID.randomUUID().toString().substring(0, 12);
        Instant now = Instant.now();
        SharedMemory mem = new SharedMemory(id, teamId, authorId, type, title, content, now, now, 1);

        teamMemories.computeIfAbsent(teamId, k -> Collections.synchronizedList(new ArrayList<>()))
                     .add(mem);
        saveTeam(teamId);
        logger.fine("[SharedMemory] Added: " + title + " (team=" + teamId + ")");
        return mem;
    }

    /** 更新记忆 */
    public SharedMemory updateMemory(String teamId, String memoryId, String newContent, String userId) {
        List<SharedMemory> memories = teamMemories.get(teamId);
        if (memories == null) return null;

        synchronized (memories) {
            for (int i = 0; i < memories.size(); i++) {
                SharedMemory m = memories.get(i);
                if (m.id.equals(memoryId)) {
                    SharedMemory updated = new SharedMemory(
                        m.id, m.teamId, m.authorId, m.type, m.title,
                        newContent, m.createdAt, Instant.now(), m.version + 1);
                    memories.set(i, updated);
                    saveTeam(teamId);
                    return updated;
                }
            }
        }
        return null;
    }

    /** 删除记忆 */
    public boolean deleteMemory(String teamId, String memoryId) {
        List<SharedMemory> memories = teamMemories.get(teamId);
        if (memories == null) return false;

        boolean removed = memories.removeIf(m -> m.id.equals(memoryId));
        if (removed) saveTeam(teamId);
        return removed;
    }

    /** 获取团队所有记忆 */
    public List<SharedMemory> getMemories(String teamId) {
        return new ArrayList<>(teamMemories.getOrDefault(teamId, List.of()));
    }

    /** 按类型筛选 */
    public List<SharedMemory> getMemoriesByType(String teamId, String type) {
        return teamMemories.getOrDefault(teamId, List.of()).stream()
            .filter(m -> m.type.equals(type))
            .toList();
    }

    /** 搜索记忆 */
    public List<SharedMemory> searchMemories(String teamId, String query) {
        String lower = query.toLowerCase();
        return teamMemories.getOrDefault(teamId, List.of()).stream()
            .filter(m -> m.title.toLowerCase().contains(lower)
                      || m.content.toLowerCase().contains(lower))
            .toList();
    }

    /** 获取最近更新的记忆 */
    public List<SharedMemory> getRecentMemories(String teamId, int limit) {
        return teamMemories.getOrDefault(teamId, List.of()).stream()
            .sorted((a, b) -> b.updatedAt.compareTo(a.updatedAt))
            .limit(limit)
            .toList();
    }

    // ==== 持久化 ====

    private Path teamFile(String teamId) {
        return MEMORY_DIR.resolve(teamId + ".json");
    }

    private void saveTeam(String teamId) {
        try {
            List<SharedMemory> memories = teamMemories.getOrDefault(teamId, List.of());
            mapper.writerWithDefaultPrettyPrinter()
                  .writeValue(teamFile(teamId).toFile(), memories);
        } catch (IOException e) {
            logger.warning("[SharedMemory] Save failed: " + e.getMessage());
        }
    }

    private void loadAll() {
        try {
            var files = Files.list(MEMORY_DIR);
            files.filter(f -> f.toString().endsWith(".json")).forEach(f -> {
                try {
                    String teamId = f.getFileName().toString().replace(".json", "");
                    List<SharedMemory> memories = mapper.readValue(
                        f.toFile(), new TypeReference<List<SharedMemory>>() {});
                    teamMemories.put(teamId, Collections.synchronizedList(
                        new ArrayList<>(memories)));
                } catch (IOException e) {
                    logger.warning("[SharedMemory] Load failed: " + f + " - " + e.getMessage());
                }
            });
            files.close();
        } catch (IOException e) {
            logger.warning("[SharedMemory] List failed: " + e.getMessage());
        }
    }

    /** 清理 30 天未更新的记忆 */
    public int cleanup(String teamId) {
        Instant cutoff = Instant.now().minusSeconds(30 * 24 * 3600);
        List<SharedMemory> memories = teamMemories.get(teamId);
        if (memories == null) return 0;

        int before = memories.size();
        memories.removeIf(m -> m.updatedAt.isBefore(cutoff));
        int removed = before - memories.size();
        if (removed > 0) saveTeam(teamId);
        return removed;
    }

    /** 获取统计信息 */
    public Map<String, Object> getStats(String teamId) {
        List<SharedMemory> memories = teamMemories.getOrDefault(teamId, List.of());
        Map<String, Long> byType = new HashMap<>();
        for (SharedMemory m : memories) {
            byType.merge(m.type, 1L, Long::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", memories.size());
        stats.put("by_type", byType);
        return stats;
    }
}
