package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.search.ContentExtractor;
import com.jwcode.core.search.HtmlParser;
import com.jwcode.core.tool.browser.PlaywrightManager;
import com.jwcode.core.tool.context.ToolExecutionContext;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebFetch 工具 - 获取网页内容（v3 — OkHttp + 可选 JS 渲染）
 *
 * <p>支持两种抓取模式：</p>
 * <ol>
 *   <li><b>simple</b>（默认）— 使用 OkHttp 发送 HTTP GET 请求，</li>
 *   <li><b>render</b>（render_js=true）— 使用 Playwright 渲染 JS 后再抓取。</li>
 * </ol>
 *
 * <p>适用于 36kr、虎嗅等 JS 重度渲染的 SPA 网站。</p>
 */
public class WebFetchTool implements Tool<WebFetchTool.Input, WebFetchTool.Output, WebFetchTool.Progress> {

    private static final Logger logger = Logger.getLogger(WebFetchTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 最大内容长度限制
    private static final int MAX_CONTENT_LENGTH = 100_000; // 100KB

    // JS SPA 检测阈值：如果正文长度 / HTML 长度 < 此值，判定为 JS 外壳
    private static final double JS_SHELL_THRESHOLD = 0.02;

    // 默认超时
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    // OkHttp 共享客户端（自动处理 gzip、编码、重定向）
    private final OkHttpClient httpClient;

    // 内容提取器
    private final ContentExtractor contentExtractor;

    public WebFetchTool() {
        this.contentExtractor = new ContentExtractor();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    }

    @Override
    public String getName() {
        return "WebFetch";
    }

    @Override
    public String getDescription() {
        return "获取网页内容。支持简单 HTTP 抓取和 JS 渲染两种模式。抓取指定 URL 的网页文本内容，自动提取正文、标题和元数据。";
    }

    @Override
    public String getPrompt() {
        return """
            使用 WebFetch 工具获取指定网页的内容。

            参数:
            - url: 要获取的网页 URL，包含 http:// 或 https://（必需）
            - max_length: 最大内容长度（可选，默认 50000 字符，最大 100000）
            - extract_article: 是否智能提取文章正文（可选，默认 true）
            - render_js: 是否使用浏览器渲染 JS（可选，默认 false）。设为 true 可获取 36kr、虎嗅等 JS 渲染网站的内容

            模式说明:
            - render_js=false（默认）: 使用 HTTP 直接抓取，速度快但无法处理 JS 渲染的页面
            - render_js=true: 使用系统 Chrome/Chromium 浏览器渲染页面，可获取 SPA 网站的完整内容

            示例:
            - {"url": "https://example.com/article"}
            - {"url": "https://36kr.com/p/123", "render_js": true}
            - {"url": "https://example.com", "max_length": 10000, "extract_article": false}

            注意:
            - URL 必须是完整的，包含协议（http:// 或 https://）
            - render_js=true 需要系统已安装 Google Chrome 或 Chromium 浏览器
            - 可通过 CHROME_PATH 环境变量指定 Chrome 路径
            - 某些网站可能会阻止抓取（返回验证码、反爬页面等）
            """;
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "url": {"type": "string", "description": "要获取的网页 URL（包含 http:// 或 https://）"},
                        "max_length": {"type": "integer", "description": "最大内容长度", "default": 50000},
                        "extract_article": {"type": "boolean", "description": "是否智能提取文章正文", "default": true},
                        "render_js": {"type": "boolean", "description": "是否使用浏览器渲染 JS（用于 36kr、虎嗅等 SPA 网站）", "default": false}
                    },
                    "required": ["url"]
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
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                if (input == null || input.url == null || input.url.trim().isEmpty()) {
                    return ToolResult.error("URL 不能为空");
                }
                if (!isValidUrl(input.url)) {
                    return ToolResult.error("无效的 URL 格式: " + input.url);
                }

                int maxLength = input.max_length != null ? input.max_length : 50000;
                if (maxLength > MAX_CONTENT_LENGTH) {
                    maxLength = MAX_CONTENT_LENGTH;
                }
                boolean extractArticle = input.extract_article != null ? input.extract_article : true;
                boolean renderJs = input.render_js != null && input.render_js;

                // 渲染模式
                if (renderJs) {
                    return fetchWithRender(input.url, maxLength, extractArticle);
                }

                // 普通模式：HTTP 直接抓取
                FetchResult result = fetchWithHttp(input.url, maxLength, extractArticle);

                // 自动检测是否为 JS SPA 外壳
                if (result != null && isLikelyJsShell(result)) {
                    Output output = new Output();
                    output.success = true;
                    output.url = input.url;
                    output.title = result.title;
                    output.content = result.content;
                    output.content_type = result.contentType;
                    output.metadata = result.metadata;
                    output.warning = "页面内容较少，可能包含 JS 动态加载的内容。如需获取完整内容，请设置 render_js=true";
                    return ToolResult.success(output);
                }

                Output output = new Output();
                output.success = true;
                output.url = input.url;
                output.title = result.title;
                output.content = result.content;
                output.content_type = result.contentType;
                output.metadata = result.metadata;

                return ToolResult.success(output);

            } catch (Exception e) {
                logger.severe("获取网页失败: " + e.getMessage());
                return ToolResult.error("获取网页失败: " + e.getMessage());
            }
        });
    }

    // ==================== HTTP 直接抓取（OkHttp） ====================

    /**
     * 使用 OkHttp 发送 HTTP GET 请求获取页面内容。
     * OkHttp 自动处理 gzip 解压缩、字符编码和重定向。
     */
    private FetchResult fetchWithHttp(String urlString, int maxLength, boolean extractArticle) throws Exception {
        Request request = new Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP 请求失败，状态码: " + response.code());
            }

            FetchResult result = new FetchResult();

            // 内容类型
            result.contentType = response.header("Content-Type");

            // 读取响应体（OkHttp 自动处理 gzip）
            ResponseBody body = response.body();
            if (body == null) {
                throw new RuntimeException("响应体为空");
            }

            // 检测编码：优先使用 Content-Type 中的 charset，否则用 BOM 或默认 UTF-8
            Charset charset = StandardCharsets.UTF_8;
            String contentType = result.contentType;
            if (contentType != null) {
                String lower = contentType.toLowerCase();
                int cs = lower.indexOf("charset=");
                if (cs >= 0) {
                    String enc = contentType.substring(cs + 8).trim();
                    int end = enc.indexOf(';');
                    if (end > 0) enc = enc.substring(0, end);
                    enc = enc.replace("\"", "").trim();
                    try {
                        charset = Charset.forName(enc);
                    } catch (Exception ignored) {
                    }
                }
            }

            String html;
            try {
                // 按检测到的编码读取字节
                byte[] bytes = body.bytes();
                html = new String(bytes, charset);
            } catch (Exception e) {
                // 回退：使用 OkHttp 的自动编码
                html = body.string();
            }

            // 使用 HtmlParser 解析
            HtmlParser parser = new HtmlParser(html);
            result.title = parser.extractTitle();
            result.metadata = parser.extractMetadata();
            result.rawHtml = html;

            // 提取正文
            if (extractArticle) {
                result.content = contentExtractor.extractArticle(html);
            } else {
                result.content = parser.extractText();
            }

            // 如果提取的正文为空，回退到简单文本提取
            if (result.content == null || result.content.trim().isEmpty()) {
                result.content = parser.extractText();
            }

            // 限制长度
            if (result.content != null && result.content.length() > maxLength) {
                result.content = result.content.substring(0, maxLength)
                    + "\n\n[内容已截断，原始长度: " + result.content.length() + " 字符]";
            }

            return result;
        }
    }

    // ==================== JS 渲染抓取 ====================

    /**
     * 使用 Playwright 渲染页面后再提取内容。
     */
    private ToolResult<Output> fetchWithRender(String urlString, int maxLength, boolean extractArticle) {
        PlaywrightManager pm = PlaywrightManager.getInstance();

        if (!pm.isAvailable()) {
            return ToolResult.error(
                "浏览器 JS 渲染引擎不可用，无法使用 render_js=true 模式。\n" +
                "请确保已安装 Google Chrome 或 Chromium 浏览器，\n" +
                "或通过 CHROME_PATH 环境变量指定 Chrome 可执行文件路径。\n" +
                "当前可使用默认的 HTTP 模式（不设置 render_js）。");
        }

        try {
            // 渲染页面（等待 5 秒确保 JS 加载完成）
            PlaywrightManager.RenderResult renderResult = pm.render(urlString, 5000);

            if (!renderResult.success) {
                return ToolResult.error("JS 渲染失败: " + renderResult.error);
            }

            String html = renderResult.html;
            if (html == null || html.isEmpty()) {
                return ToolResult.error("渲染结果为空");
            }

            // 使用 HtmlParser 和 ContentExtractor 提取内容
            HtmlParser parser = new HtmlParser(html);
            String title = renderResult.title != null ? renderResult.title : parser.extractTitle();
            Map<String, String> metadata = renderResult.metadata != null
                ? renderResult.metadata : parser.extractMetadata();

            String content;
            if (extractArticle) {
                content = contentExtractor.extractArticle(html);
            } else {
                content = parser.extractText();
            }

            if (content == null || content.trim().isEmpty()) {
                content = parser.extractText();
            }

            // 限制长度
            if (content != null && content.length() > maxLength) {
                content = content.substring(0, maxLength)
                    + "\n\n[内容已截断，原始长度: " + content.length() + " 字符]";
            }

            Output output = new Output();
            output.success = true;
            output.url = urlString;
            output.title = title;
            output.content = content;
            output.content_type = "text/html (JS rendered)";
            output.metadata = metadata;

            return ToolResult.success(output);

        } catch (Exception e) {
            logger.severe("JS 渲染抓取失败: " + e.getMessage());
            return ToolResult.error("JS 渲染抓取失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证 URL 格式。
     */
    private boolean isValidUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * 检测页面是否为 JS SPA 外壳（内容极少，需要 JS 渲染）。
     */
    private boolean isLikelyJsShell(FetchResult result) {
        if (result == null || result.content == null || result.rawHtml == null) {
            return false;
        }
        String content = result.content.trim();
        String html = result.rawHtml;

        // 如果提取的正文很短，且 HTML 较长，很可能是 JS 外壳
        if (content.length() < 200 && html.length() > 5000) {
            double ratio = (double) content.length() / html.length();
            return ratio < JS_SHELL_THRESHOLD;
        }
        return false;
    }

    @Override
    public ToolValidationResult validate(Input input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();

        if (input == null || input.url == null || input.url.trim().isEmpty()) {
            builder.addError("url 是必需的");
        } else if (!isValidUrl(input.url)) {
            builder.addError("URL 必须以 http:// 或 https:// 开头");
        }

        if (input.max_length != null && input.max_length < 1) {
            builder.addError("max_length 必须大于 0");
        }

        return builder.build();
    }

    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }

    @Override
    public boolean isConcurrencySafe(Input input) {
        return true;
    }

    // ==================== 内部类型 ====================

    /**
     * HTTP 抓取结果（内部使用）。
     */
    private static class FetchResult {
        String title;
        String content;
        String contentType;
        String rawHtml;
        Map<String, String> metadata;
    }

    /**
     * 工具输入参数。
     */
    public static class Input {
        public String url;
        public Integer max_length;
        public Boolean extract_article;
        public Boolean render_js;

        public Input() {}

        public Input(String url) {
            this.url = url;
        }
    }

    /**
     * 工具输出结果。
     */
    public static class Output {
        public boolean success;
        public String url;
        public String title;
        public String content;
        public String content_type;
        public Map<String, String> metadata;
        public String warning; // 提示信息（如建议使用 JS 渲染）

        public Output() {}
    }

    /**
     * 进度类型。
     */
    public static class Progress {
        private final String url;
        private final int bytesDownloaded;
        private final int totalBytes;

        public Progress(String url, int bytesDownloaded, int totalBytes) {
            this.url = url;
            this.bytesDownloaded = bytesDownloaded;
            this.totalBytes = totalBytes;
        }

        public String getUrl() { return url; }
        public int getBytesDownloaded() { return bytesDownloaded; }
        public int getTotalBytes() { return totalBytes; }
        public double getProgress() { return totalBytes > 0 ? (double) bytesDownloaded / totalBytes : 0; }
    }
}
