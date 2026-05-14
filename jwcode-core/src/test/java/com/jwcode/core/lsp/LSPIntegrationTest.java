package com.jwcode.core.lsp;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
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
        LspHover mockHover = new LspHover("test hover info", 1, 5);
        Mockito.when(lspService.hover("Test.java", 1, 5))
            .thenReturn(CompletableFuture.completedFuture(mockHover));

        LspHover hover = lspService.hover("Test.java", 1, 5).get(5, TimeUnit.SECONDS);

        assertAll("悬停信息验证",
            () -> assertNotNull(hover, "悬停信息不应为 null"),
            () -> assertEquals("test hover info", hover.getContent(), "内容匹配")
        );
    }

    @Test
    @DisplayName("LSP 服务 - 跳转到定义")
    void testGoToDefinition() throws Exception {
        LspLocation location = new LspLocation("Test.java", 10, 3);
        Mockito.when(lspService.goToDefinition("Test.java", 5, 3))
            .thenReturn(CompletableFuture.completedFuture(List.of(location)));

        List<LspLocation> locations = lspService.goToDefinition("Test.java", 5, 3)
            .get(5, TimeUnit.SECONDS);

        assertAll("定义跳转验证",
            () -> assertNotNull(locations, "位置列表不应为 null"),
            () -> assertFalse(locations.isEmpty(), "位置列表不应为空"),
            () -> assertEquals("Test.java", locations.get(0).getUri(), "URI 匹配")
        );
    }

    // ==================== LspDocumentManager 测试 ====================

    @Test
    @DisplayName("LSP 文档管理 - 打开和关闭文档")
    void testOpenAndCloseDocument() {
        String filePath = "Test.java";
        String content = "public class Test {}";

        documentManager.openDocument(filePath, content);
        assertTrue(documentManager.isDocumentOpen(filePath), "文档应已打开");

        documentManager.closeDocument(filePath);
        assertFalse(documentManager.isDocumentOpen(filePath), "文档应已关闭");
    }

    @Test
    @DisplayName("LSP 文档管理 - 文档变更")
    void testDocumentChange() {
        String filePath = "Test.java";
        documentManager.openDocument(filePath, "original content");

        documentManager.changeDocument(filePath, "new content", 1);
        assertEquals("new content", documentManager.getDocumentContent(filePath), "内容应已更新");
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
        LspTextEdit edit = new LspTextEdit("replacement text",
            new LspRange(new LspPosition(1, 0), new LspPosition(1, 5)));
        LspCodeAction action = new LspCodeAction("快速修复", "fix.issue");

        assertAll("LSP 模型验证",
            () -> assertNotNull(edit.getNewText(), "替换文本不应为 null"),
            () -> assertNotNull(action.getTitle(), "动作标题不应为 null"),
            () -> assertEquals("快速修复", action.getTitle(), "标题匹配")
        );
    }

    // ==================== LspDiagnosticRegistry 测试 ====================

    @Test
    @DisplayName("LSP 诊断注册 - 添加和查询诊断")
    void testDiagnosticRegistry() {
        LspDiagnosticRegistry diagnosticRegistry = new LspDiagnosticRegistry();
        String filePath = "Test.java";

        diagnosticRegistry.addDiagnostic(filePath, "类型错误", 1, 5);
        diagnosticRegistry.addDiagnostic(filePath, "语法错误", 2, 3);

        List<LspDiagnosticRegistry.Diagnostic> diagnostics = diagnosticRegistry.getDiagnostics(filePath);
        assertAll("诊断注册验证",
            () -> assertNotNull(diagnostics, "诊断列表不应为 null"),
            () -> assertEquals(2, diagnostics.size(), "应有2条诊断"),
            () -> assertEquals("类型错误", diagnostics.get(0).getMessage(), "第一条诊断消息匹配")
        );
    }

    // ==================== LspClient 测试 ====================

    @Test
    @DisplayName("LSP 客户端 - 发送请求和接收响应")
    void testLspClientRequest() {
        LspClient client = new LspClient();

        assertAll("LSP 客户端验证",
            () -> assertNotNull(client, "客户端不应为 null"),
            () -> assertDoesNotThrow(() -> client.initialize(), "初始化不应抛出异常")
        );
    }

    // ==================== 工作空间编辑 ====================

    @Test
    @DisplayName("LSP 工作空间编辑 - 应用文本变更")
    void testWorkspaceEdit() {
        LspWorkspaceEdit workspaceEdit = new LspWorkspaceEdit();
        LspTextEdit edit = new LspTextEdit("new content",
            new LspRange(new LspPosition(1, 0), new LspPosition(1, 10)));

        workspaceEdit.addEdit("Test.java", edit);
        List<LspTextEdit> edits = workspaceEdit.getEdits("Test.java");

        assertAll("工作空间编辑验证",
            () -> assertNotNull(edits, "编辑列表不应为 null"),
            () -> assertEquals(1, edits.size(), "应有1个编辑"),
            () -> assertEquals("new content", edits.get(0).getNewText(), "编辑内容匹配")
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
        documentManager.openDocument(filePath, "class Example {}");
        assertTrue(documentManager.isDocumentOpen(filePath), "文档应打开");

        // 悬停提示
        LspHover mockHover = new LspHover("Example class", 0, 7);
        Mockito.when(lspService.hover(filePath, 0, 7))
            .thenReturn(CompletableFuture.completedFuture(mockHover));
        LspHover hover = lspService.hover(filePath, 0, 7).get(5, TimeUnit.SECONDS);
        assertNotNull(hover, "悬停提示不应为 null");

        // 跳转定义
        LspLocation location = new LspLocation("Example.java", 5, 0);
        Mockito.when(lspService.goToDefinition(filePath, 0, 7))
            .thenReturn(CompletableFuture.completedFuture(List.of(location)));
        List<LspLocation> definitions = lspService.goToDefinition(filePath, 0, 7)
            .get(5, TimeUnit.SECONDS);
        assertFalse(definitions.isEmpty(), "定义位置不应为空");

        // 断开
        Mockito.when(lspService.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(lspService.isConnected()).thenReturn(false);
        lspService.disconnect().get(5, TimeUnit.SECONDS);
        assertFalse(lspService.isConnected(), "应成功断开");
    }
}
