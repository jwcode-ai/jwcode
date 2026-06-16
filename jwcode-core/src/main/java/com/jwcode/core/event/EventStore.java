package com.jwcode.core.event;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EventStore — SQLite 备份的事件持久化存储。
 *
 * <p>存储所有 {@link SessionEvent} 到 SQLite 数据库，支持按 session 和 type 查询。
 * 使用 WAL 模式保障写入性能。
 *
 * <p>数据库位置：{@code ~/.jwcode/events/<sessionId>.db}
 */
public class EventStore implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(EventStore.class.getName());

    private final String sessionId;
    private final String dbPath;
    private Connection connection;

    public EventStore(String sessionId) {
        this.sessionId = sessionId;
        String eventsDir = System.getProperty("user.home") + "/.jwcode/events";
        new java.io.File(eventsDir).mkdirs();
        this.dbPath = eventsDir + "/" + sessionId + ".db";
        initDatabase();
    }

    public EventStore(String sessionId, String dbPath) {
        this.sessionId = sessionId;
        this.dbPath = dbPath;
        new java.io.File(dbPath).getParentFile().mkdirs();
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }
            createSchema();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[EventStore] Failed to init: " + dbPath, e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    event_id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    version INTEGER NOT NULL DEFAULT 0,
                    data TEXT
                )
            """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_events_session_type
                    ON events(session_id, event_type)
            """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_events_timestamp
                    ON events(timestamp)
            """);
        }
    }

    // ==================== 写入 ====================

    /**
     * 追加存储一个事件。
     */
    public void append(SessionEvent event) {
        String sql = "INSERT OR IGNORE INTO events(event_id, session_id, event_type, timestamp, version, data) "
            + "VALUES(?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getEventId());
            ps.setString(2, event.getSessionId());
            ps.setString(3, event.getType().name());
            ps.setString(4, event.getTimestamp().toString());
            ps.setLong(5, event.getVersion());
            ps.setString(6, event.getData());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "[EventStore] append failed: " + event, e);
        }
    }

    /**
     * 批量追加。
     */
    public void appendAll(List<SessionEvent> events) {
        String sql = "INSERT OR IGNORE INTO events(event_id, session_id, event_type, timestamp, version, data) "
            + "VALUES(?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (SessionEvent event : events) {
                ps.setString(1, event.getEventId());
                ps.setString(2, event.getSessionId());
                ps.setString(3, event.getType().name());
                ps.setString(4, event.getTimestamp().toString());
                ps.setLong(5, event.getVersion());
                ps.setString(6, event.getData());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "[EventStore] batch append failed", e);
        }
    }

    // ==================== 查询 ====================

    /**
     * 获取指定 session 的所有事件（按时间升序）。
     */
    public List<SessionEvent> getEvents(String sessionId) {
        String sql = "SELECT * FROM events WHERE session_id = ? ORDER BY timestamp ASC, version ASC";
        return queryEvents(sql, sessionId);
    }

    /**
     * 获取指定 session 和类型的事件。
     */
    public List<SessionEvent> getEventsByType(String sessionId, SessionEvent.EventType type) {
        String sql = "SELECT * FROM events WHERE session_id = ? AND event_type = ? ORDER BY timestamp ASC";
        return queryEvents(sql, sessionId, type.name());
    }

    /**
     * 获取自指定版本号之后的事件。
     */
    public List<SessionEvent> getEventsSinceVersion(String sessionId, long sinceVersion) {
        String sql = "SELECT * FROM events WHERE session_id = ? AND version > ? ORDER BY version ASC";
        return queryEvents(sql, sessionId, sinceVersion);
    }

    /**
     * 获取最新版本号。
     */
    public long getLatestVersion(String sessionId) {
        String sql = "SELECT COALESCE(MAX(version), 0) FROM events WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "[EventStore] getLatestVersion failed", e);
        }
        return 0;
    }

    /**
     * 对指定 session 的所有事件执行 replay。
     */
    public void replay(String sessionId, java.util.function.Consumer<SessionEvent> handler) {
        List<SessionEvent> events = getEvents(sessionId);
        for (SessionEvent event : events) {
            handler.accept(event);
        }
    }

    // ==================== 内部 ====================

    private List<SessionEvent> queryEvents(String sql, Object... params) {
        List<SessionEvent> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "[EventStore] query failed", e);
        }
        return result;
    }

    private SessionEvent mapRow(ResultSet rs) throws SQLException {
        String eventId = rs.getString("event_id");
        SessionEvent.EventType type = SessionEvent.EventType.valueOf(rs.getString("event_type"));
        String sessionId = rs.getString("session_id");
        java.time.Instant timestamp = java.time.Instant.parse(rs.getString("timestamp"));
        long version = rs.getLong("version");
        String data = rs.getString("data");
        return new SessionEvent(eventId, type, sessionId, timestamp, version, data);
    }

    // ==================== 生命周期 ====================

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "[EventStore] close error", e);
        }
    }

    /**
     * 清理超过指定天数的旧数据库。
     */
    public static int cleanOldEvents(int maxAgeDays) {
        String eventsDir = System.getProperty("user.home") + "/.jwcode/events";
        java.io.File dir = new java.io.File(eventsDir);
        if (!dir.exists()) return 0;
        int count = 0;
        long cutoff = System.currentTimeMillis() - (long) maxAgeDays * 86400_000L;
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
        if (files != null) {
            for (java.io.File f : files) {
                if (f.lastModified() < cutoff && f.delete()) count++;
            }
        }
        return count;
    }

    public String getDbPath() { return dbPath; }
}
