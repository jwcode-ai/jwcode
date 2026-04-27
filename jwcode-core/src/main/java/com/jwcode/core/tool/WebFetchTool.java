package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.search.ContentExtractor;
import com.jwcode.core.search.HtmlParser;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebFetch 工具 - 获取网页内容（增强版）
 * 
 * 支持抓取指定 URL 的网页文本内容
 * 使用 HtmlParser 和 ContentExtractor 智能提取正文
 * 支持 User-Agent 设置、超时配置、跟随重定向
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class WebFetchTool implements Tool<WebFetchTool.Input, WebFetchTool.Output, WebFetchTool.Progress> {
    
    private static final Logger logger = Logger.getLogger(WebFetchTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // 最大内容长度限制
    private static final int MAX_CONTENT_LENGTH = 100000; // 100KB
    
    // 默认配置
    private static final String DEFAULT_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int DEFAULT_CONNECT_TIMEOUT = 15000;
    private static final int DEFAULT_READ_TIMEOUT = 15000;
    
    // HTML 解析器和内容提取器
    private final HtmlParser htmlParser;
    private final ContentExtractor contentExtractor;
    
    // 配置
    private String userAgent = DEFAULT_USER_AGENT;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private int readTimeout = DEFAULT_READ_TIMEOUT;
    private boolean followRedirects = true;
    
    public WebFetchTool() {
        this.htmlParser = null; // 延迟初始化
        this.contentExtractor = new ContentExtractor();
    }
    
    @Override
    public String getName() {
        return "WebFetch";
    }
    
    @Override
    public String getDescription() {
        return "获取网页内容。用于抓取指定 URL 的网页文本内容，自动提取正文、标题和元数据。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 WebFetch 工具获取指定网页的内容。
               
               参数:
               - url: 要获取的网页 URL（必需）
               - max_length: 最大内容长度（可选，默认 50000 字符）
               - extract_article: 是否智能提取文章正文（可选，默认 true）
               
               示例:
               - {"url": "https://docs.oracle.com/javase/21/docs/api/"}
               - {"url": "https://github.com/spring-projects/spring-boot/releases", "max_length": 10000}
               - {"url": "https://example.com/article", "extract_article": true}
               
               注意:
               - URL 必须是完整的，包含协议（http:// 或 https://）
               - 某些网站可能会阻止抓取
               - 返回的是网页的文本内容，已去除 HTML 标签和广告
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
                        "extract_article": {"type": "boolean", "description": "是否智能提取文章正文", "default": true}
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
                
                boolean extractArticle = input.extract_article != null ? input.extract_article : true;
                
                // 获取网页内容
                FetchResult result = fetchWebPage(input.url, maxLength, extractArticle);
                
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
    
    /**
     * 验证 URL 格式
     */
    private boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
    
    /**
     * 从 Content-Type 头检测字符编码
     */
    private Charset detectCharset(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        
        // 查找 charset=xxx
        int charsetIndex = contentType.toLowerCase().indexOf("charset=");
        if (charsetIndex >= 0) {
            String charsetPart = contentType.substring(charsetIndex + 8).trim();
            // 去除可能的引号和分号
            int endIndex = charsetPart.indexOf(';');
            if (endIndex > 0) {
                charsetPart = charsetPart.substring(0, endIndex);
            }
            charsetPart = charsetPart.replace('"', ' ').replace('\'', ' ').trim();
            
            try {
                return Charset.forName(charsetPart);
            } catch (Exception e) {
                logger.warning("不支持的字符编码: " + charsetPart + ", 使用 UTF-8");
            }
        }
        
        return StandardCharsets.UTF_8;
    }
    
    /**
     * 获取网页内容
     */
    private FetchResult fetchWebPage(String urlString, int maxLength, boolean extractArticle) throws Exception {
        FetchResult result = new FetchResult();
        
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            setupConnection(conn);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("HTTP 请求失败，状态码: " + responseCode);
            }
            
            // 获取内容类型
            result.contentType = conn.getContentType();
            
            // 动态检测编码
            Charset charset = detectCharset(result.contentType);
            
            // 读取内容
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                    if (content.length() > maxLength * 2) {
                        break; // 防止内容过大
                    }
                }
            }
            
            String html = content.toString();
            
            // 使用 HtmlParser 解析
            HtmlParser parser = new HtmlParser(html);
            result.title = parser.extractTitle();
            result.metadata = parser.extractMetadata();
            
            // 提取正文内容
            if (extractArticle) {
                // 使用 ContentExtractor 智能提取
                result.content = contentExtractor.extractArticle(html);
            } else {
                // 简单文本提取
                result.content = parser.extractText();
            }
            
            // 限制长度
            if (result.content.length() > maxLength) {
                result.content = result.content.substring(0, maxLength) + 
                    "\n\n[内容已截断，原始长度: " + result.content.length() + " 字符]";
            }
            
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        
        return result;
    }
    
    /**
     * 配置 HTTP 连接
     */
    private void setupConnection(HttpURLConnection conn) {
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            conn.setRequestProperty("Connection", "keep-alive");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setInstanceFollowRedirects(followRedirects);
        } catch (Exception e) {
            logger.warning("配置连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 设置 User-Agent
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent != null ? userAgent : DEFAULT_USER_AGENT;
    }
    
    /**
     * 设置超时
     */
    public void setTimeout(int timeoutMs) {
        this.connectTimeout = timeoutMs;
        this.readTimeout = timeoutMs;
    }
    
    /**
     * 设置是否跟随重定向
     */
    public void setFollowRedirects(boolean follow) {
        this.followRedirects = follow;
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
        java.util.Map<String, String> metadata;
    }
    
    /**
     * 输入类型
     */
    public static class Input {
        public String url;
        public Integer max_length;
        public Boolean extract_article;
        
        public Input() {}
        
        public Input(String url) {
            this.url = url;
        }
        
        public Input(String url, Integer max_length) {
            this.url = url;
            this.max_length = max_length;
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
        public java.util.Map<String, String> metadata;
        
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
