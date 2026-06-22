package com.jwcode.core.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.session.Session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 会话分享服务 — 在团队成员之间共享会话摘要和上下文。
 *
 * <p>支持：
 * <ul>
 *   <li>发布会话摘要到团队空间</li>
 *   <li>查看团队活动的会话摘要</li>
 *   <li>基于共享会话上下文快速上手</li>
 *   <li>会话模板和最佳实践分享</li>
 * </ul>
 */
public class SessionSharingService {
    private static final Logger logger = Logger.getLogger(SessionSharingService.class.getName());

    private static final Path SHARE_DIR = Path.of(
        System.getProperty("user.home"), ".jwcode", "shared_sessions");

    private static volatile SessionSharingService instance;

    /** 分享的会话摘要 */
    public record SharedSession(
        String shareId,
        String teamId,
        String ownerId,
        String sessionId,
        String title,
        String summary,        // AI 生成的摘要
        String goal,           // 会话目标
        String outcome,        // 结果
        List<String> keyFiles, // 涉及的关键文件
        List<String> tags,     // 标签
        Instant createdAt,
        Instant sharedAt,
        int viewCount,
        int forkCount
    ) {}

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /** teamId -> shared sessions */
    private final Map<String, List<SharedSession>> sharedSessions = new ConcurrentHashMap<>();

    private SessionSharingService() {
        try { Files.createDirectories(SHARE_DIR); } catch (IOException ignored) {
            logger.fine("Cannot create share directory: " + SHARE_DIR);
        }
        loadAll();
    }

    public static synchronized SessionSharingService getInstance() {
        if (instance == null) {
            instance = new SessionSharingService();
        }
        return instance;
    }

    // ==== 发布/查看 ====

    /** 分享会话到团队 */
    public SharedSession shareSession(String teamId, String ownerId, Session session,
                                       String summary, String goal, String outcome,
                                       List<String> keyFiles, List<String> tags) {
        String shareId = "share_" + UUID.randomUUID().toString().substring(0, 12);
        Instant now = Instant.now();

        SharedSession shared = new SharedSession(
            shareId, teamId, ownerId, session.getId(),
            session.getTitle() != null ? session.getTitle() : "Untitled Session",
            summary, goal, outcome,
            keyFiles != null ? keyFiles : List.of(),
            tags != null ? tags : List.of(),
            session.getCreatedAt(), now, 0, 0);

        sharedSessions.computeIfAbsent(teamId,
            k -> Collections.synchronizedList(new ArrayList<>())).add(shared);
        saveTeam(teamId);

        logger.info("[SessionSharing] " + ownerId + " shared session '" +
            shared.title + "' to team " + teamId);
        return shared;
    }

    /** 获取团队的分享会话列表 */
    public List<SharedSession> getSharedSessions(String teamId) {
        return new ArrayList<>(sharedSessions.getOrDefault(teamId, List.of()));
    }

    /** 获取分享详情并增加查看计数 */
    public SharedSession viewSharedSession(String teamId, String shareId) {
        List<SharedSession> sessions = sharedSessions.get(teamId);
        if (sessions == null) return null;

        synchronized (sessions) {
            for (int i = 0; i < sessions.size(); i++) {
                SharedSession s = sessions.get(i);
                if (s.shareId.equals(shareId)) {
                    SharedSession updated = new SharedSession(
                        s.shareId, s.teamId, s.ownerId, s.sessionId,
                        s.title, s.summary, s.goal, s.outcome,
                        s.keyFiles, s.tags, s.createdAt, s.sharedAt,
                        s.viewCount + 1, s.forkCount);
                    sessions.set(i, updated);
                    saveTeam(teamId);
                    return updated;
                }
            }
        }
        return null;
    }

    /** Fork 分享的会话（记录 fork 计数） */
    public void recordFork(String teamId, String shareId) {
        List<SharedSession> sessions = sharedSessions.get(teamId);
        if (sessions == null) return;

        synchronized (sessions) {
            for (int i = 0; i < sessions.size(); i++) {
                SharedSession s = sessions.get(i);
                if (s.shareId.equals(shareId)) {
                    sessions.set(i, new SharedSession(
                        s.shareId, s.teamId, s.ownerId, s.sessionId,
                        s.title, s.summary, s.goal, s.outcome,
                        s.keyFiles, s.tags, s.createdAt, s.sharedAt,
                        s.viewCount, s.forkCount + 1));
                    saveTeam(teamId);
                    return;
                }
            }
        }
    }

    /** 删除分享 */
    public boolean deleteSharedSession(String teamId, String shareId, String userId) {
        List<SharedSession> sessions = sharedSessions.get(teamId);
        if (sessions == null) return false;

        boolean removed = sessions.removeIf(s ->
            s.shareId.equals(shareId) && s.ownerId.equals(userId));
        if (removed) saveTeam(teamId);
        return removed;
    }

    // ==== 搜索/筛选 ====

    /** 按标签搜索 */
    public List<SharedSession> searchByTag(String teamId, String tag) {
        return sharedSessions.getOrDefault(teamId, List.of()).stream()
            .filter(s -> s.tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag)))
            .sorted((a, b) -> Integer.compare(b.viewCount, a.viewCount))
            .toList();
    }

    /** 按关键词搜索 */
    public List<SharedSession> search(String teamId, String query) {
        String lower = query.toLowerCase();
        return sharedSessions.getOrDefault(teamId, List.of()).stream()
            .filter(s -> s.title.toLowerCase().contains(lower)
                      || s.summary.toLowerCase().contains(lower)
                      || s.goal.toLowerCase().contains(lower)
                      || s.tags.stream().anyMatch(t -> t.toLowerCase().contains(lower)))
            .sorted((a, b) -> Integer.compare(b.viewCount, a.viewCount))
            .toList();
    }

    /** 获取热门分享（按查看次数排序） */
    public List<SharedSession> getPopular(String teamId, int limit) {
        return sharedSessions.getOrDefault(teamId, List.of()).stream()
            .sorted((a, b) -> Integer.compare(b.viewCount, a.viewCount))
            .limit(limit)
            .toList();
    }

    /** 获取用户自己的分享 */
    public List<SharedSession> getMyShares(String teamId, String userId) {
        return sharedSessions.getOrDefault(teamId, List.of()).stream()
            .filter(s -> s.ownerId.equals(userId))
            .toList();
    }

    // ==== 持久化 ====

    private Path teamFile(String teamId) {
        return SHARE_DIR.resolve(teamId + "_sessions.json");
    }

    private void saveTeam(String teamId) {
        try {
            List<SharedSession> sessions = sharedSessions.getOrDefault(teamId, List.of());
            mapper.writerWithDefaultPrettyPrinter()
                  .writeValue(teamFile(teamId).toFile(), sessions);
        } catch (IOException e) {
            logger.warning("[SessionSharing] Save failed: " + e.getMessage());
        }
    }

    private void loadAll() {
        try {
            var files = Files.list(SHARE_DIR);
            files.filter(f -> f.toString().endsWith("_sessions.json")).forEach(f -> {
                try {
                    String name = f.getFileName().toString();
                    String teamId = name.replace("_sessions.json", "");
                    var listType = mapper.getTypeFactory()
                        .constructCollectionType(List.class, SharedSession.class);
                    List<SharedSession> sessions = mapper.readValue(f.toFile(), listType);
                    sharedSessions.put(teamId,
                        Collections.synchronizedList(new ArrayList<>(sessions)));
                } catch (IOException e) {
                    logger.warning("[SessionSharing] Load failed: " + f);
                }
            });
            files.close();
        } catch (IOException e) {
            logger.warning("[SessionSharing] List failed: " + e.getMessage());
        }
    }

    /** 获取统计信息 */
    public Map<String, Object> getStats(String teamId) {
        List<SharedSession> sessions = sharedSessions.getOrDefault(teamId, List.of());
        int totalViews = sessions.stream().mapToInt(s -> s.viewCount).sum();
        int totalForks = sessions.stream().mapToInt(s -> s.forkCount).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_shares", sessions.size());
        stats.put("total_views", totalViews);
        stats.put("total_forks", totalForks);
        return stats;
    }
}
