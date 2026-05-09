package com.jwcode.core.a2a.router;

/**
 * RoutingStrategy — Agent 路由选择策略枚举。
 *
 * <p>定义 TaskService 在多个匹配 Agent 中选择最优 Agent 的策略。</p>
 */
public enum RoutingStrategy {

    /** 负载最低优先（默认） */
    LEAST_LOAD,

    /** 能力匹配度最高优先 */
    BEST_MATCH,

    /** 轮询（Round-Robin） */
    ROUND_ROBIN,

    /** 随机选择 */
    RANDOM,

    /** 最快响应优先 */
    FASTEST_RESPONSE
}
