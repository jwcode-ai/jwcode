package com.jwcode.core.tool.browser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * BrowserTool — 浏览器自动化工具。
 *
 * <p>使用 Playwright 引擎提供真实的浏览器操作能力：</p>
 * <ul>
 *   <li>navigate — 导航到 URL</li>
 *   <li>snapshot — 获取页面无障碍树</li>
 *   <li>click — 点击元素</li>
 *   <li>type — 输入文本</li>
 *   <li>scroll — 滚动页面</li>
 *   <li>wait — 等待</li>
 *   <li>screenshot — 截图</li>
 * </ul>
 *
 * <p>当 Playwright 不可用时，所有操作返回清晰的安装指引。</p>
 */
public class BrowserTool implements Tool<String, String, Void> {

    private static final Logger logger = Logger.getLogger(BrowserTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int NAVIGATION_TIMEOUT_MS = 30_000;
    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0", "[::1]"
    );
    private static final String[] BLOCKED_PREFIXES = {
        "10.", "172.16.", "172.17.", "172.18.", "172.19.",
        "172.20.", "172.21.", "172.22.", "172.23.", "172.24.",
        "172.25.", "172.26.", "172.27.", "172.28.", "172.29.",
        "172.30.", "172.31.", "192.168."
    };

    // 每个会话维护一个 pageId（通过状态存储）
    private static final String STATE_KEY_PAGE_ID = "browser_page_id";

    private final PlaywrightManager playwright;

    public BrowserTool() {
        this.playwright = PlaywrightManager.getInstance();
    }

    @Override
    public String getName() { return "Browser"; }

    @Override
    public String getDescription() {
        return "浏览器自动化工具：导航(navigate)、页面快照(snapshot)、点击(click)、输入(type)、滚动(scroll)、等待(wait)、截图(screenshot)。" +
               "使用 Playwright 驱动真实 Chromium 浏览器。本地地址(localhost/127.0.0.1/内网IP)被安全拦截。";
    }

    @Override
    public String getPrompt() {
        return """
            Browser — 浏览器自动化工具

            支持的 action:
            - navigate url=<URL>       导航到指定 URL
            - snapshot url=<URL>       获取页面无障碍快照
            - click selector=<CSS>     点击元素
            - type selector=<CSS> text=<文本>  在输入框中输入文本
            - scroll x=<水平> y=<垂直>  滚动页面
            - wait ms=<毫秒>            等待
            - screenshot               截图（base64 PNG）

            使用示例:
            navigate url=https://example.com
            snapshot url=https://example.com
            click selector="#submit-btn"
            type selector="#search" text="hello"
            scroll y=500
            wait ms=2000
            screenshot

            注意:
            - navigate/snapshot 在当前浏览器标签页中执行
            - 后续 click/type/scroll/screenshot 作用于上一次 navigate 的页面
            - 需要 Playwright + Chromium 浏览器依赖
            """;
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "command": {
                            "type": "string",
                            "description": "浏览器操作指令，格式: <action> [param=value ...]"
                        }
                    },
                    "required": ["command"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String parseInput(JsonNode json) {
        if (json == null) return "";
        if (json.has("command")) {
            return json.get("command").asText();
        }
        return json.asText();
    }

    @Override
    public boolean isReadOnly(String input) { return true; }

    @Override
    public ToolCategory getCategory() { return ToolCategory.COMMUNICATION; }

    @Override
    public TypeReference<String> getInputType() {
        return new TypeReference<>() {};
    }

    @Override
    public TypeReference<String> getOutputType() {
        return new TypeReference<>() {};
    }

    @Override
    public CompletableFuture<ToolResult<String>> call(
            String input, ToolExecutionContext context, Consumer<ToolProgress<Void>> onProgress) {
        CompletableFuture<ToolResult<String>> future = new CompletableFuture<>();
        try {
            if (input == null || input.trim().isEmpty()) {
                future.complete(ToolResult.error("请输入浏览器操作指令"));
                return future;
            }

            // 解析 action + 参数
            String action = extractAction(input);
            String url = extractParam(input, "url");
            String selector = extractParam(input, "selector");
            String text = extractParam(input, "text");
            int waitMs = parseIntOrDefault(extractParam(input, "ms"), 1000);
            int scrollX = parseIntOrDefault(extractParam(input, "x"), 0);
            int scrollY = parseIntOrDefault(extractParam(input, "y"), 500);

            // 检查 Playwright 可用性（navigate 和 snapshot 除外，它们有自己的降级）
            boolean needsPlaywright = !("navigate".equals(action) || "snapshot".equals(action) || "wait".equals(action));
            if (needsPlaywright && !playwright.isAvailable()) {
                future.complete(ToolResult.error(
                    "Playwright 浏览器引擎不可用。click/type/scroll/screenshot 需要 Playwright。\n" +
                    "安装指引: https://playwright.dev/java/docs/intro\n" +
                    "或使用 navigate/snapshot/wait 进行基本操作。"));
                return future;
            }

            ToolResult<String> result = switch (action) {
                case "navigate" -> handleNavigate(url, context);
                case "snapshot" -> handleSnapshot(url, context);
                case "click" -> handleClick(selector, context);
                case "type" -> handleType(selector, text, context);
                case "scroll" -> handleScroll(scrollX, scrollY, context);
                case "wait" -> handleWait(waitMs);
                case "screenshot" -> handleScreenshot(context);
                default -> ToolResult.error("不支持的浏览器操作: " + action +
                    "。支持: navigate, snapshot, click, type, scroll, wait, screenshot");
            };
            future.complete(result);
        } catch (Exception e) {
            future.complete(ToolResult.error("浏览器工具错误: " + e.getMessage()));
        }
        return future;
    }

    // ==================== 操作实现 ====================

    private ToolResult<String> handleNavigate(String url, ToolExecutionContext context) {
        if (url == null || url.isEmpty())
            return ToolResult.error("navigate 需要 url 参数");
        if (isBlockedUrl(url))
            return ToolResult.error("安全拦截: 不允许访问本地/内网地址: " + url);

        // 如果 Playwright 可用，使用浏览器渲染
        if (playwright.isAvailable()) {
            try {
                PlaywrightManager.NavigationResult navResult = playwright.navigate(url);
                if (!navResult.success) {
                    return ToolResult.error("导航失败: " + navResult.error);
                }

                // 保存 pageId 到上下文
                context.putState(STATE_KEY_PAGE_ID, navResult.pageId);

                // 构建页面摘要
                StringBuilder sb = new StringBuilder();
                sb.append("Navigated to: ").append(url).append("\n");
                sb.append("Title: ").append(navResult.title).append("\n\n");
                sb.append(buildPageSummaryFromHtml(navResult.pageContent, url));
                return ToolResult.success(sb.toString());
            } catch (Exception e) {
                return ToolResult.error("导航失败: " + e.getMessage());
            }
        }

        // Playwright 不可用：回退到简单的 HTTP 请求
        return httpFallbackNavigate(url);
    }

    private ToolResult<String> handleSnapshot(String url, ToolExecutionContext context) {
        if (url == null || url.isEmpty())
            return ToolResult.error("snapshot 需要 url 参数");
        if (isBlockedUrl(url))
            return ToolResult.error("安全拦截: 不允许访问本地/内网地址: " + url);

        if (playwright.isAvailable()) {
            try {
                PlaywrightManager.NavigationResult navResult = playwright.navigate(url);
                if (!navResult.success) {
                    return ToolResult.error("snapshot 失败: " + navResult.error);
                }
                context.putState(STATE_KEY_PAGE_ID, navResult.pageId);

                PlaywrightManager.SnapshotResult snap = playwright.snapshot(navResult.pageId);
                if (!snap.success) {
                    return ToolResult.error("snapshot 失败: " + snap.error);
                }
                return ToolResult.success(snap.snapshot);
            } catch (Exception e) {
                return ToolResult.error("snapshot 失败: " + e.getMessage());
            }
        }

        // Playwright 不可用：HTTP 回退
        return httpFallbackSnapshot(url);
    }

    private ToolResult<String> handleClick(String selector, ToolExecutionContext context) {
        if (selector == null || selector.isEmpty())
            return ToolResult.error("click 需要 selector 参数");

        Integer pageId = context.getState(STATE_KEY_PAGE_ID);
        if (pageId == null) {
            return ToolResult.error("没有活动的页面。先使用 navigate 导航到目标页面。");
        }

        PlaywrightManager.ActionResult result = playwright.click(pageId, selector);
        return result.success
            ? ToolResult.success(result.message)
            : ToolResult.error("点击失败: " + result.error);
    }

    private ToolResult<String> handleType(String selector, String text, ToolExecutionContext context) {
        if (selector == null || selector.isEmpty())
            return ToolResult.error("type 需要 selector 参数");
        if (text == null || text.isEmpty())
            return ToolResult.error("type 需要 text 参数");

        Integer pageId = context.getState(STATE_KEY_PAGE_ID);
        if (pageId == null) {
            return ToolResult.error("没有活动的页面。先使用 navigate 导航到目标页面。");
        }

        PlaywrightManager.ActionResult result = playwright.type(pageId, selector, text);
        return result.success
            ? ToolResult.success(result.message)
            : ToolResult.error("输入失败: " + result.error);
    }

    private ToolResult<String> handleScroll(int x, int y, ToolExecutionContext context) {
        Integer pageId = context.getState(STATE_KEY_PAGE_ID);
        if (pageId == null) {
            return ToolResult.error("没有活动的页面。先使用 navigate 导航到目标页面。");
        }

        PlaywrightManager.ActionResult result = playwright.scroll(pageId, x, y);
        return result.success
            ? ToolResult.success(result.message)
            : ToolResult.error("滚动失败: " + result.error);
    }

    private ToolResult<String> handleWait(int ms) {
        PlaywrightManager.ActionResult result = playwright.waitFor(ms);
        return result.success
            ? ToolResult.success(result.message)
            : ToolResult.error("等待失败: " + result.error);
    }

    private ToolResult<String> handleScreenshot(ToolExecutionContext context) {
        Integer pageId = context.getState(STATE_KEY_PAGE_ID);
        if (pageId == null) {
            return ToolResult.error("没有活动的页面。先使用 navigate 导航到目标页面。");
        }

        PlaywrightManager.ScreenshotResult result = playwright.screenshot(pageId);
        if (!result.success) {
            return ToolResult.error("截图失败: " + result.error);
        }
        return ToolResult.success(
            "Screenshot captured (base64 PNG, " + result.base64Data.length() + " chars)\n" +
            "data:" + result.mimeType + ";base64," + result.base64Data);
    }

    // ==================== HTTP 回退实现 ====================

    private ToolResult<String> httpFallbackNavigate(String url) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofMillis(NAVIGATION_TIMEOUT_MS))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET().build();
            java.net.http.HttpResponse<String> resp = client.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofString());

            String body = resp.body();
            String title = extractTitleSimple(body);

            StringBuilder sb = new StringBuilder();
            sb.append("Navigated to: ").append(url).append("\n");
            sb.append("Status: ").append(resp.statusCode()).append("\n");
            sb.append("Title: ").append(title).append("\n\n");
            sb.append("[INFO] Playwright 不可用，使用 HTTP 模式。页面可能缺少 JS 渲染的内容。\n");
            sb.append("如需完整浏览器功能，请安装 Playwright: https://playwright.dev/java/docs/intro\n\n");
            sb.append(buildPageSummaryFromHtml(body, url));

            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("导航失败: " + e.getMessage() +
                "\n提示: 安装 Playwright 可获得更好的浏览器兼容性: https://playwright.dev/java/docs/intro");
        }
    }

    private ToolResult<String> httpFallbackSnapshot(String url) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofMillis(NAVIGATION_TIMEOUT_MS))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET().build();
            java.net.http.HttpResponse<String> resp = client.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofString());

            String body = resp.body();
            String title = extractTitleSimple(body);

            StringBuilder sb = new StringBuilder();
            sb.append("=== Accessibility Snapshot (HTTP mode) ===\n");
            sb.append("URL: ").append(url).append("\n");
            sb.append("Title: ").append(title).append("\n");
            sb.append("[INFO] 安装 Playwright 可获得完整的交互式快照体验\n\n");

            // 提取标题
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<(h[1-6])[^>]*>([^<]*)</\\1>", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
            sb.append("Page structure:\n");
            while (m.find()) {
                String level = m.group(1);
                String text = m.group(2).trim();
                if (!text.isEmpty()) {
                    sb.append("  ").append(level).append(": ").append(text).append("\n");
                }
            }

            // 提取链接
            m = java.util.regex.Pattern
                .compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>([^<]*)</a>", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
            sb.append("\nLinks:\n");
            int linkCount = 0;
            while (m.find() && linkCount < 30) {
                String href = m.group(1).trim();
                String label = m.group(2).trim().replaceAll("\\s+", " ");
                if (!href.startsWith("javascript:") && !href.startsWith("#")) {
                    linkCount++;
                    sb.append("  [").append(linkCount).append("] ");
                    if (!label.isEmpty()) sb.append("\"").append(label).append("\" -> ");
                    sb.append(href).append("\n");
                }
            }

            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("snapshot 失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isBlockedUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            if (BLOCKED_HOSTS.contains(host.toLowerCase())) return true;
            for (String prefix : BLOCKED_PREFIXES) {
                if (host.startsWith(prefix)) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private String extractTitleSimple(String html) {
        if (html == null) return "(no title)";
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>", start);
        if (start >= 0 && end > start) return html.substring(start + 7, end).trim();
        start = html.indexOf("og:title\" content=\"");
        if (start >= 0) {
            start += "og:title\" content=\"".length();
            end = html.indexOf("\"", start);
            if (end > start) return html.substring(start, end);
        }
        return "(no title)";
    }

    private String buildPageSummaryFromHtml(String html, String url) {
        if (html == null || html.isEmpty()) return "(empty page)";
        StringBuilder sb = new StringBuilder();
        sb.append("=== Page Summary ===\n\n");

        // 提取标题
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("<(h[1-6])[^>]*>([^<]*)</\\1>", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        sb.append("Headings:\n");
        int headingCount = 0;
        while (m.find() && headingCount < 20) {
            String text = m.group(2).trim();
            if (!text.isEmpty()) {
                headingCount++;
                sb.append("  ").append(m.group(1)).append(": ").append(text).append("\n");
            }
        }
        sb.append("\n");

        // 提取链接
        m = java.util.regex.Pattern
            .compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>([^<]*)</a>", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        sb.append("Links (").append(countMatches(html, "<a ")).append(" total, showing up to 30):\n");
        int linkCount = 0;
        while (m.find() && linkCount < 30) {
            String href = m.group(1).trim();
            String label = m.group(2).trim().replaceAll("\\s+", " ");
            if (!href.startsWith("javascript:") && !href.startsWith("#")) {
                linkCount++;
                sb.append("  [").append(linkCount).append("] ");
                if (!label.isEmpty()) sb.append("\"").append(label).append("\" -> ");
                sb.append(href).append("\n");
            }
        }
        sb.append("\n");

        // 文本预览
        String text = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                          .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                          .replaceAll("<[^>]+>", " ")
                          .replaceAll("\\s+", " ")
                          .trim();
        if (!text.isEmpty()) {
            String preview = text.length() > 2000 ? text.substring(0, 2000) + "..." : text;
            sb.append("Text preview:\n").append(preview).append("\n");
        }

        return sb.toString();
    }

    private int countMatches(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private String extractAction(String input) {
        if (input == null) return "snapshot";
        String trimmed = input.trim();
        int spaceIdx = trimmed.indexOf(' ');
        return spaceIdx > 0 ? trimmed.substring(0, spaceIdx).toLowerCase() : trimmed.toLowerCase();
    }

    private String extractParam(String input, String param) {
        if (input == null) return null;
        String search = param + "=";
        int idx = input.indexOf(search);
        if (idx < 0) { search = param + ":"; idx = input.indexOf(search); }
        if (idx < 0) {
            // 如果是 navigate/snapshot，尝试将第一个非 action 的单词作为 url
            String action = extractAction(input);
            if ("url".equals(param) && ("navigate".equals(action) || "snapshot".equals(action))) {
                int spaceIdx = input.indexOf(' ');
                if (spaceIdx > 0) {
                    String afterAction = input.substring(spaceIdx + 1).trim();
                    int endIdx = afterAction.indexOf(' ');
                    return endIdx > 0 ? afterAction.substring(0, endIdx) : afterAction;
                }
            }
            return null;
        }
        idx += search.length();
        int endIdx = input.indexOf(' ', idx);
        String value = endIdx > 0 ? input.substring(idx, endIdx) : input.substring(idx);
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
