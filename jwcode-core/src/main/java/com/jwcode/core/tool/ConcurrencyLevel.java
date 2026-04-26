package com.jwcode.core.tool;

/**
 * 工具并发安全级别 — 声明工具是否可与其他工具并行执行。
 *
 * <p>AI 编排引擎利用此信息决定工具调用顺序：</p>
 * <ul>
 *   <li>{@code PARALLEL_SAFE} — 可与其他 PARALLEL_SAFE 工具并行</li>
 *   <li>{@code SEQUENTIAL} — 必须串行执行</li>
 *   <li>{@code EXCLUSIVE} — 独占执行，期间不允许任何其他工具并行</li>
 * </ul>
 */
public enum ConcurrencyLevel {
    /**
     * 可安全并行 — 纯读操作、无副作用冲突的工具
     */
    PARALLEL_SAFE,

    /**
     * 必须串行 — 默认级别，大多数工具适用
     */
    SEQUENTIAL,

    /**
     * 独占执行 — 涉及全局状态变更或资源锁定的危险操作
     */
    EXCLUSIVE
}
