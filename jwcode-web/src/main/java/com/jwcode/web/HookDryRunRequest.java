package com.jwcode.web;

/**
 * Dry-run 请求 — 模拟 Hook 执行。
 */
public record HookDryRunRequest(
    HookRuleDto hook,
    String eventType,
    String toolName,
    String toolInput
) {}
