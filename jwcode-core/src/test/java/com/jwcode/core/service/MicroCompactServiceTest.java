package com.jwcode.core.service;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MicroCompactService — 单条工具结果微压缩测试")
class MicroCompactServiceTest {

    private MicroCompactService service;

    @BeforeEach
    void setUp() {
        service = new MicroCompactService();
    }

    // === 分级测试 ===

    @Test
    @DisplayName("CRITICAL: 错误结果返回 CRITICAL 等级")
    void classifyErrorAsCritical() {
        assertEquals(MicroCompactConfig.Tier.CRITICAL,
            service.classifyToolResult("BashTool", "Error: connection refused"));
        assertEquals(MicroCompactConfig.Tier.CRITICAL,
            service.classifyToolResult("FileReadTool", "NullPointerException at line 42"));
    }

    @Test
    @DisplayName("HIGH: 文件修改工具返回 HIGH 等级")
    void classifyFileModifyAsHigh() {
        assertEquals(MicroCompactConfig.Tier.HIGH,
            service.classifyToolResult("FileWriteTool", "wrote /src/Main.java"));
        assertEquals(MicroCompactConfig.Tier.HIGH,
            service.classifyToolResult("EditTool", "edited /app/config.yaml"));
    }

    @Test
    @DisplayName("MEDIUM: 读取工具返回 MEDIUM 等级")
    void classifyReadAsMedium() {
        assertEquals(MicroCompactConfig.Tier.MEDIUM,
            service.classifyToolResult("FileReadTool", "file content here"));
        assertEquals(MicroCompactConfig.Tier.MEDIUM,
            service.classifyToolResult("GrepTool", "found 3 matches"));
    }

    @Test
    @DisplayName("LOW: 命令工具返回 LOW 等级")
    void classifyCommandAsLow() {
        assertEquals(MicroCompactConfig.Tier.LOW,
            service.classifyToolResult("BashTool", "exit=0\nbuild success"));
    }

    @Test
    @DisplayName("MINIMAL: 未知工具返回 MINIMAL 等级")
    void classifyUnknownAsMinimal() {
        assertEquals(MicroCompactConfig.Tier.MINIMAL,
            service.classifyToolResult("UnknownTool", "some result"));
    }

    // === 压缩测试 ===

    @Test
    @DisplayName("CRITICAL 压缩保留错误信息")
    void compactCriticalRetainsError() {
        String result = service.microCompact("BashTool",
            "Error: build failed with exit code 1", MicroCompactConfig.Tier.CRITICAL);
        assertTrue(result.contains("ERROR"));
        assertTrue(result.contains("build failed"));
    }

    @Test
    @DisplayName("MINIMAL 压缩仅保留工具名 + success")
    void compactMinimalOnlySuccess() {
        String result = service.microCompact("SomeTool",
            "very long output that would be truncated", MicroCompactConfig.Tier.MINIMAL);
        assertEquals("[SomeTool: success]", result);
    }

    @Test
    @DisplayName("autoCompact 不压缩短内容")
    void autoCompactSkipsShortContent() {
        String content = "short result";
        String result = service.autoCompact("SomeTool", content);
        assertEquals(content, result);
    }
}
