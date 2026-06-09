package com.jwcode.web;

/**
 * Hook 执行日志条目 — 含运行时执行记录和管理操作审计。
 */
public record HookExecutionLogEntry(
    String id,
    String hookName,
    String eventType,
    String decision,
    String reason,
    long durationMs,
    long timestamp,
    String operatorIp,
    String changeType,
    String changeDiff
) {}
