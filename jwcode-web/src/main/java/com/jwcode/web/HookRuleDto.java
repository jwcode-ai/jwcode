package com.jwcode.web;

import java.util.List;
import java.util.Map;

/**
 * Hook 规则 DTO — API 序列化。
 */
public record HookRuleDto(
    String name,
    String description,
    List<String> events,
    String implementationType,
    String command,
    String url,
    String promptTemplate,
    String agentName,
    String priority,
    List<String> tools,
    Map<String, String> matchers,
    long timeoutMs,
    boolean enabled,
    boolean failOpen,
    String scope
) {}
