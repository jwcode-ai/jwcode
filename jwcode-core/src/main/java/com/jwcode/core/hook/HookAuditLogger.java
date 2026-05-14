package com.jwcode.core.hook;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HookAuditLogger — Hook 决策审计日志。
 *
 * <p>记录每一次 Hook 调用的完整轨迹：事件类型、Hook 名称、决策、耗时、原因。
 * 支持查询最近 N 条记录，用于调试和合规审计。</p>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link ConcurrentLinkedQueue} 保证多线程写入安全。</p>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class HookAuditLogger {

    private static final Logger logger = Logger.getLogger(HookAuditLogger.class.getName());

    private static final int DEFAULT_MAX_ENTRIES = 1000;

    private final ConcurrentLinkedQueue<AuditEntry> entries;
    private final int maxEntries;

    public HookAuditLogger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public HookAuditLogger(int maxEntries) {
        this.maxEntries = maxEntries;
        this.entries = new ConcurrentLinkedQueue<>();
    }

    /**
     * 记录一次 Hook 调用。
     */
    public void record(HookContext context, HookResult result) {
        AuditEntry entry = new AuditEntry(
            context.getEventType(),
            result.getHookName(),
            result.getDecision(),
            result.getDurationMs(),
            result.getReason(),
            Instant.now()
        );

        entries.offer(entry);
        // 超出上限时淘汰最旧记录
        while (entries.size() > maxEntries) {
            entries.poll();
        }

        // 拒绝和回退的决策用 WARNING 级别记录
        if (result.getDecision().isTerminal()) {
            logger.warning("[HookAudit] " + entry);
        } else {
            logger.fine("[HookAudit] " + entry);
        }
    }

    /**
     * 记录 Hook 执行异常。
     */
    public void recordError(String hookName, HookEventType eventType, String errorMessage) {
        AuditEntry entry = new AuditEntry(
            eventType, hookName, HookDecision.ALLOW, 0,
            "ERROR: " + errorMessage, Instant.now()
        );
        entries.offer(entry);
        while (entries.size() > maxEntries) {
            entries.poll();
        }
        logger.log(Level.WARNING, "[HookAudit] " + entry);
    }

    /**
     * 获取最近的审计记录（不可变快照）。
     */
    public java.util.List<AuditEntry> getRecentEntries() {
        return java.util.List.copyOf(entries);
    }

    /**
     * 获取指定事件类型的最近记录。
     */
    public java.util.List<AuditEntry> getRecentEntries(HookEventType eventType) {
        return entries.stream()
            .filter(e -> e.eventType == eventType)
            .toList();
    }

    /**
     * 获取统计摘要。
     */
    public AuditSummary getSummary() {
        int total = entries.size();
        long denied = entries.stream()
            .filter(e -> e.decision == HookDecision.DENY || e.decision == HookDecision.VOID)
            .count();
        long modified = entries.stream()
            .filter(e -> e.decision == HookDecision.MODIFY)
            .count();
        double avgLatencyMs = entries.stream()
            .mapToLong(AuditEntry::durationMs)
            .average()
            .orElse(0);

        return new AuditSummary(total, denied, modified, avgLatencyMs);
    }

    /**
     * 清空审计记录。
     */
    public void clear() {
        entries.clear();
    }

    // ==================== 数据类 ====================

    /**
     * 单条审计记录。
     */
    public record AuditEntry(
        HookEventType eventType,
        String hookName,
        HookDecision decision,
        long durationMs,
        String reason,
        Instant timestamp
    ) {
        @Override
        public String toString() {
            return String.format("[%s] %s | %s | %s | %dms | %s",
                eventType, hookName, decision, reason, durationMs, timestamp);
        }
    }

    /**
     * 审计统计摘要。
     */
    public record AuditSummary(
        long totalCalls,
        long deniedCalls,
        long modifiedCalls,
        double avgLatencyMs
    ) {
        @Override
        public String toString() {
            return String.format("HookAudit{total=%d, denied=%d, modified=%d, avgLatency=%.1fms}",
                totalCalls, deniedCalls, modifiedCalls, avgLatencyMs);
        }
    }
}
