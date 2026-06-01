package com.jwcode.core.checkpoint;

import com.jwcode.core.graph.GraphCheckpoint;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed checkpoint storage providing cross-restart durability.
 * <p>
 * Schema:
 * <pre>
 *   checkpoints: id, session_id, step, ts, source, channel_values (JSON)
 *   channel_versions: checkpoint_id, channel_name, version
 *   versions_seen: checkpoint_id, node_name, channel_name, version
 *   writes: checkpoint_id, task_id, channel, value (JSON), idx
 * </pre>
 * <p>
 * The database file is stored at {@code ~/.jwcode/checkpoints/<sessionId>.db}.
 */
public class SqliteCheckpointStorage implements CheckpointStorage {

    private static final Logger logger = Logger.getLogger(SqliteCheckpointStorage.class.getName());

    private final String sessionId;
    private final String dbPath;
    private Connection connection;

    public SqliteCheckpointStorage(String sessionId) {
        this.sessionId = sessionId;
        String jwcodeDir = System.getProperty("user.home") + "/.jwcode/checkpoints";
        java.io.File dir = new java.io.File(jwcodeDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.dbPath = jwcodeDir + "/" + sessionId + ".db";
        initDatabase();
    }

    public SqliteCheckpointStorage(String sessionId, String dbPath) {
        this.sessionId = sessionId;
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            // Enable WAL mode for better concurrent read performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            createSchema();
            logger.info("SQLite checkpoint storage initialized: " + dbPath);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize SQLite storage", e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS checkpoints (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    step INTEGER NOT NULL,
                    ts TEXT NOT NULL,
                    source TEXT NOT NULL,
                    channel_values TEXT NOT NULL,
                    updated_channels TEXT
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS channel_versions (
                    checkpoint_id TEXT NOT NULL,
                    channel_name TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    PRIMARY KEY (checkpoint_id, channel_name),
                    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS versions_seen (
                    checkpoint_id TEXT NOT NULL,
                    node_name TEXT NOT NULL,
                    channel_name TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    PRIMARY KEY (checkpoint_id, node_name, channel_name),
                    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS writes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    checkpoint_id TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    channel TEXT NOT NULL,
                    value TEXT NOT NULL,
                    idx INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_checkpoints_session_step
                ON checkpoints(session_id, step)
            """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_writes_checkpoint
                ON writes(checkpoint_id, task_id)
            """);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CheckpointStorage implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void put(GraphCheckpoint cp) {
        String sql = """
            INSERT OR REPLACE INTO checkpoints
            (id, session_id, step, ts, source, channel_values, updated_channels)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, cp.getId());
                ps.setString(2, sessionId);
                ps.setInt(3, cp.getStep());
                ps.setString(4, cp.getTs());
                ps.setString(5, cp.getSource());
                ps.setString(6, toJson(cp.getChannelValues()));

                // Serialize updated_channels as comma-separated
                java.util.Iterator<String> it = cp.getUpdatedChannels().iterator();
                StringBuilder sb = new StringBuilder();
                while (it.hasNext()) {
                    sb.append(it.next());
                    if (it.hasNext()) sb.append(",");
                }
                ps.setString(7, sb.toString());
                ps.executeUpdate();
            }

            // Insert channel versions
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO channel_versions (checkpoint_id, channel_name, version) VALUES (?, ?, ?)")) {
                for (var entry : cp.getChannelVersions().entrySet()) {
                    ps.setString(1, cp.getId());
                    ps.setString(2, entry.getKey());
                    ps.setLong(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Insert versions seen
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO versions_seen (checkpoint_id, node_name, channel_name, version) VALUES (?, ?, ?, ?)")) {
                for (var nodeEntry : cp.getVersionsSeen().entrySet()) {
                    for (var chEntry : nodeEntry.getValue().entrySet()) {
                        ps.setString(1, cp.getId());
                        ps.setString(2, nodeEntry.getKey());
                        ps.setString(3, chEntry.getKey());
                        ps.setLong(4, chEntry.getValue());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            logger.log(Level.WARNING, "Failed to save checkpoint: " + cp.getId(), e);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override
    public void putWrite(String checkpointId, String taskId, String channel, Object value) {
        String sql = """
            INSERT INTO writes (checkpoint_id, task_id, channel, value, idx)
            VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(idx), 0) + 1 FROM writes WHERE checkpoint_id = ? AND task_id = ?))
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, checkpointId);
            ps.setString(2, taskId);
            ps.setString(3, channel);
            ps.setString(4, toJson(value));
            ps.setString(5, checkpointId);
            ps.setString(6, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to save write", e);
        }
    }

    @Override
    public Optional<GraphCheckpoint> get(String checkpointId) {
        String sql = "SELECT * FROM checkpoints WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, checkpointId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(buildCheckpoint(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to get checkpoint: " + checkpointId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<GraphCheckpoint> getByStep(int step) {
        String sql = "SELECT * FROM checkpoints WHERE session_id = ? AND step = ? ORDER BY ts DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, step);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(buildCheckpoint(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to get checkpoint by step: " + step, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<GraphCheckpoint> getLatest() {
        String sql = "SELECT * FROM checkpoints WHERE session_id = ? ORDER BY step DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(buildCheckpoint(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to get latest checkpoint", e);
        }
        return Optional.empty();
    }

    @Override
    public List<GraphCheckpoint> list(Integer before, Integer limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM checkpoints WHERE session_id = ?");
        if (before != null) sql.append(" AND step < ").append(before);
        sql.append(" ORDER BY step DESC");
        if (limit != null) sql.append(" LIMIT ").append(limit);

        List<GraphCheckpoint> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(buildCheckpoint(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to list checkpoints", e);
        }
        return result;
    }

    @Override
    public void clear() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM writes WHERE checkpoint_id IN (SELECT id FROM checkpoints WHERE session_id = '" + sessionId + "')");
            stmt.execute("DELETE FROM versions_seen WHERE checkpoint_id IN (SELECT id FROM checkpoints WHERE session_id = '" + sessionId + "')");
            stmt.execute("DELETE FROM channel_versions WHERE checkpoint_id IN (SELECT id FROM checkpoints WHERE session_id = '" + sessionId + "')");
            stmt.execute("DELETE FROM checkpoints WHERE session_id = '" + sessionId + "'");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to clear checkpoints", e);
        }
    }

    @Override
    public int size() {
        String sql = "SELECT COUNT(*) FROM checkpoints WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to count checkpoints", e);
        }
        return 0;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("SQLite checkpoint storage closed: " + dbPath);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to close SQLite connection", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private GraphCheckpoint buildCheckpoint(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        int step = rs.getInt("step");
        String ts = rs.getString("ts");
        String source = rs.getString("source");
        Map<String, Object> channelValues = fromJson(rs.getString("channel_values"));
        String updatedChannelsRaw = rs.getString("updated_channels");

        // Load channel versions
        Map<String, Long> channelVersions = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT channel_name, version FROM channel_versions WHERE checkpoint_id = ?")) {
            ps.setString(1, id);
            try (ResultSet crs = ps.executeQuery()) {
                while (crs.next()) {
                    channelVersions.put(crs.getString(1), crs.getLong(2));
                }
            }
        } catch (SQLException ignored) {}

        // Load versions seen
        Map<String, Map<String, Long>> versionsSeen = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT node_name, channel_name, version FROM versions_seen WHERE checkpoint_id = ?")) {
            ps.setString(1, id);
            try (ResultSet vrs = ps.executeQuery()) {
                while (vrs.next()) {
                    versionsSeen.computeIfAbsent(vrs.getString(1), k -> new LinkedHashMap<>())
                            .put(vrs.getString(2), vrs.getLong(3));
                }
            }
        } catch (SQLException ignored) {}

        // Parse updated channels
        List<String> updatedChannels = new ArrayList<>();
        if (updatedChannelsRaw != null && !updatedChannelsRaw.isEmpty()) {
            for (String ch : updatedChannelsRaw.split(",")) {
                if (!ch.isEmpty()) updatedChannels.add(ch.trim());
            }
        }

        return new GraphCheckpoint(id, ts, channelValues, channelVersions,
                versionsSeen, Collections.unmodifiableList(updatedChannels), step, source);
    }

    // Minimal JSON helpers (avoids Jackson dependency in storage layer)
    @SuppressWarnings("unchecked")
    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) return Collections.emptyMap();
        // For full fidelity, users should inject a proper JSON codec.
        // This minimal parser handles String, Number, Boolean, null values in simple maps.
        Map<String, Object> map = new LinkedHashMap<>();
        String content = json.trim();
        if (!content.startsWith("{") || !content.endsWith("}")) return map;
        content = content.substring(1, content.length() - 1).trim();
        if (content.isEmpty()) return map;

        // Split on commas, respecting JSON string quoting
        int depth = 0;
        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (c == '"' && (current.isEmpty() || current.charAt(current.length() - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') depth--;
            }
            if (c == ',' && depth == 0 && !inString) {
                parseEntry(current.toString().trim(), map);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parseEntry(current.toString().trim(), map);
        }
        return map;
    }

    private static void parseEntry(String entry, Map<String, Object> map) {
        if (entry.isEmpty()) return;
        int colonPos = entry.indexOf(':');
        if (colonPos < 0) return;
        String key = unquote(entry.substring(0, colonPos).trim());
        String value = entry.substring(colonPos + 1).trim();
        if ("null".equals(value)) {
            map.put(key, null);
        } else if (value.startsWith("\"")) {
            map.put(key, unquote(value));
        } else if ("true".equals(value)) {
            map.put(key, true);
        } else if ("false".equals(value)) {
            map.put(key, false);
        } else {
            try {
                map.put(key, Long.parseLong(value));
            } catch (NumberFormatException e1) {
                try {
                    map.put(key, Double.parseDouble(value));
                } catch (NumberFormatException e2) {
                    map.put(key, value); // keep as string
                }
            }
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }

    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + ((String) value).replace("\"", "\\\"") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) value;
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + value.toString().replace("\"", "\\\"") + "\"";
    }
}
