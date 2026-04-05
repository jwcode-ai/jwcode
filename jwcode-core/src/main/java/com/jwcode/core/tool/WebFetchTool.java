package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * WebFetch 工具 - 获取网页内容（重构后）
 * 
 * 支持抓取指定 URL 的网页文本内容
 * 自动提取正文、标题和元数据
 */
public class WebFetchTool implements Tool<WebFetchTool.Input, WebFetchTool.Output, WebFetchTool.Progress> {
    
    private static final Logger logger = Logger.getLogger(WebFetchTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // 最大内容长度限制
    private static final int MAX_CONTENT_LENGTH = 100000; // 100KB
    
    @Override
    public String getName() {
        return "WebFetch";
    }
    
    @Override
    public String getDescription() {
        return "获取网页内容。用于抓取指定 URL 的网页文本内容。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 WebFetch 工具获取指定网页的内容。
               
               参数:
               - url: 要获取的网页 URL（必需）
               - max_length: 最大内容长度（可选，默认 50000 字符）
               
               示例:
               - {"url": "https://docs.oracle.com/javase/21/docs/api/"}
               - {"url": "https://github.com/spring-projects/spring-boot/releases", "max_length": 10000}
               
               注意:
               - URL 必须是完整的，包含协议（http:// 或 https://）
               - 某些网站可能会阻止抓取
               - 返回的是网页的文本内容，已去除 HTML 标签
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
                        "max_length": {"type": "integer", "description": "最大内容长度", "default": 50000}
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
                
                // 验证 URL 格式
                if (!isValidUrl(input.url)) {
                    return ToolResult.error("无效的 URL 格式: " + input.url);
                }
                
                int maxLength = input.max_length != null ? input.max_length : 50000;
                if (maxLength > MAX_CONTENT_LENGTH) {
                    maxLength = MAX_CONTENT_LENGTH;
                }
                
                // 获取网页内容
                FetchResult result = fetchWebPage(input.url, maxLength);
                
                Output output = new Output();
                output.success = true;
                output.url = input.url;
                output.title = result.title;
                output.content = result.content;
                output.content_type = result.contentType;
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                logger.severe("获取网页失败: " + e.getMessage());
                return ToolResult.error("获取网页失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 验证 URL 格式
     */
    private boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
    
    /**
     * 获取网页内容
     */
    private FetchResult fetchWebPage(String urlString, int maxLength) throws Exception {
        FetchResult result = new FetchResult();
        
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("HTTP 请求失败，状态码: " + responseCode);
            }
            
            // 获取内容类型
            result.contentType = conn.getContentType();
            
            // 读取内容
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                    if (content.length() > maxLength * 2) {
                        break; // 防止内容过大
                    }
                }
            }
            
            String html = content.toString();
            
            // 提取标题
            result.title = extractTitle(html);
            
            // 提取正文内容
            result.content = extractTextContent(html, maxLength);
            
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        
        return result;
    }
    
    /**
     * 提取标题
     */
    private String extractTitle(String html) {
        try {
            // 尝试提取 <title> 标签内容
            int titleStart = html.indexOf("<title>");
            int titleEnd = html.indexOf("</title>");
            if (titleStart != -1 && titleEnd != -1 && titleEnd > titleStart) {
                return html.substring(titleStart + 7, titleEnd).trim();
            }
            
            // 尝试提取 h1 标签
            int h1Start = html.toLowerCase().indexOf("<h1");
            if (h1Start != -1) {
                int h1Close = html.indexOf(">", h1Start);
                int h1End = html.toLowerCase().indexOf("</h1>", h1Close);
                if (h1Close != -1 && h1End != -1) {
                    String h1 = html.substring(h1Close + 1, h1End);
                    return h1.replaceAll("<.*?>", "").trim();
                }
            }
            
        } catch (Exception e) {
            logger.fine("提取标题失败: " + e.getMessage());
        }
        return "";
    }
    
    /**
     * 提取正文内容（去除 HTML 标签）
     */
    private String extractTextContent(String html, int maxLength) {
        try {
            // 移除 script 和 style 标签及其内容
            String text = html;
            text = text.replaceAll("(?is)<script.*?>.*?</script>", "");
            text = text.replaceAll("(?is)<style.*?>.*?</style>", "");
            text = text.replaceAll("(?is)<nav.*?>.*?</nav>", "");
            text = text.replaceAll("(?is)<header.*?>.*?</header>", "");
            text = text.replaceAll("(?is)<footer.*?>.*?</footer>", "");
            
            // 移除 HTML 标签
            text = text.replaceAll("<.*?>", " ");
            
            // 解码 HTML 实体
            text = text.replace("&nbsp;", " ");
            text = text.replace("&lt;", "<");
            text = text.replace("&gt;", ">");
            text = text.replace("&amp;", "&");
            text = text.replace("&quot;", "\"");
            text = text.replace("&#39;", "'");
            
            // 规范化空白
            text = text.replaceAll("\\s+", " ");
            
            // 去除首尾空白
            text = text.trim();
            
            // 限制长度
            if (text.length() > maxLength) {
                text = text.substring(0, maxLength) + "\n\n[内容已截断，原始长度: " + text.length() + " 字符]";
            }
            
            return text;
            
        } catch (Exception e) {
            logger.warning("提取正文失败: " + e.getMessage());
            return "[无法提取正文内容]";
        }
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
    
    /**
     * 抓取结果
     */
    private static class FetchResult {
        String title;
        String content;
        String contentType;
    }
    
    /**
     * 输入类型
     */
    public static class Input {
        public String url;
        public Integer max_length;
        
        public Input() {}
        
        public Input(String url) {
            this.url = url;
        }
    }
    
    /**
     * 输出类型
     */
    public static class Output {
        public boolean success;
        public String url;
        public String title;
        public String content;
        public String content_type;
        
        public Output() {}
    }
    
    /**
     * 进度类型
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
