package com.jwcode.core.planner;

import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.planner.IntentAnalyzer.AnalysisResult;
import com.jwcode.core.planner.IntentAnalyzer.Complexity;
import com.jwcode.core.planner.IntentAnalyzer.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SemanticIntentAnalyzer 单元测试。
 *
 * <p>测试场景覆盖：
 * <ul>
 *   <li>无 LLM 时 fallback 正则匹配</li>
 *   <li>LLM JSON 解析</li>
 *   <li>快路径（斜杠命令、闲聊）</li>
 *   <li>缓存命中</li>
 *   <li>LLM 返回异常 JSON 的容错</li>
 * </ul>
 * </p>
 */
class SemanticIntentAnalyzerTest {

    private SemanticIntentAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        // 无 LLM 时走正则 fallback
        analyzer = new SemanticIntentAnalyzer(null);
    }

    // ==================== 正则 Fallback 测试 ====================

    @Test
    @DisplayName("无 LLM — 新功能开发识别")
    void testFeatureDetectionWithoutLLM() {
        // 注意：正则 fallback 对"添加一个用户登录功能"匹配不到（有"一个"干扰词）
        // 这正是 LLM 分类器要解决的问题
        AnalysisResult result = analyzer.analyze("添加新功能：用户登录", null);
        assertEquals(TaskType.FEATURE, result.getTaskType());
    }

    @Test
    @DisplayName("无 LLM — Bug修复识别")
    void testBugfixDetectionWithoutLLM() {
        AnalysisResult result = analyzer.analyze("修复NPE空指针异常", null);
        assertEquals(TaskType.BUGFIX, result.getTaskType());
    }

    @Test
    @DisplayName("无 LLM — 重构识别")
    void testRefactorDetectionWithoutLLM() {
        AnalysisResult result = analyzer.analyze("重构auth模块，提取公共逻辑", null);
        assertEquals(TaskType.REFACTOR, result.getTaskType());
    }

    @Test
    @DisplayName("无 LLM — 测试识别")
    void testTestDetectionWithoutLLM() {
        AnalysisResult result = analyzer.analyze("给IntentAnalyzer编写单元测试", null);
        assertEquals(TaskType.TEST, result.getTaskType());
    }

    @Test
    @DisplayName("无 LLM — 文档识别")
    void testDocDetectionWithoutLLM() {
        AnalysisResult result = analyzer.analyze("更新API文档", null);
        assertEquals(TaskType.DOC, result.getTaskType());
    }

    @Test
    @DisplayName("无 LLM — 闲聊识别")
    void testChatDetectionWithoutLLM() {
        AnalysisResult result = analyzer.analyze("你好", null);
        assertEquals(TaskType.CHAT, result.getTaskType());
    }

    @Test
    @DisplayName("无 LLM — 空输入处理")
    void testEmptyInput() {
        AnalysisResult result = analyzer.analyze("", null);
        assertEquals(TaskType.CHAT, result.getTaskType());
    }

    @Test
    @DisplayName("无 LLM — null输入处理")
    void testNullInput() {
        AnalysisResult result = analyzer.analyze(null, null);
        assertEquals(TaskType.CHAT, result.getTaskType());
    }

    // ==================== 中断检测测试 ====================

    @Test
    @DisplayName("中断检测 — /stop 命令")
    void testInterruptionSlashCommand() {
        assertTrue(analyzer.isInterruption("/stop"));
        assertTrue(analyzer.isInterruption("/pause"));
        assertTrue(analyzer.isInterruption("/cancel"));
    }

    @Test
    @DisplayName("中断检测 — 自然语言")
    void testInterruptionNaturalLanguage() {
        assertTrue(analyzer.isInterruption("等一下，先做这个"));
        assertTrue(analyzer.isInterruption("暂停当前任务"));
    }

    @Test
    @DisplayName("中断检测 — 正常输入不误判")
    void testNoFalseInterruption() {
        assertFalse(analyzer.isInterruption("帮我修复一个bug"));
        assertFalse(analyzer.isInterruption("添加新功能"));
    }

    // ==================== 模块提取测试 ====================

    @Test
    @DisplayName("模块提取 — 提及 core 模块")
    void testModuleExtraction() {
        AnalysisResult result = analyzer.analyze("修复jwcode-core中的NPE问题", null);
        assertTrue(result.getModulesInvolved().contains("core"),
            "应该识别出 core 模块");
    }

    @Test
    @DisplayName("模块提取 — 多模块")
    void testMultipleModules() {
        AnalysisResult result = analyzer.analyze(
            "jwcode-core和jwcode-web之间的接口需要统一", null);
        List<String> modules = result.getModulesInvolved();
        assertTrue(modules.contains("core") || modules.contains("jwcode-core"));
    }

    // ==================== 复杂度测试 ====================

    @Test
    @DisplayName("复杂度 — 简单任务")
    void testSimpleComplexity() {
        AnalysisResult result = analyzer.analyze("简单改个变量名", null);
        assertEquals(Complexity.SIMPLE, result.getComplexity());
    }

    @Test
    @DisplayName("复杂度 — 复杂任务")
    void testComplexComplexity() {
        AnalysisResult result = analyzer.analyze("跨模块大规模重构agent系统", null);
        assertEquals(Complexity.COMPLEX, result.getComplexity());
    }

    // ==================== JSON 解析测试 ====================

    @Test
    @DisplayName("JSON 提取 — 纯 JSON")
    void testExtractJsonPure() {
        String json = IntentAnalysisPrompt.extractJson(
            "{\"taskType\":\"FEATURE\",\"complexity\":\"MEDIUM\"}");
        assertNotNull(json);
        assertTrue(json.contains("FEATURE"));
    }

    @Test
    @DisplayName("JSON 提取 — markdown 代码块包裹")
    void testExtractJsonMarkdown() {
        String json = IntentAnalysisPrompt.extractJson(
            "```json\n{\"taskType\":\"BUGFIX\"}\n```");
        assertNotNull(json);
        assertTrue(json.contains("BUGFIX"));
    }

    @Test
    @DisplayName("JSON 提取 — 前后有多余文字")
    void testExtractJsonWithSurroundingText() {
        String json = IntentAnalysisPrompt.extractJson(
            "分析结果如下：{\"taskType\":\"REFACTOR\"}，请确认。");
        assertNotNull(json);
        assertTrue(json.contains("REFACTOR"));
    }

    @Test
    @DisplayName("JSON 提取 — null/空输入")
    void testExtractJsonNullOrEmpty() {
        assertNull(IntentAnalysisPrompt.extractJson(null));
        assertNull(IntentAnalysisPrompt.extractJson(""));
        assertNull(IntentAnalysisPrompt.extractJson("   "));
    }

    // ==================== Quick path 测试 ====================

    @Test
    @DisplayName("快路径 — 斜杠命令直接走正则")
    void testQuickPathSlashCommand() {
        AnalysisResult result = analyzer.analyze("/help", null);
        assertNotNull(result);
        // /help 应被识别为中断或闲聊
    }

    // ==================== LLM 集成测试（模拟） ====================

    @Test
    @DisplayName("LLM 集成 — 有效 JSON 响应")
    void testLlmWithValidJsonResponse() {
        // 创建一个返回正确 JSON 的模拟 LLMService
        LLMService mockLlm = createMockLlmService(
            "{\"taskType\":\"FEATURE\",\"complexity\":\"MEDIUM\"," +
            "\"modulesInvolved\":[\"core\"],\"techStack\":\"Java\"," +
            "\"summary\":\"添加新功能\",\"confidence\":0.95,\"isInterruption\":false}");

        SemanticIntentAnalyzer llmAnalyzer = new SemanticIntentAnalyzer(mockLlm);
        AnalysisResult result = llmAnalyzer.analyze("添加用户登录功能", null);

        assertEquals(TaskType.FEATURE, result.getTaskType());
        assertEquals(Complexity.MEDIUM, result.getComplexity());
    }

    @Test
    @DisplayName("LLM 集成 — markdown 包裹的 JSON")
    void testLlmWithMarkdownWrappedJson() {
        LLMService mockLlm = createMockLlmService(
            "```json\n{\"taskType\":\"BUGFIX\",\"complexity\":\"SIMPLE\"," +
            "\"modulesInvolved\":[],\"techStack\":\"\",\"summary\":\"修复bug\"," +
            "\"confidence\":0.9,\"isInterruption\":false}\n```");

        SemanticIntentAnalyzer llmAnalyzer = new SemanticIntentAnalyzer(mockLlm);
        AnalysisResult result = llmAnalyzer.analyze("修复空指针", null);

        assertEquals(TaskType.BUGFIX, result.getTaskType());
    }

    @Test
    @DisplayName("LLM 集成 — 异常 JSON 回退正则")
    void testLlmWithInvalidJsonFallback() {
        // 返回无效 JSON，应回退到正则
        LLMService mockLlm = createMockLlmService("这不是有效的JSON");

        SemanticIntentAnalyzer llmAnalyzer = new SemanticIntentAnalyzer(mockLlm);
        AnalysisResult result = llmAnalyzer.analyze("修复一个bug", null);

        // 应该回退到正则，识别为 BUGFIX
        assertEquals(TaskType.BUGFIX, result.getTaskType());
    }

    @Test
    @DisplayName("LLM 集成 — LLM 不可用时直接走正则")
    void testLlmDisabled() {
        SemanticIntentAnalyzer disabledAnalyzer = new SemanticIntentAnalyzer(null);
        disabledAnalyzer.setLlmEnabled(false);

        AnalysisResult result = disabledAnalyzer.analyze("修复bug", null);
        assertEquals(TaskType.BUGFIX, result.getTaskType());
    }

    // ==================== 缓存测试 ====================

    @Test
    @DisplayName("缓存 — 相同输入命中缓存")
    void testCacheHit() {
        int[] callCount = {0};
        LLMService mockLlm = new LLMService() {
            @Override
            public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) {
                callCount[0]++;
                return CompletableFuture.completedFuture(
                    LLMResponse.success("{\"taskType\":\"FEATURE\",\"complexity\":\"MEDIUM\"," +
                        "\"modulesInvolved\":[],\"techStack\":\"\",\"summary\":\"test\"," +
                        "\"confidence\":1.0,\"isInterruption\":false}"));
            }
            @Override public CompletableFuture<LLMResponse> chatWithTools(
                List<LLMMessage> m, List<com.jwcode.core.llm.LLMTool> t) { return chat(m); }
            @Override public CompletableFuture<LLMResponse> chatStream(
                List<LLMMessage> m, java.util.function.Consumer<String> c) { return chat(m); }
            @Override public CompletableFuture<LLMResponse> chatStreamWithTools(
                List<LLMMessage> m, List<com.jwcode.core.llm.LLMTool> t,
                java.util.function.Consumer<String> cc,
                java.util.function.Consumer<String> tc,
                java.util.function.Consumer<LLMService.StreamToolCallEvent> sc) { return chat(m); }
            @Override public CompletableFuture<com.jwcode.core.llm.LLMTestResult> test() {
                return CompletableFuture.completedFuture(null);
            }
            @Override public String getModelName() { return "mock"; }
            @Override public void close() {}
        };

        SemanticIntentAnalyzer llmAnalyzer = new SemanticIntentAnalyzer(mockLlm);

        // 第一次调用
        llmAnalyzer.analyze("测试缓存", null);
        assertEquals(1, callCount[0], "第一次应调用 LLM");

        // 第二次相同输入 — 应命中缓存
        llmAnalyzer.analyze("测试缓存", null);
        assertEquals(1, callCount[0], "第二次应命中缓存，不再调用 LLM");
    }

    // ==================== 辅助方法 ====================

    private LLMService createMockLlmService(String jsonResponse) {
        return new LLMService() {
            @Override
            public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) {
                return CompletableFuture.completedFuture(LLMResponse.success(jsonResponse));
            }
            @Override
            public CompletableFuture<LLMResponse> chatWithTools(
                List<LLMMessage> m, List<com.jwcode.core.llm.LLMTool> t) {
                return chat(m);
            }
            @Override
            public CompletableFuture<LLMResponse> chatStream(
                List<LLMMessage> m, java.util.function.Consumer<String> c) {
                return chat(m);
            }
            @Override
            public CompletableFuture<LLMResponse> chatStreamWithTools(
                List<LLMMessage> m, List<com.jwcode.core.llm.LLMTool> t,
                java.util.function.Consumer<String> cc,
                java.util.function.Consumer<String> tc,
                java.util.function.Consumer<LLMService.StreamToolCallEvent> sc) {
                return chat(m);
            }
            @Override
            public CompletableFuture<com.jwcode.core.llm.LLMTestResult> test() {
                return CompletableFuture.completedFuture(null);
            }
            @Override
            public String getModelName() { return "mock-intent-classifier"; }
            @Override
            public void close() {}
        };
    }
}
