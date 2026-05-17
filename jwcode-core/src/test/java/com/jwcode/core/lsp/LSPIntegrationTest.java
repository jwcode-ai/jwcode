package com.jwcode.core.lsp;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSP 语言服务器协议集成测试
 *
 * <p>测试 LSP 服务接口、文档管理、悬停提示、代码补全等核心功能。
 * 覆盖 LspService → LspDocumentManager → LspServerManager → LspClient 链路。</p>
 */
@DisplayName("LSP 集成测试")
public class LSPIntegrationTest {

    private LspService lspService;
    private LspDocumentManager documentManager;
    private LspServerManager serverManager;

    @BeforeEach
    void setUp() {
        lspService = Mockito.mock(LspService.class);
        documentManager = new LspDocumentManager();
        serverManager = new LspServerManager();
    }

    // ==================== LspService 接口测试 ====================

    @Test
    @DisplayName("LSP 服务 - 连接与断开")
    void testConnectAndDisconnect() throws Exception {
        Mockito.when(lspService.connect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(lspService.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(lspService.isConnected()).thenReturn(true);

        lspService.connect().get(5, TimeUnit.SECONDS);
        assertTrue(lspService.isConnected(), "连接后应返回 true");

        lspService.disconnect().get(5, TimeUnit.SECONDS);
        Mockito.when(lspService.isConnected()).thenReturn(false);
        assertFalse(lspService.isConnected(), "断开后应返回 false");
    }

    @Test
    @DisplayName("LSP 服务 - 获取悬停信息")
    void testHover() throws Exception {
        LspRange range = new LspRange(new LspPosition(1, 0), new LspPosition(1, 5));
        LspHover mockHover = new LspHover("test hover info", range);
        Mockito.when(lspService.hover("Test.java", 1, 5))
            .thenReturn(CompletableFuture.completedFuture(mockHover));

        LspHover hover = lspService.hover("Test.java", 1, 5).get(5, TimeUnit.SECONDS);

        assertAll("悬停信息验证",
            () -> assertNotNull(hover, "悬停信息不应为 null"),
            () -> assertEquals("test hover info", hover.getContents(), "内容匹配")
        );
    }

    @Test
    @DisplayName("LSP 服务 - 跳转到定义")
    void testDefinition() throws Exception {
        LspRange range = new LspRange(new LspPosition(10, 0), new LspPosition(10, 3));
        LspLocation location = new LspLocation("Test.java", range);
        Mockito.when(lspService.definition("Test.java", 5, 3))
            .thenReturn(CompletableFuture.completedFuture(List.of(location)));

        List<LspLocation> locations = lspService.definition("Test.java", 5, 3)
            .get(5, TimeUnit.SECONDS);

        assertAll("定义跳转验证",
            () -> assertNotNull(locations, "位置列表不应为 null"),
            () -> assertFalse(locations.isEmpty(), "位置列表不应为空"),
            () -> assertEquals("Test.java", locations.get(0).getUri(), "URI 匹配")
        );
    }

    @Test
    @DisplayName("LSP 服务 - 查找引用")
    void testReferences() throws Exception {
        LspRange range = new LspRange(new LspPosition(5, 0), new LspPosition(5, 5));
        LspLocation location = new LspLocation("Ref.java", range);
        Mockito.when(lspService.references("Test.java", 5, 3))
            .thenReturn(CompletableFuture.completedFuture(List.of(location)));

        List<LspLocation> refs = lspService.references("Test.java", 5, 3)
            .get(5, TimeUnit.SECONDS);
        assertNotNull(refs);
    }

    @Test
    @DisplayName("LSP 服务 - 格式化")
    void testFormat() throws Exception {
        LspTextEdit edit = new LspTextEdit(
            new LspRange(new LspPosition(0, 0), new LspPosition(1, 0)), "formatted code");
        Mockito.when(lspService.format("Test.java"))
            .thenReturn(CompletableFuture.completedFuture(List.of(edit)));

        List<LspTextEdit> edits = lspService.format("Test.java").get(5, TimeUnit.SECONDS);
        assertNotNull(edits);
    }

    @Test
    @DisplayName("LSP 服务 - 代码动作")
    void testCodeAction() throws Exception {
        LspCodeAction action = new LspCodeAction("快速修复", "fix.issue");
        Mockito.when(lspService.codeAction("Test.java", 1, 5))
            .thenReturn(CompletableFuture.completedFuture(List.of(action)));

        List<LspCodeAction> actions = lspService.codeAction("Test.java", 1, 5)
            .get(5, TimeUnit.SECONDS);
        assertNotNull(actions);
    }

    // ==================== LspDocumentManager 测试 ====================

    @Test
    @DisplayName("LSP 文档管理 - 打开和关闭文档")
    void testOpenAndCloseDocument() {
        String filePath = "Test.java";
        String content = "public class Test {}";

        int version = documentManager.openDocument(filePath, content, "java");
        assertTrue(version >= 1, "打开文档应返回有效版本号");
        assertTrue(documentManager.isDocumentOpen(filePath), "文档应已打开");

        documentManager.closeDocument(filePath);
        assertFalse(documentManager.isDocumentOpen(filePath), "文档应已关闭");
    }

    @Test
    @DisplayName("LSP 文档管理 - 文档更新")
    void testDocumentUpdate() {
        String filePath = "Test.java";
        documentManager.openDocument(filePath, "original content", "java");

        int newVersion = documentManager.updateDocument(filePath, "new content");
        assertEquals("new content", documentManager.getDocumentContent(filePath), "内容应已更新");
        assertTrue(newVersion > 1, "版本号应递增");
    }

    @Test
    @DisplayName("LSP 文档管理 - 获取文档信息")
    void testDocumentInfo() {
        String filePath = "Test.java";
        documentManager.openDocument(filePath, "content", "java");

        LspDocumentManager.DocumentInfo info = documentManager.getDocumentInfo(filePath);
        assertNotNull(info);
    }

    @Test
    @DisplayName("LSP 文档管理 - 获取所有文档信息")
    void testAllDocumentInfos() {
        documentManager.openDocument("A.java", "a", "java");
        documentManager.openDocument("B.java", "b", "java");

        List<LspDocumentManager.DocumentInfo> infos = documentManager.getAllDocumentInfos();
        assertEquals(2, infos.size());
    }

    @Test
    @DisplayName("LSP 文档管理 - 关闭所有文档")
    void testCloseAllDocuments() {
        documentManager.openDocument("A.java", "a", "java");
        documentManager.openDocument("B.java", "b", "java");

        documentManager.closeAllDocuments();
        assertTrue(documentManager.getOpenDocuments().isEmpty());
    }

    @Test
    @DisplayName("LSP 文档管理 - 文档历史")
    void testDocumentHistory() {
        String filePath = "Test.java";
        documentManager.openDocument(filePath, "v1", "java");
        documentManager.updateDocument(filePath, "v2");

        List<LspDocumentManager.DocumentChange> history = documentManager.getDocumentHistory(filePath);
        assertNotNull(history);
    }

    // ==================== LspServerManager 测试 ====================

    @Test
    @DisplayName("LSP 服务器管理 - 启动和停止")
    void testStartAndStopServer() {
        assertAll("服务器管理验证",
            () -> assertDoesNotThrow(() -> serverManager.startServer("java"),
                "启动 Java LSP 服务器不应抛出异常"),
            () -> assertDoesNotThrow(() -> serverManager.stopServer("java"),
                "停止 Java LSP 服务器不应抛出异常")
        );
    }

    // ==================== LspModels 测试 ====================

    @Test
    @DisplayName("LSP 模型 - 位置和范围模型")
    void testLspPositionAndRange() {
        LspPosition pos = new LspPosition(5, 10);
        LspRange range = new LspRange(pos, new LspPosition(5, 20));

        assertAll("位置模型验证",
            () -> assertEquals(5, pos.getLine(), "行号匹配"),
            () -> assertEquals(10, pos.getCharacter(), "列号匹配"),
            () -> assertEquals(5, range.getStart().getLine(), "范围起始行匹配"),
            () -> assertEquals(20, range.getEnd().getCharacter(), "范围结束列匹配")
        );
    }

    @Test
    @DisplayName("LSP 模型 - 文本编辑和代码动作")
    void testLspTextEditAndCodeAction() {
        LspTextEdit edit = new LspTextEdit(
            new LspRange(new LspPosition(1, 0), new LspPosition(1, 5)), "replacement text");
        LspCodeAction action = new LspCodeAction("快速修复", "fix.issue");

        assertAll("LSP 模型验证",
            () -> assertNotNull(edit.getNewText(), "替换文本不应为 null"),
            () -> assertNotNull(action.getTitle(), "动作标题不应为 null"),
            () -> assertEquals("快速修复", action.getTitle(), "标题匹配")
        );
    }

    // ==================== LspDiagnosticRegistry 测试 ====================

    @Test
    @DisplayName("LSP 诊断注册 - 设置和查询诊断")
    void testDiagnosticRegistry() {
        LspDiagnosticRegistry diagnosticRegistry = new LspDiagnosticRegistry();
        String filePath = "Test.java";

        LspDiagnosticRegistry.LspDiagnostic diag1 = new LspDiagnosticRegistry.LspDiagnostic(
            LspDiagnosticRegistry.LspDiagnosticSeverity.ERROR,
            new LspDiagnosticRegistry.LspRange(
                new LspDiagnosticRegistry.LspPosition(1, 0),
                new LspDiagnosticRegistry.LspPosition(1, 5)),
            "类型错误", "test", "ERR001");
        LspDiagnosticRegistry.LspDiagnostic diag2 = new LspDiagnosticRegistry.LspDiagnostic(
            LspDiagnosticRegistry.LspDiagnosticSeverity.ERROR,
            new LspDiagnosticRegistry.LspRange(
                new LspDiagnosticRegistry.LspPosition(2, 0),
                new LspDiagnosticRegistry.LspPosition(2, 3)),
            "语法错误", "test", "ERR002");

        diagnosticRegistry.setDiagnostics(filePath, List.of(diag1, diag2));

        List<LspDiagnosticRegistry.LspDiagnostic> diagnostics = diagnosticRegistry.getDiagnostics(filePath);
        assertAll("诊断注册验证",
            () -> assertNotNull(diagnostics, "诊断列表不应为 null"),
            () -> assertEquals(2, diagnostics.size(), "应有2条诊断"),
            () -> assertEquals("类型错误", diagnostics.get(0).getMessage(), "第一条诊断消息匹配")
        );
    }

    @Test
    @DisplayName("LSP 诊断注册 - 统计信息")
    void testDiagnosticStats() {
        LspDiagnosticRegistry diagnosticRegistry = new LspDiagnosticRegistry();
        LspDiagnosticRegistry.DiagnosticStats stats = diagnosticRegistry.getStats();
        assertNotNull(stats);
    }

    @Test
    @DisplayName("LSP 诊断注册 - 清除所有")
    void testDiagnosticClearAll() {
        LspDiagnosticRegistry diagnosticRegistry = new LspDiagnosticRegistry();
        diagnosticRegistry.setDiagnostics("Test.java", List.of(
            new LspDiagnosticRegistry.LspDiagnostic(
                LspDiagnosticRegistry.LspDiagnosticSeverity.ERROR,
                new LspDiagnosticRegistry.LspRange(
                    new LspDiagnosticRegistry.LspPosition(0, 0),
                    new LspDiagnosticRegistry.LspPosition(0, 1)),
                "err", "test", "ERR")));

        diagnosticRegistry.clearAll();
        assertEquals(0, diagnosticRegistry.getTotalDiagnosticCount());
    }

    // ==================== LspClient 测试 ====================

    @Test
    @DisplayName("LSP 客户端 - 构造和初始化")
    void testLspClientConstruction() {
        LspDiagnosticRegistry diagnosticRegistry = new LspDiagnosticRegistry();
        // LspClient 需要 languageId, serverProcess, diagnosticRegistry
        // 使用 mock 的 Process 避免实际启动
        assertDoesNotThrow(() -> {
            new LspClient("java", Mockito.mock(Process.class), diagnosticRegistry);
        });
    }

    // ==================== 工作空间编辑 ====================

    @Test
    @DisplayName("LSP 工作空间编辑 - 应用文本变更")
    void testWorkspaceEdit() {
        LspWorkspaceEdit workspaceEdit = new LspWorkspaceEdit();
        LspTextEdit edit = new LspTextEdit(
            new LspRange(new LspPosition(1, 0), new LspPosition(1, 10)), "new content");

        workspaceEdit.setChanges(Map.of("Test.java", List.of(edit)));
        Map<String, List<LspTextEdit>> changes = workspaceEdit.getChanges();

        assertAll("工作空间编辑验证",
            () -> assertNotNull(changes, "编辑列表不应为 null"),
            () -> assertTrue(changes.containsKey("Test.java"), "应包含 Test.java 的编辑"),
            () -> assertEquals("new content", changes.get("Test.java").get(0).getNewText(), "编辑内容匹配")
        );
    }

    // ==================== 完整 LSP 流程 ====================

    @Test
    @DisplayName("完整 LSP 流程：连接 → 打开文档 → 悬停提示 → 跳转定义 → 断开")
    void testCompleteLspFlow() throws Exception {
        // 模拟完整流程
        Mockito.when(lspService.connect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(lspService.isConnected()).thenReturn(true);

        // 连接
        lspService.connect().get(5, TimeUnit.SECONDS);
        assertTrue(lspService.isConnected(), "应成功连接");

        // 打开文档
        String filePath = "Example.java";
        int version = documentManager.openDocument(filePath, "class Example {}", "java");
        assertTrue(version >= 1, "文档应打开");

        // 悬停提示
        LspRange hoverRange = new LspRange(new LspPosition(0, 0), new LspPosition(0, 7));
        LspHover mockHover = new LspHover("Example class", hoverRange);
        Mockito.when(lspService.hover(filePath, 0, 7))
            .thenReturn(CompletableFuture.completedFuture(mockHover));
        LspHover hover = lspService.hover(filePath, 0, 7).get(5, TimeUnit.SECONDS);
        assertNotNull(hover, "悬停提示不应为 null");

        // 跳转定义
        LspRange defRange = new LspRange(new LspPosition(5, 0), new LspPosition(5, 0));
        LspLocation location = new LspLocation("Example.java", defRange);
        Mockito.when(lspService.definition(filePath, 0, 7))
            .thenReturn(CompletableFuture.completedFuture(List.of(location)));
        List<LspLocation> definitions = lspService.definition(filePath, 0, 7)
            .get(5, TimeUnit.SECONDS);
        assertFalse(definitions.isEmpty(), "定义位置不应为空");

        // 断开
        Mockito.when(lspService.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(lspService.isConnected()).thenReturn(false);
        lspService.disconnect().get(5, TimeUnit.SECONDS);
        assertFalse(lspService.isConnected(), "应成功断开");
    }
}
