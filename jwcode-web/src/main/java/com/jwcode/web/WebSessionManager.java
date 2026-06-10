package com.jwcode.web;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Web 会话管理器 — 支持消息存储与会话持久化。
 */
public class WebSessionManager {

    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(1);
    private final Path storageDir;
    private static final int MAX_SESSIONS = 50;
    private static final int MAX_MESSAGES_PER_SESSION = 500;

    public WebSessionManager() {
        Path home = Paths.get(System.getProperty("user.home"), ".jwcode", "sessions");
        try { Files.createDirectories(home); } catch (IOException ignored) {}
        this.storageDir = home;
        loadPersistedSessions();
    }

    public WebSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            WebSession s = new WebSession(id);
            return s;
        });
    }

    public WebSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        WebSession removed = sessions.remove(sessionId);
        if (removed != null) {
            try { Files.deleteIfExists(storageDir.resolve(sessionId + ".json")); } catch (IOException ignored) {}
        }
    }

    public Collection<WebSession> getAllSessions() {
        return sessions.values().stream()
            .sorted((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()))
            .collect(Collectors.toList());
    }

    public String generateSessionId() {
        return "session_" + String.format("%03d", sessionCounter.getAndIncrement());
    }

    /**
     * 将消息追加到会话并自动持久化。
     */
    public void appendMessage(String sessionId, String type, String data) {
        WebSession session = sessions.get(sessionId);
        if (session == null) return;
        String json = "{\"type\":\"" + escapeJson(type) + "\",\"data\":"
            + (data != null ? "\"" + escapeJson(data) + "\"" : "null")
            + ",\"ts\":" + System.currentTimeMillis() + "}";
        synchronized (session) {
            session.messages.add(json);
            if (session.messages.size() > MAX_MESSAGES_PER_SESSION) {
                session.messages.removeFirst();
            }
            session.updateTimestamp();
        }
        pruneOldSessions();
        persistSession(sessionId);
    }

    /**
     * 持久化单个会话到磁盘。
     */
    private void persistSession(String sessionId) {
        WebSession session = sessions.get(sessionId);
        if (session == null) return;
        try {
            Path file = storageDir.resolve(sessionId + ".json");
            StringBuilder sb = new StringBuilder();
            sb.append("{\"id\":\"").append(escapeJson(session.id)).append("\",");
            sb.append("\"title\":\"").append(escapeJson(session.title)).append("\",");
            sb.append("\"createdAt\":").append(session.createdAt).append(",");
            sb.append("\"updatedAt\":").append(session.updatedAt).append(",");
            sb.append("\"messageCount\":").append(session.messages.size()).append(",");
            sb.append("\"messages\":[");
            boolean first = true;
            for (String msg : session.messages) {
                if (!first) sb.append(",");
                sb.append(msg);
                first = false;
            }
            sb.append("]}");
            Files.writeString(file, sb.toString());
        } catch (IOException ignored) {}
    }

    private void loadPersistedSessions() {
        try {
            File[] files = storageDir.toFile().listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) return;
            for (File f : files) {
                try {
                    String raw = Files.readString(f.toPath());
                    // Simple JSON parsing to extract id and title
                    String id = extractJsonString(raw, "id");
                    String title = extractJsonString(raw, "title");
                    if (id != null && !id.isEmpty()) {
                        WebSession s = new WebSession(id);
                        s.title = title != null ? title : "会话";
                        // Extract createdAt if present
                        long ca = extractJsonLong(raw, "createdAt");
                        if (ca > 0) s.createdAt = ca;
                        s.updatedAt = System.currentTimeMillis();
                        sessions.put(id, s);
                        sessionCounter.set(Math.max(sessionCounter.get(), Integer.parseInt(id.replace("session_", "")) + 1));
                    }
                } catch (IOException ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int i = json.indexOf(pattern);
        if (i < 0) return null;
        i += pattern.length();
        int end = i;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && (end == 0 || json.charAt(end - 1) != '\\')) break;
            end++;
        }
        return json.substring(i, end);
    }

    private long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int i = json.indexOf(pattern);
        if (i < 0) return 0;
        i += pattern.length();
        int end = i;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try { return Long.parseLong(json.substring(i, end)); } catch (NumberFormatException e) { return 0; }
    }

    private void pruneOldSessions() {
        if (sessions.size() <= MAX_SESSIONS) return;
        List<WebSession> sorted = new ArrayList<>(sessions.values());
        sorted.sort((a, b) -> Long.compare(a.updatedAt, b.updatedAt));
        for (int i = 0; i < sorted.size() - MAX_SESSIONS; i++) {
            removeSession(sorted.get(i).id);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Web 会话 — 包含消息列表和元数据。
     */
    public static class WebSession {
        private final String id;
        private long createdAt;
        private long updatedAt;
        private String title;
        private final LinkedList<String> messages = new LinkedList<>();

        public WebSession(String id) {
            this.id = id;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
            this.title = "会话";
        }

        public String getId() { return id; }
        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public List<String> getMessages() { return new ArrayList<>(messages); }
        public int getMessageCount() { return messages.size(); }
        void updateTimestamp() { this.updatedAt = System.currentTimeMillis(); }
    }
}
