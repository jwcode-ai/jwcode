package com.jwcode.core.llm.fragment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * 片段注入审计日志 — 环形缓冲区记录每次注入的片段及时间戳。
 *
 * <p>用于调试和成本追踪：了解哪些片段被注入、消耗了多少 token。
 */
public class FragmentAuditLog {
    private static final Logger logger = Logger.getLogger(FragmentAuditLog.class.getName());
    private static final int MAX_ENTRIES = 256;

    private final List<Entry> entries = new ArrayList<>(MAX_ENTRIES);

    public record Entry(
        String fragmentId,
        FragmentCategory category,
        int tokenCount,
        Instant timestamp,
        String sessionId
    ) {}

    public synchronized void record(String fragmentId, FragmentCategory category,
                                     int tokenCount, String sessionId) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.remove(0);
        }
        entries.add(new Entry(fragmentId, category, tokenCount, Instant.now(), sessionId));
        logger.fine("[FragmentAudit] " + fragmentId + " | category=" + category
            + " | tokens=" + tokenCount + " | session=" + sessionId);
    }

    public synchronized List<Entry> getRecentEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized List<Entry> getEntriesBySession(String sessionId) {
        return entries.stream()
            .filter(e -> sessionId.equals(e.sessionId()))
            .toList();
    }

    public synchronized int getTotalTokensInjected(String sessionId) {
        return entries.stream()
            .filter(e -> sessionId.equals(e.sessionId()))
            .mapToInt(Entry::tokenCount)
            .sum();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
