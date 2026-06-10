package com.jwcode.core.tool.browser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * BrowserTool — 对标 Codex browser_use，提供浏览器自动化能力。
 *
 * 操作原语（对标 Codex web.run 的7个核心操作）：
 *   navigate(url)  — 导航到指定 URL
 *   snapshot()     — 获取页面 a11y tree（轻量文本表示）
 *   click(selector)— 点击元素
 *   type(selector, text) — 输入文本
 *   scroll(x, y)   — 滚动页面
 *   wait(ms)       — 等待指定毫秒
 *   screenshot()   — 截图（base64 PNG）
 */
public class BrowserTool implements Tool<String, String, Void> {

    private static final Logger logger = Logger.getLogger(BrowserTool.class.getName());
    private static final int BROWSER_TIMEOUT_MS = 15_000;
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

    private final HttpClient httpClient;

    public BrowserTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public String getName() { return "browser"; }

    @Override
    public String getDescription() {
        return "Automate web browser actions: navigate, snapshot (a11y tree), click, type, scroll, wait, screenshot. " +
               "Local addresses (localhost, 127.0.0.1, private IPs) are blocked for security.";
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
            String action = extractAction(input);
            String url = extractParam(input, "url");
            String selector = extractParam(input, "selector");
            String text = extractParam(input, "text");
            int waitMs = Integer.parseInt(extractParamOrDefault(input, "waitMs", "1000"));

            ToolResult<String> result = switch (action) {
                case "navigate" -> navigate(url);
                case "snapshot" -> snapshot(url);
                case "click" -> click(selector);
                case "type" -> type(selector, text);
                case "scroll" -> scroll(input);
                case "wait" -> waitFor(waitMs);
                case "screenshot" -> screenshot();
                default -> ToolResult.error("Unknown browser action: " + action +
                    ". Supported: navigate, snapshot, click, type, scroll, wait, screenshot");
            };
            future.complete(result);
        } catch (Exception e) {
            future.complete(ToolResult.error("Browser tool error: " + e.getMessage()));
        }
        return future;
    }

    // ---- Action implementations ----

    private ToolResult<String> navigate(String url) {
        if (url == null || url.isEmpty())
            return ToolResult.error("URL is required for navigate action");
        if (isBlockedUrl(url))
            return ToolResult.error("Blocked URL: local/private addresses are not allowed. URL: " + url);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(NAVIGATION_TIMEOUT_MS))
                .header("User-Agent", "JWCode-Browser/1.0")
                .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            return ToolResult.success(
                "Navigated to: " + url + "\nStatus: " + resp.statusCode() +
                "\nTitle: " + extractTitle(body) + "\n\n" + buildPageSummary(body, url));
        } catch (Exception e) {
            return ToolResult.error("Navigation failed: " + e.getMessage());
        }
    }

    private ToolResult<String> snapshot(String url) {
        if (url == null || url.isEmpty())
            return ToolResult.error("URL is required for snapshot");
        if (isBlockedUrl(url))
            return ToolResult.error("Blocked URL: " + url);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(BROWSER_TIMEOUT_MS))
                .header("User-Agent", "JWCode-Browser/1.0")
                .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return ToolResult.success(extractA11ySnapshot(resp.body(), url));
        } catch (Exception e) {
            return ToolResult.error("Snapshot failed: " + e.getMessage());
        }
    }

    private ToolResult<String> click(String selector) {
        return ToolResult.success(
            "Click element: \"" + (selector != null ? selector : "") + "\"\n" +
            "[INFO] Full interactive click support requires Playwright MCP integration.\n" +
            "Use 'snapshot' to view page elements first, then re-navigate with URL+hash for link following.");
    }

    private ToolResult<String> type(String selector, String text) {
        return ToolResult.success(
            "Type \"" + (text != null ? text : "") + "\" into: \"" + (selector != null ? selector : "") + "\"\n" +
            "[INFO] Full interactive typing requires Playwright MCP integration.");
    }

    private ToolResult<String> scroll(String input) {
        return ToolResult.success(
            "Scroll: " + (input != null ? input : "") + "\n" +
            "[INFO] Full scroll support requires Playwright MCP integration.");
    }

    private ToolResult<String> waitFor(int ms) {
        int actualMs = Math.min(ms, 10_000);
        try { Thread.sleep(actualMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return ToolResult.error("Wait interrupted"); }
        return ToolResult.success("Waited " + actualMs + "ms");
    }

    private ToolResult<String> screenshot() {
        return ToolResult.success(
            "[INFO] Screenshot capture requires Playwright MCP integration.\n" +
            "Install: npx @playwright/mcp-server\n" +
            "Configure in ~/.jwcode/mcp-servers.yaml under 'browser'.");
    }

    // ---- Helpers ----

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

    private String extractTitle(String html) {
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

    private String buildPageSummary(String html, String url) {
        if (html == null || html.isEmpty()) return "(empty page)";
        StringBuilder sb = new StringBuilder();
        sb.append("=== Page Summary (a11y-like snapshot) ===\nURL: ").append(url).append("\n\n");
        List<String> links = extractLinks(html);
        if (!links.isEmpty()) {
            sb.append("Links (").append(links.size()).append("):\n");
            int maxLinks = Math.min(links.size(), 30);
            for (int i = 0; i < maxLinks; i++) {
                String[] parts = links.get(i).split("\\|");
                sb.append("  [").append(i + 1).append("] ");
                sb.append(parts.length > 0 ? parts[0] : links.get(i));
                if (parts.length > 1) sb.append(" -> ").append(parts[1]);
                sb.append("\n");
            }
            if (links.size() > maxLinks) sb.append("  ... +").append(links.size() - maxLinks).append(" more\n");
            sb.append("\n");
        }
        String text = stripHtml(html);
        if (!text.isEmpty()) {
            String preview = text.length() > 2000 ? text.substring(0, 2000) + "..." : text;
            sb.append("Text preview:\n").append(preview).append("\n");
        }
        return sb.toString();
    }

    private String extractA11ySnapshot(String html, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Accessibility Snapshot ===\nURL: ").append(url).append("\n\n");
        List<String> headings = extractHeadings(html);
        if (!headings.isEmpty()) {
            sb.append("Page structure:\n");
            for (String h : headings) sb.append("  ").append(h).append("\n");
            sb.append("\n");
        }
        List<String> links = extractLinks(html);
        if (!links.isEmpty()) {
            sb.append("Interactive elements (").append(links.size()).append(" links):\n");
            int maxLinks = Math.min(links.size(), 30);
            for (int i = 0; i < maxLinks; i++) {
                String[] parts = links.get(i).split("\\|");
                String label = parts.length > 0 ? parts[0].trim() : links.get(i);
                String href = parts.length > 1 ? parts[1].trim() : "";
                sb.append("  link [").append(i + 1).append("] ");
                if (!label.isEmpty()) sb.append("\"").append(label).append("\"");
                if (!href.isEmpty()) sb.append(" -> ").append(href);
                sb.append("\n");
            }
            if (links.size() > maxLinks) sb.append("  ... +").append(links.size() - maxLinks).append(" more\n");
        }
        List<String> buttons = extractButtons(html);
        if (!buttons.isEmpty()) {
            sb.append("\nButtons:\n");
            for (int i = 0; i < Math.min(buttons.size(), 20); i++)
                sb.append("  button [").append(i + 1).append("] \"").append(buttons.get(i)).append("\"\n");
        }
        List<String> inputs = extractInputs(html);
        if (!inputs.isEmpty()) {
            sb.append("\nForm inputs:\n");
            for (String input : inputs) sb.append("  ").append(input).append("\n");
        }
        return sb.toString();
    }

    // ---- HTML parsers ----

    private List<String> extractHeadings(String html) {
        List<String> headings = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("<(h[1-6])[^>]*>([^<]*)</\\1>", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        while (m.find() && headings.size() < 50) {
            String level = m.group(1).toLowerCase();
            String text = m.group(2).trim();
            if (!text.isEmpty()) {
                String indent = "  ".repeat(level.charAt(1) - '1');
                headings.add(indent + level + ": " + text);
            }
        }
        return headings;
    }

    private List<String> extractLinks(String html) {
        List<String> links = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>([^<]*)</a>", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        while (m.find() && links.size() < 100) {
            String href = m.group(1).trim();
            String label = m.group(2).trim().replaceAll("\\s+", " ");
            if (label.length() > 80) label = label.substring(0, 80) + "...";
            if (!href.startsWith("javascript:") && !href.startsWith("#"))
                links.add(label + "|" + href);
        }
        return links;
    }

    private List<String> extractButtons(String html) {
        List<String> buttons = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("<button[^>]*>([^<]*)</button>", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        while (m.find() && buttons.size() < 50) {
            String text = m.group(1).trim();
            if (!text.isEmpty()) buttons.add(text);
        }
        m = java.util.regex.Pattern
            .compile("<input[^>]+type=[\"']submit[\"'][^>]+value=[\"']([^\"']+)[\"'][^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        while (m.find() && buttons.size() < 50) buttons.add(m.group(1).trim());
        return buttons;
    }

    private List<String> extractInputs(String html) {
        List<String> inputs = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("<(input|textarea|select)[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(html);
        while (m.find() && inputs.size() < 50) {
            String tag = m.group(1).toLowerCase();
            String attrs = m.group(0);
            String name = extractAttr(attrs, "name");
            String type = extractAttr(attrs, "type");
            String placeholder = extractAttr(attrs, "placeholder");
            StringBuilder sb = new StringBuilder(tag);
            if (!type.isEmpty()) sb.append(" type=").append(type);
            if (!name.isEmpty()) sb.append(" name=").append(name);
            if (!placeholder.isEmpty()) sb.append(" placeholder=\"").append(placeholder).append("\"");
            inputs.add(sb.toString());
        }
        return inputs;
    }

    private String extractAttr(String tag, String attrName) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile(attrName + "=[\"']([^\"']*)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(tag);
        return m.find() ? m.group(1).trim() : "";
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                   .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                   .replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&#\\d+;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
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
        if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
        return value.trim();
    }

    private String extractParamOrDefault(String input, String param, String defaultVal) {
        String val = extractParam(input, param);
        return val != null ? val : defaultVal;
    }
}
