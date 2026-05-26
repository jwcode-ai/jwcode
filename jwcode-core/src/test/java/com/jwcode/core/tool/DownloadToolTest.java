package com.jwcode.core.tool;

import com.jwcode.core.session.Session;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DownloadTool 单元测试。
 *
 * <p>使用 Java 内置 {@link HttpServer} 创建本地 HTTP 端点，
 * 无需外部网络依赖。</p>
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>文本文件下载成功</li>
 *   <li>二进制文件下载成功（MD5 校验）</li>
 *   <li>目标路径父目录自动创建</li>
 *   <li>URL scheme 非 http/https → 拒绝</li>
 *   <li>目标路径在工作区外 → WorkspaceGuard 拒绝</li>
 *   <li>HTTP 404 → 错误处理</li>
 *   <li>HTTP 403 → 错误处理</li>
 *   <li>HTTP 500 → 错误处理</li>
 *   <li>进度回调验证</li>
 *   <li>自定义请求头传递验证</li>
 *   <li>空文件下载</li>
 * </ul>
 */
class DownloadToolTest {

    @TempDir
    Path tempDir;

    private Path workspaceRoot;
    private HttpServer server;
    private int serverPort;
    private DownloadTool tool;
    private ToolExecutionContext context;
    private Session session;

    @BeforeEach
    void setUp() throws IOException {
        // 创建工作区目录
        workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot);

        // 启动本地 HTTP 服务器
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = server.getAddress().getPort();
        server.setExecutor(null); // 默认 executor
        server.start();

        // 初始化 DownloadTool
        tool = new DownloadTool();

        // 创建测试上下文（带 WorkspaceGuard）
        session = new Session("test-session-" + System.currentTimeMillis(), workspaceRoot.toString());
        context = new ToolExecutionContext(session, workspaceRoot, null);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ==================== 辅助方法 ====================

    private String serverUrl(String path) {
        return "http://localhost:" + serverPort + path;
    }

    private void registerHandler(String path, int statusCode, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    private void registerEchoHeadersHandler(String path, String expectedHeaderName) {
        server.createContext(path, exchange -> {
            String headerValue = exchange.getRequestHeaders().getFirst(expectedHeaderName);
            String responseBody = headerValue != null ? headerValue : "MISSING";
            byte[] body = responseBody.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    // ==================== 测试用例 ====================

    @Test
    void testDownloadTextFile_Success() throws Exception {
        String content = "Hello, World! 这是一段测试文本。\n第二行内容。";
        registerHandler("/test.txt", 200, "text/plain; charset=utf-8", content.getBytes());

        String dest = "downloads/test.txt";
        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/test.txt"), dest);

        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);
        assertTrue(result.isSuccess(), "下载应成功: " + result.getContent());
        DownloadTool.Output output = result.getData();

        assertNotNull(output);
        assertTrue(Files.exists(workspaceRoot.resolve(dest)));
        String fileContent = Files.readString(workspaceRoot.resolve(dest));
        assertEquals(content, fileContent);
        assertTrue(output.fileSize() > 0);
        assertTrue(output.contentType().contains("text/plain"));
        assertTrue(output.durationMs() >= 0);
    }

    @Test
    void testDownloadBinaryFile_Success() throws Exception {
        // 生成一些二进制数据
        byte[] data = new byte[1024 * 10]; // 10KB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        registerHandler("/data.bin", 200, "application/octet-stream", data);

        String dest = "downloads/data.bin";
        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/data.bin"), dest);

        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);
        assertTrue(result.isSuccess(), "下载应成功: " + result.getContent());
        DownloadTool.Output output = result.getData();

        assertNotNull(output);
        Path downloaded = workspaceRoot.resolve(dest);
        assertTrue(Files.exists(downloaded));
        assertEquals(data.length, Files.size(downloaded));

        // 字节校验
        byte[] downloadedBytes = Files.readAllBytes(downloaded);
        assertArrayEquals(data, downloadedBytes);
        assertEquals(data.length, output.fileSize());
    }

    @Test
    void testParentDirectoryAutoCreated() throws Exception {
        String content = "test content";
        registerHandler("/deep.txt", 200, "text/plain", content.getBytes());

        // 目标路径在深层嵌套目录中（父目录尚不存在）
        String dest = "a/b/c/d/deep.txt";
        Path expectedParent = workspaceRoot.resolve("a/b/c/d");
        assertFalse(Files.exists(expectedParent), "父目录在测试前不应存在");

        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/deep.txt"), dest);
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);
        assertTrue(result.isSuccess(), "下载应成功: " + result.getContent());

        assertNotNull(result.getData());
        assertTrue(Files.exists(expectedParent), "父目录应自动创建");
        assertTrue(Files.exists(workspaceRoot.resolve(dest)));
    }

    @Test
    void testInvalidUrlScheme_Rejected() {
        DownloadTool.Input input = new DownloadTool.Input("ftp://example.com/file.txt", "test.txt");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("不支持的 URL 协议"));
    }

    @Test
    void testFileUrlScheme_Rejected() {
        DownloadTool.Input input = new DownloadTool.Input("file:///etc/passwd", "passwd.txt");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("不支持的 URL 协议"));
    }

    @Test
    void testPathOutsideWorkspace_Rejected() {
        registerHandler("/test.txt", 200, "text/plain", "content".getBytes());

        // 尝试写入工作区外的路径
        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/test.txt"), "../outside.txt");

        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        assertFalse(result.isSuccess());
        String errorMsg = result.getContent();
        assertTrue(errorMsg.contains("安全工作区") || errorMsg.contains("WORKSPACE"),
                "错误信息应包含工作区安全提示，实际: " + errorMsg);
    }

    @Test
    void testAbsolutePathOutsideWorkspace_Rejected() {
        registerHandler("/test.txt", 200, "text/plain", "content".getBytes());

        // 绝对路径在工作区外
        Path outsidePath = tempDir.resolve("outside.txt");
        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/test.txt"),
                outsidePath.toAbsolutePath().toString());

        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        assertFalse(result.isSuccess());
    }

    @Test
    void testHttp404_Error() {
        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/not-exists"), "test.txt");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("404") || result.getContent().contains("不存在"),
                "错误信息应包含 404 或'不存在'，实际: " + result.getContent());
    }

    @Test
    void testHttp403_Error() {
        registerHandler("/forbidden.txt", 403, "text/plain", "Forbidden".getBytes());

        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/forbidden.txt"), "test.txt");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("403") || result.getContent().contains("拒绝"),
                "错误信息应包含 403 或'拒绝'，实际: " + result.getContent());
    }

    @Test
    void testHttp500_Error() {
        registerHandler("/error.txt", 500, "text/plain", "Internal Error".getBytes());

        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/error.txt"), "test.txt");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("500") || result.getContent().contains("服务器错误"),
                "错误信息应包含 500 或'服务器错误'，实际: " + result.getContent());
    }

    @Test
    void testProgressCallback() throws Exception {
        byte[] data = new byte[1024 * 100]; // 100KB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        registerHandler("/progress.bin", 200, "application/octet-stream", data);

        List<DownloadTool.Progress> progressEvents = new ArrayList<>();
        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/progress.bin"), "progress.bin");

        // 使用异步 call 以获取进度回调
        ToolResult<DownloadTool.Output> result = tool.call(input, context, toolProgress -> {
            if (toolProgress.getData() != null) {
                progressEvents.add(toolProgress.getData());
            }
        }).get(30, TimeUnit.SECONDS);

        assertTrue(result.isSuccess(), "进度回调下载应成功: " + result.getContent());
        DownloadTool.Output output = result.getData();
        assertNotNull(output);
        assertEquals(data.length, output.fileSize());
        // 至少收到一条进度（最终的 100%）
        assertFalse(progressEvents.isEmpty(), "应至少收到一条进度回调");
        // 检查最后一条进度是 100%
        DownloadTool.Progress lastProgress = progressEvents.get(progressEvents.size() - 1);
        assertEquals(100, lastProgress.percentage(),
                "最后一条进度应为 100%");
    }

    @Test
    void testCustomHeaders() throws Exception {
        String headerName = "Authorization";
        String expectedValue = "Bearer test-token-12345";
        registerEchoHeadersHandler("/echo-header", headerName);

        DownloadTool.Input input = new DownloadTool.Input(
                serverUrl("/echo-header"),
                "echo-response.txt",
                30,
                java.util.Map.of(headerName, expectedValue)
        );

        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);
        assertTrue(result.isSuccess(), "下载应成功: " + result.getContent());
        assertNotNull(result.getData());
        String responseContent = Files.readString(workspaceRoot.resolve("echo-response.txt"));
        assertEquals(expectedValue, responseContent,
                "服务器应收到自定义请求头");
    }

    @Test
    void testEmptyFileDownload() throws Exception {
        registerHandler("/empty.txt", 200, "text/plain", new byte[0]);

        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/empty.txt"), "empty.txt");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);
        assertTrue(result.isSuccess(), "下载应成功: " + result.getContent());
        DownloadTool.Output output = result.getData();

        assertNotNull(output);
        assertTrue(Files.exists(workspaceRoot.resolve("empty.txt")));
        assertEquals(0, output.fileSize());
    }

    @Test
    void testNullInput_Error() {
        ToolResult<DownloadTool.Output> result = tool.callSync(null, context);
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("不能为空"));
    }

    @Test
    void testEmptyUrl_Error() {
        DownloadTool.Input input = new DownloadTool.Input("", "test.txt");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("URL 不能为空"));
    }

    @Test
    void testEmptyDestination_Error() {
        registerHandler("/test.txt", 200, "text/plain", "test".getBytes());
        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/test.txt"), "");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("目标路径不能为空"));
    }

    @Test
    void testGetName() {
        assertEquals("Download", tool.getName());
    }

    @Test
    void testGetCategory() {
        assertEquals(ToolCategory.FILE_OPERATION, tool.getCategory());
    }

    @Test
    void testIsReadOnly() {
        assertFalse(tool.isReadOnly(new DownloadTool.Input("http://example.com/f", "f.txt")));
    }

    @Test
    void testGetSideEffects() {
        var sideEffects = tool.getSideEffects();
        assertTrue(sideEffects.contains(SideEffect.NETWORK));
        assertTrue(sideEffects.contains(SideEffect.WRITE_FILE));
    }

    @Test
    void testGetInputType() {
        assertNotNull(tool.getInputType());
    }

    @Test
    void testGetOutputType() {
        assertNotNull(tool.getOutputType());
    }

    @Test
    void testGetInputSchema() {
        assertNotNull(tool.getInputSchema());
    }

    @Test
    void testLargeFile_SizeLimitCheck() throws Exception {
        // 注册一个 Content-Length 超过 1GB 的端点，实际发送体很小
        // 下载后校验的是实际文件大小，所以小文件通过但大文件会被拒绝
        server.createContext("/large-claim.bin", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            // 声称 2GB，但实际只发小数据
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(2L * 1024 * 1024 * 1024));
            byte[] body = "small body".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        DownloadTool.Input input = new DownloadTool.Input(serverUrl("/large-claim.bin"), "large-claim.bin");
        ToolResult<DownloadTool.Output> result = tool.callSync(input, context);

        // 实际文件很小，下载应成功（Content-Length 不可信，以实际文件大小为准）
        assertTrue(result.isSuccess(),
                "Content-Length 声称 2GB 但实际文件很小，应下载成功。错误: " + result.getContent());
        assertTrue(Files.exists(workspaceRoot.resolve("large-claim.bin")));

        // 清理
        Files.deleteIfExists(workspaceRoot.resolve("large-claim.bin"));
    }
}
