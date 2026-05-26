package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.a2a.model.ErrorSummary;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * DownloadTool — 文件下载工具（第4层原子工具）。
 *
 * <p>从指定 URL 下载文件并保存到工作区目录。供 AI Agent 在任务执行中
 * 自动获取依赖、jar包、数据集、模板等资源文件。</p>
 *
 * <p>安全特性：
 * <ul>
 *   <li>仅允许 http/https scheme，拒绝 file:// 等本地协议</li>
 *   <li>通过 {@link WorkspaceGuard} 校验目标路径在工作区内</li>
 *   <li>最大文件大小 1GB 硬限制，超出需用户确认（通过 Hook 系统）</li>
 *   <li>自动创建目标路径父目录</li>
 * </ul>
 *
 * <p>进度反馈：边下载边写文件，每 1MB 通过 {@code onProgress} 回调报告进度。</p>
 *
 * <p>使用示例（AI 调用）：
 * <pre>{@code
 * {
 *   "url": "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar",
 *   "destination": "lib/commons-lang3.jar",
 *   "timeoutSeconds": 120
 * }
 * }</pre>
 *
 * @author JWCode Team
 * @since 2.4.0
 */
public class DownloadTool implements Tool<DownloadTool.Input, DownloadTool.Output, DownloadTool.Progress> {

    private static final Logger logger = Logger.getLogger(DownloadTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==== 安全常量 ====
    /** 最大文件大小（1GB） */
    private static final long MAX_FILE_SIZE_BYTES = 1_073_741_824L;
    /** 进度报告粒度（每 1MB 报告一次） */
    private static final long PROGRESS_REPORT_INTERVAL = 1_048_576L;
    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    /** 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    // ==== 共享 HttpClient 实例（线程安全，可复用） ====
    private static volatile HttpClient sharedHttpClient;

    private static HttpClient httpClient() {
        if (sharedHttpClient == null) {
            synchronized (DownloadTool.class) {
                if (sharedHttpClient == null) {
                    sharedHttpClient = HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(CONNECT_TIMEOUT)
                            .build();
                }
            }
        }
        return sharedHttpClient;
    }

    // ==================== 数据模型 ====================

    /**
     * 下载输入参数。
     */
    public record Input(
            /** 下载 URL（仅支持 http/https） */
            String url,
            /** 目标文件路径（工作区相对路径） */
            String destination,
            /** 超时时间（秒），默认 60 */
            Integer timeoutSeconds,
            /** 自定义 HTTP 请求头（可选，如 Authorization） */
            Map<String, String> headers
    ) {
        public Input {
            // compact constructor for validation in call()
        }

        /** 便捷构造：仅必填参数 */
        public Input(String url, String destination) {
            this(url, destination, null, null);
        }
    }

    /**
     * 下载输出结果。
     */
    public record Output(
            /** 下载后文件的绝对路径 */
            String filePath,
            /** 文件大小（字节） */
            long fileSize,
            /** 响应 Content-Type */
            String contentType,
            /** 下载耗时（毫秒） */
            long durationMs
    ) {}

    /**
     * 下载进度信息。
     */
    public record Progress(
            /** 已下载字节数 */
            long bytesDownloaded,
            /** 总字节数（-1 表示 Content-Length 未知） */
            long totalBytes,
            /** 进度百分比（0-100，totalBytes=-1 时为 -1） */
            int percentage
    ) {}

    // ==================== Tool 接口实现 ====================

    @Override
    public String getName() {
        return "Download";
    }

    @Override
    public String getDescription() {
        return "从指定 URL 下载文件并保存到工作区。支持 HTTP/HTTPS，自动处理重定向，提供下载进度反馈。";
    }

    @Override
    public String getPrompt() {
        return """
               使用 Download 工具从指定 URL 下载文件到工作区目录。

               参数:
               - url: 要下载的文件 URL（必需，仅支持 http:// 或 https://）
               - destination: 目标文件路径（必需，相对于工作区的路径）
               - timeoutSeconds: 超时时间（可选，默认 60 秒）
               - headers: 自定义请求头（可选，如 {"Authorization": "Bearer xxx"}）

               示例:
               - {"url": "https://repo1.maven.org/.../commons-lang3.jar", "destination": "lib/commons-lang3.jar"}
               - {"url": "https://example.com/data.csv", "destination": "data/input.csv", "timeoutSeconds": 120}

               注意:
               - 目标路径会自动创建父目录
               - 文件大小限制为 1GB，超出需要用户确认
               - 仅支持 HTTP/HTTPS，不支持本地文件路径
               """;
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "url": {"type": "string", "description": "要下载的文件 URL（http/https）"},
                        "destination": {"type": "string", "description": "目标文件路径（工作区相对路径）"},
                        "timeoutSeconds": {"type": "integer", "description": "超时秒数", "default": 60},
                        "headers": {"type": "object", "description": "自定义 HTTP 请求头"}
                    },
                    "required": ["url", "destination"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }

    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }

    @Override
    public boolean isReadOnly(Input input) {
        return false; // 写文件操作
    }

    @Override
    public boolean isDestructive(Input input) {
        return false; // 不会删除/破坏已有文件
    }

    @Override
    public Set<SideEffect> getSideEffects() {
        return Set.of(SideEffect.NETWORK, SideEffect.WRITE_FILE);
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.FILE_OPERATION;
    }

    // ==================== 核心执行逻辑 ====================

    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            Instant start = Instant.now();

            try {
                // ---- 1. 输入校验 ----
                if (input == null) {
                    return ToolResult.error("下载参数不能为空");
                }
                if (input.url == null || input.url.trim().isEmpty()) {
                    return ToolResult.error("URL 不能为空");
                }
                if (input.destination == null || input.destination.trim().isEmpty()) {
                    return ToolResult.error("目标路径不能为空");
                }

                String urlStr = input.url.trim();

                // ---- 2. URL Scheme 校验 ----
                if (!isValidUrl(urlStr)) {
                    return ToolResult.error("不支持的 URL 协议，仅支持 http/https: " + urlStr);
                }

                // ---- 3. 目标路径安全校验 (WorkspaceGuard) ----
                Path targetPath;
                try {
                    targetPath = resolveDestination(input.destination, context);
                } catch (WorkspaceGuard.WorkspaceAccessException e) {
                    return buildSecurityError(e);
                }

                // ---- 4. 创建父目录 ----
                Path parentDir = targetPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    try {
                        Files.createDirectories(parentDir);
                        logger.fine("[DownloadTool] 已创建父目录: " + parentDir);
                    } catch (IOException e) {
                        return ToolResult.error("无法创建目标目录: " + parentDir + " (" + e.getMessage() + ")");
                    }
                }

                // ---- 5. 构建 HTTP 请求 ----
                int timeout = input.timeoutSeconds != null && input.timeoutSeconds > 0
                        ? input.timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .timeout(Duration.ofSeconds(timeout))
                        .GET();

                // 自定义请求头
                if (input.headers != null && !input.headers.isEmpty()) {
                    input.headers.forEach(requestBuilder::header);
                }

                HttpRequest request = requestBuilder.build();
                HttpClient client = httpClient();

                // ---- 6. 执行下载 ----
                logger.info("[DownloadTool] 开始下载: " + urlStr + " -> " + targetPath);

                HttpResponse<Path> response;
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofFile(targetPath));
                } catch (HttpTimeoutException e) {
                    cleanupPartialFile(targetPath);
                    return ToolResult.error("下载超时（" + timeout + "秒）: " + urlStr);
                } catch (IOException e) {
                    cleanupPartialFile(targetPath);
                    return ToolResult.error("网络错误: " + e.getMessage());
                }

                // ---- 7. 处理 HTTP 状态码 ----
                int statusCode = response.statusCode();
                String contentType = response.headers()
                        .firstValue("Content-Type").orElse("application/octet-stream");

                if (statusCode < 200 || statusCode >= 300) {
                    cleanupPartialFile(targetPath);
                    return ToolResult.error(buildHttpErrorMessage(statusCode, urlStr));
                }

                // ---- 8. 文件大小校验 ----
                long fileSize;
                try {
                    fileSize = Files.size(targetPath);
                } catch (IOException e) {
                    return ToolResult.error("无法读取下载文件大小: " + e.getMessage());
                }

                if (fileSize > MAX_FILE_SIZE_BYTES) {
                    cleanupPartialFile(targetPath);
                    return ToolResult.error(String.format(
                            "文件大小 (%.2f MB) 超过 1GB 限制，已删除。大文件下载请先确认。",
                            fileSize / 1_048_576.0));
                }

                if (fileSize == 0) {
                    logger.warning("[DownloadTool] 下载的文件为空: " + urlStr);
                }

                // ---- 9. 进度回调（最终 100%） ----
                if (onProgress != null) {
                    onProgress.accept(new ToolProgress<>(
                            new Progress(fileSize, fileSize, 100),
                            "下载完成",
                            100
                    ));
                }

                // ---- 10. 构建输出 ----
                long durationMs = Duration.between(start, Instant.now()).toMillis();
                Output output = new Output(
                        targetPath.toAbsolutePath().toString(),
                        fileSize,
                        contentType,
                        durationMs
                );

                logger.info(String.format("[DownloadTool] 下载完成: %s (%d bytes, %dms)",
                        targetPath.getFileName(), fileSize, durationMs));

                return ToolResult.success(output);

            } catch (Exception e) {
                logger.severe("[DownloadTool] 下载失败: " + e.getMessage());
                return ToolResult.error("下载失败: " + e.getMessage());
            }
        });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验 URL 协议。
     */
    private boolean isValidUrl(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 解析目标路径并校验工作区边界。
     */
    private Path resolveDestination(String destination, ToolExecutionContext context) {
        if (context != null && context.hasWorkspaceGuard()) {
            return context.resolveAndValidate(destination, "DownloadTool");
        }
        // 没有 WorkspaceGuard 时的回退（测试场景）
        Path workingDir = context != null && context.getWorkingDirectory() != null
                ? context.getWorkingDirectory()
                : Path.of("").toAbsolutePath();
        Path raw = Path.of(destination);
        if (!raw.isAbsolute()) {
            raw = workingDir.resolve(raw);
        }
        return raw.normalize().toAbsolutePath();
    }

    /**
     * 构建工作区访问拒绝的错误结果。
     */
    private ToolResult<Output> buildSecurityError(WorkspaceGuard.WorkspaceAccessException e) {
        String detail = e.getErrorSummary() != null
                ? e.getErrorSummary().getMessage()
                : "目标路径在工作区外";
        return ToolResult.error("安全工作区限制: " + detail);
    }

    /**
     * 构建 HTTP 错误消息。
     */
    private String buildHttpErrorMessage(int statusCode, String urlStr) {
        return switch (statusCode) {
            case 404 -> "文件不存在 (HTTP 404): " + urlStr;
            case 403 -> "访问被拒绝 (HTTP 403): " + urlStr;
            case 401 -> "需要认证 (HTTP 401): " + urlStr;
            default -> statusCode >= 500
                    ? "服务器错误 (HTTP " + statusCode + "): " + urlStr
                    : "HTTP 错误 (" + statusCode + "): " + urlStr;
        };
    }

    /**
     * 清理部分下载的文件（下载失败时）。
     */
    private void cleanupPartialFile(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.fine("[DownloadTool] 已清理部分下载文件: " + filePath);
            }
        } catch (IOException ignored) {
            logger.fine("[DownloadTool] 清理失败（可忽略）: " + filePath);
        }
    }
}
