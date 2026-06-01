package com.jwcode.core.compact;

import com.jwcode.core.agent.CompactorTrigger;

/**
 * EscalationResult — 分级升级压缩结果 POJO。
 *
 * @param level        触发的升级级别
 * @param strategy     执行的压缩策略（null 表示未执行）
 * @param compacted    是否实际执行了压缩
 * @param tokensSaved  节省的 token 数
 * @param messageCount 压缩前后对比描述
 * @param summary      人类可读的结果摘要
 */
public record EscalationResult(
    GraduatedEscalationPipeline.EscalationLevel level,
    CompactorTrigger.Strategy strategy,
    boolean compacted,
    long tokensSaved,
    String messageCount,
    String summary
) {
    public static EscalationResult none() {
        return new EscalationResult(
            GraduatedEscalationPipeline.EscalationLevel.NONE,
            null, false, 0, null,
            "Token 充足，无需压缩"
        );
    }

    public static EscalationResult warning(GraduatedEscalationPipeline.EscalationLevel level) {
        return new EscalationResult(
            level, null, false, 0, null,
            "Token 接近上限，建议压缩"
        );
    }
}
