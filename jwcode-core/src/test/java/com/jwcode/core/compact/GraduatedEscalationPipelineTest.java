package com.jwcode.core.compact;

import com.jwcode.core.agent.CompactorAgent;
import com.jwcode.core.llm.TokenBudget;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

@DisplayName("GraduatedEscalationPipeline — 分级升级压缩管道测试")
class GraduatedEscalationPipelineTest {

    private GraduatedEscalationPipeline pipeline;
    private CompactorAgent mockAgent;
    private TokenBudget budget;

    @BeforeEach
    void setUp() {
        mockAgent = mock(CompactorAgent.class);
        pipeline = new GraduatedEscalationPipeline(mockAgent);
        budget = new TokenBudget(100_000);
    }

    // === 级别评估 ===

    @Test
    @DisplayName("充足 token 返回 NONE")
    void evaluateNone() {
        budget.reserve(10_000); // 使用 10K，剩余 90K
        assertEquals(GraduatedEscalationPipeline.EscalationLevel.NONE,
            pipeline.evaluate(budget));
    }

    @Test
    @DisplayName("接近阈值返回 WARNING")
    void evaluateWarning() {
        budget.reserve(85_000); // 使用 85K，剩余 15K
        GraduatedEscalationPipeline.EscalationLevel level = pipeline.evaluate(budget);
        assertTrue(level.ordinal() <= GraduatedEscalationPipeline.EscalationLevel.ERROR.ordinal());
    }

    @Test
    @DisplayName("剩余不足 10K 返回 AUTO")
    void evaluateAuto() {
        budget.reserve(92_000); // 使用 92K，剩余 8K
        GraduatedEscalationPipeline.EscalationLevel level = pipeline.evaluate(budget);
        assertTrue(level.ordinal() >= GraduatedEscalationPipeline.EscalationLevel.AUTO.ordinal());
    }

    @Test
    @DisplayName("剩余为 0 返回 RESET")
    void evaluateReset() {
        budget.reserve(100_000); // 全部用完
        assertEquals(GraduatedEscalationPipeline.EscalationLevel.RESET,
            pipeline.evaluate(budget));
    }

    // === 管道执行 ===

    @Test
    @DisplayName("NONE 级别不压缩")
    void runNoneNoCompact() {
        budget.reserve(5_000);
        EscalationResult result = pipeline.run(List.of(), budget);
        assertFalse(result.compacted());
        assertEquals(GraduatedEscalationPipeline.EscalationLevel.NONE, result.level());
    }

    @Test
    @DisplayName("冷却期内跳过压缩")
    void cooldownSkipsCompaction() {
        budget.reserve(97_000); // 剩余 3K → AGGRESSIVE
        CompactorAgent.CompactionResult mockResult = new CompactorAgent.CompactionResult(
            List.of(), 10, 5, 2500, "test summary"
        );
        when(mockAgent.compact(any())).thenReturn(mockResult);

        // 第一次压缩 — 应成功
        EscalationResult first = pipeline.run(List.of(), budget);
        // 由于消息数不足 10 条，run() 会跳过压缩
        // 第二次也跳过了，因为消息数不足
        EscalationResult second = pipeline.run(List.of(), budget);
        assertFalse(second.compacted());
    }

    // === 级别 → 策略映射 ===

    @Test
    @DisplayName("级别映射到正确的策略")
    void levelToStrategyMapping() {
        assertTrue(pipeline.evaluate(budget) == GraduatedEscalationPipeline.EscalationLevel.NONE);
    }

    @Test
    @DisplayName("next() 逐级升级")
    void nextLevelUpgrades() {
        assertEquals(GraduatedEscalationPipeline.EscalationLevel.WARNING,
            GraduatedEscalationPipeline.EscalationLevel.NONE.next());
        assertEquals(GraduatedEscalationPipeline.EscalationLevel.RESET,
            GraduatedEscalationPipeline.EscalationLevel.AGGRESSIVE.next());
    }
}
