package com.jwcode.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.model.Message;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * ApiClient - API 客户端（重构后）
 * 
 * 对标 JavaScript 项目的 API 客户端
 * 使用 Jackson 进行 JSON 序列化/反序列化
 * 支持流式响应和错误处理
 */
public class ApiClient {
    
    private static final Logger logger = Logger.getLogger(ApiClient.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private HttpClient httpClient;
    private ExecutorService executorService;
    private String baseUrl;
    private String apiKey;
    private Duration timeout;
    private int maxRetries;
    
    public ApiClient() {
        this(loadConfigFromFile());
    }
    
    private ApiClient(Config config) {
        this(config.baseUrl, config.apiKey, config.timeout, config.maxRetries);
    }
    
    private static Config loadConfigFromFile() {
        String userHome = System.getProperty("user.home");
        Path configPath = Paths.get(userHome, ".jwcode", "config.properties");
        
        // 配置文件中的值（无默认值）
        String baseUrl = null;
        Duration timeout = Duration.ofMillis(30000);  // 默认超时 30 秒
        int maxRetries = 3;                           // 默认重试次数
        String apiKeyEnvName = "ANTHROPIC_API_KEY";    // 默认环境变量名
        
        boolean configFileExists = Files.exists(configPath);
        if (configFileExists) {
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                Properties props = new Properties();
                props.load(fis);
                
                // 读取必需的配置项 - 不设置默认值，必须从配置文件获取
                if (props.containsKey("api.endpoint")) {
                    String configBaseUrl = props.getProperty("api.endpoint");
                    if (configBaseUrl != null && !configBaseUrl.trim().isEmpty()) {
                        baseUrl = configBaseUrl.trim();
                    }
                }
                
                // 读取可选的配置项 - api.key 暂不读取，稍后在 apiKey 读取阶段处理
                
                // 从配置文件读取要使用的环境变量名称
                if (props.containsKey("api.key.env")) {
                    String envName = props.getProperty("api.key.env");
                    if (envName != null && !envName.trim().isEmpty()) {
                        apiKeyEnvName = envName.trim();
                    }
                }
                
                if (props.containsKey("api.timeout")) {
                    try {
                        long timeoutMs = Long.parseLong(props.getProperty("api.timeout"));
                        if (timeoutMs > 0) {
                            timeout = Duration.ofMillis(timeoutMs);
                        }
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid timeout value in config: " + e.getMessage());
                    }
                }
                
                if (props.containsKey("api.maxRetries")) {
                    try {
                        int retries = Integer.parseInt(props.getProperty("api.maxRetries"));
                        if (retries > 0) {
                            maxRetries = retries;
                        }
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid maxRetries value in config: " + e.getMessage());
                    }
                }
                
                logger.info("Loaded configuration from: " + configPath);
                logger.info("API Endpoint: " + baseUrl);
                logger.info("API Key Env: " + apiKeyEnvName);
                logger.info("Timeout: " + timeout.toMillis() + "ms");
                logger.info("Max Retries: " + maxRetries);
                
            } catch (IOException e) {
                logger.warning("Failed to load configuration file: " + e.getMessage());
            }
        } else {
            logger.info("Configuration file not found: " + configPath);
        }
        
        // 从环境变量读取 API key（优先级最高）
        String apiKey = System.getenv(apiKeyEnvName);
        
        // 如果配置文件中有 api.key，作为备用
        if ((apiKey == null || apiKey.isEmpty()) && configFileExists) {
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                Properties props = new Properties();
                props.load(fis);
                if (props.containsKey("api.key")) {
                    String configApiKey = props.getProperty("api.key");
                    if (configApiKey != null && !configApiKey.trim().isEmpty()) {
                        apiKey = configApiKey.trim();
                    }
                }
            } catch (IOException e) {
                logger.warning("Failed to read api.key from config: " + e.getMessage());
            }
        }
        
        // 如果没有配置文件或配置为空，使用默认值（模拟模式）
        if (baseUrl == null || baseUrl.isEmpty()) {
            logger.warning("未找到 API 端点配置，将使用默认配置（模拟模式）");
            logger.info("提示: 可以使用 'config set-endpoint <endpoint>' 和 'config set-key <api-key>' 来配置 API");
            // 使用默认的 MiniMax API 端点
            baseUrl = "https://api.minimaxi.com/v1/chat/completions";
            // 使用空 API key，ApiClient 会自动使用模拟响应
            apiKey = "";
            logger.info("使用默认端点: " + baseUrl);
        }
        
        return new Config(baseUrl, apiKey, timeout, maxRetries);
    }
    
    private static class Config {
        final String baseUrl;
        final String apiKey;
        final Duration timeout;
        final int maxRetries;
        
        Config(String baseUrl, String apiKey, Duration timeout, int maxRetries) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.timeout = timeout;
            this.maxRetries = maxRetries;
        }
    }
    
    public ApiClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, Duration.ofSeconds(30), 3);
    }
    
    public ApiClient(String baseUrl, String apiKey, Duration timeout, int maxRetries) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        
        this.executorService = Executors.newCachedThreadPool();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .executor(executorService)
                .build();
    }
    
    /**
     * 发送请求
     */
    public CompletableFuture<ApiResponse> sendRequest(ApiRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            // 如果未配置 API 密钥，返回模拟响应
            if (apiKey == null || apiKey.isEmpty()) {
                return createMockResponse(request);
            }
            
            // 真实的 API 调用
            return callApiWithRetry(request, 0);
        }, executorService);
    }
    
    /**
     * 创建模拟响应
     */
    private ApiResponse createMockResponse(ApiRequest request) {
        String lastMessage = request.getMessages().isEmpty() ? "" : 
            contentBlocksToString(request.getMessages().get(request.getMessages().size() - 1).getContent());
        
        String mockContent = "这是一个模拟响应。要获得真实 AI 响应，请使用 'config set-key <your-api-key>' 配置 API 密钥。\n\n" +
                "您询问的是：" + lastMessage + "\n\n" +
                "我可以帮您编写俄罗斯方块游戏！请配置 API 密钥后，我将使用 MiniMax API 为您生成完整的 Java 代码。";
        
        return ApiResponse.builder()
                .success(true)
                .content(mockContent)
                .build();
    }
    
    /**
     * 将 ContentBlock 列表转换为字符串
     */
    private String contentBlocksToString(List<Message.ContentBlock> contentBlocks) {
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : contentBlocks) {
            if (block instanceof Message.TextContent) {
                String text = ((Message.TextContent) block).getText();
                if (text != null) {
                    sb.append(text);
                }
            } else if (block instanceof Message.ToolResultContent) {
                // 处理工具结果内容
                Message.ToolResultContent toolResult = (Message.ToolResultContent) block;
                Object resultObj = toolResult.getResult();
                if (resultObj != null) {
                    sb.append("工具 ").append(toolResult.getToolName()).append(" 执行结果: ");
                    if (resultObj instanceof ToolResult) {
                        ToolResult<?> tr = (ToolResult<?>) resultObj;
                        if (tr.getContent() != null) {
                            sb.append(tr.getContent());
                        } else if (tr.getData() != null) {
                            // 使用 ObjectMapper 序列化为 JSON 而不是 toString()
                            try {
                                sb.append(objectMapper.writeValueAsString(tr.getData()));
                            } catch (Exception e) {
                                sb.append(tr.getData().toString());
                            }
                        } else {
                            sb.append(tr.isSuccess() ? "成功" : "失败");
                        }
                    } else {
                        sb.append(resultObj.toString());
                    }
                }
            }
        }
        return sb.toString();
    }
    
    /**
     * 带重试的 API 调用
     */
    private ApiResponse callApiWithRetry(ApiRequest request, int retryCount) {
        try {
            return callMiniMaxApi(request);
        } catch (Exception e) {
            if (retryCount < maxRetries) {
                logger.warning("API 调用失败，重试 " + (retryCount + 1) + "/" + maxRetries + ": " + e.getMessage());
                try {
                    Thread.sleep(1000 * (retryCount + 1)); // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return callApiWithRetry(request, retryCount + 1);
            }
            logger.severe("API 调用失败，达到最大重试次数: " + e.getMessage());
            return ApiResponse.builder()
                    .success(false)
                    .errorMessage("API 调用失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 调用 MiniMax API
     */
    private ApiResponse callMiniMaxApi(ApiRequest request) throws IOException, InterruptedException {
        // 构建请求体
        ObjectNode requestBody = buildRequestBody(request);
        String requestJson = objectMapper.writeValueAsString(requestBody);
        
        // 自动补全 API 端点路径
        String effectiveUrl = normalizeApiUrl(baseUrl);
        
        // 构建 HTTP 请求
        long startTime = System.currentTimeMillis();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(effectiveUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "JWCode/1.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(timeout)
                .build();
        
        // 打印 AI 对话请求日志
        logger.info("╔══════════════════════════════════════════════════════════════════════╗");
        logger.info("║ [AI 对话请求] 发送到: " + effectiveUrl);
        logger.info("╠══════════════════════════════════════════════════════════════════════╣");
        logger.info("║ 请求体 (JSON):");
        // 打印完整 JSON（不拆分）
            logger.info("║   " + requestJson);
        logger.info("╚══════════════════════════════════════════════════════════════════════╝");
        
        // 发送请求
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        long duration = System.currentTimeMillis() - startTime;
        
        // 打印 AI 对话响应日志
        logger.info("╔══════════════════════════════════════════════════════════════════════╗");
        logger.info("║ [AI 对话响应] 状态码: " + response.statusCode() + ", 耗时: " + duration + "ms");
        logger.info("╠══════════════════════════════════════════════════════════════════════╣");
        logger.info("║ 响应体 (JSON):");
            logger.info("║   " + response.body());
        logger.info("╚══════════════════════════════════════════════════════════════════════╝");
        
        // 解析响应
        return parseApiResponse(response);
    }
    
   
    
    /**
     * 标准化 API URL，自动补全路径
     */
    private String normalizeApiUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://api.minimaxi.com/v1/chat/completions";
        }
        
        // 移除末尾的 /
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        // 如果 URL 已经包含完整路径，直接返回
        if (url.contains("/chat/completions") || url.contains("/v1/")) {
            return url;
        }
        
        // MiniMax API 默认端点
        if (url.contains("minimaxi") || url.contains("minimax")) {
            if (url.endsWith("/v1")) {
                return url + "/chat/completions";
            }
            return url + "/v1/chat/completions";
        }
        
        // Anthropic API
        if (url.contains("anthropic")) {
            if (!url.contains("/v1/messages")) {
                if (url.endsWith("/v1")) {
                    return url + "/messages";
                }
                return url + "/v1/messages";
            }
            return url;
        }
        
        // OpenAI 兼容接口
        if (url.contains("openai") || url.contains("azure") || url.contains("ollama")) {
            if (!url.contains("/chat/completions")) {
                return url + "/chat/completions";
            }
            return url;
        }
        
        // 默认：添加 /v1/chat/completions
        if (!url.contains("/v1")) {
            return url + "/v1/chat/completions";
        }
        
        return url;
    }
    
    /**
     * 构建请求体
     */
    private ObjectNode buildRequestBody(ApiRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        
        // 基础参数
        body.put("model", request.getModel());
        body.put("stream", false);
        body.put("temperature", 0.7);
        body.put("top_p", 0.95);
        
        // 工具相关参数 - 支持 MiniMax API
        // tool_choice: 控制模型何时选择工具 (auto/any/none)
        body.put("tool_choice", "auto");
        // parallel_tool_use: 允许并行执行多个工具调用，提升效率
        // 注意：MiniMax API 可能对并行工具调用的 tool_call_id 有特殊要求
        // 如果遇到 "tool result's tool id not found" 错误，尝试设为 false
        body.put("parallel_tool_use", false);
        
        // 消息
        ArrayNode messages = objectMapper.createArrayNode();
        for (Message message : request.getMessages()) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", message.getRole().getValue());
            
            // 对于 TOOL 类型的消息，需要添加 tool_call_id
            // 防御性检查：确保 tool_call_id 有效
            boolean skipMessage = false;
            if (message.getRole() == Message.Role.TOOL) {
                List<Message.ContentBlock> contentBlocks = message.getContent();
                if (contentBlocks != null && !contentBlocks.isEmpty()) {
                    boolean foundToolResult = false;
                    for (Message.ContentBlock block : contentBlocks) {
                        if (block instanceof Message.ToolResultContent) {
                            Message.ToolResultContent toolResult = (Message.ToolResultContent) block;
                            String toolUseId = toolResult.getToolUseId();
                            
                            // 验证 tool_call_id 是否有效
                            if (toolUseId != null && !toolUseId.trim().isEmpty()) {
                                msgNode.put("tool_call_id", toolUseId);
                                foundToolResult = true;
                            } else {
                                // tool_call_id 无效，跳过此消息
                                logger.warning("跳过无效的 tool result 消息：tool_call_id 为空");
                                skipMessage = true;
                            }
                            break;
                        }
                    }
                    // 如果没有找到 ToolResultContent，也跳过
                    if (!foundToolResult) {
                        logger.warning("跳过无效的 tool result 消息：未找到 ToolResultContent");
                        skipMessage = true;
                    }
                } else {
                    // content 为空，跳过此消息
                    skipMessage = true;
                }
            }
            
            // 如果需要跳过此消息，则继续下一条
            if (skipMessage) {
                continue;
            }
            
            // 处理 ASSISTANT 消息：如果包含 tool_calls，需要正确序列化
            if (message.getRole() == Message.Role.ASSISTANT && message.hasToolCalls()) {
                // assistant 消息的 content 可以为 null（当只有 tool_calls 时）
                String textContent = contentBlocksToString(message.getContent());
                if (textContent != null && !textContent.isEmpty()) {
                    msgNode.put("content", textContent);
                } else {
                    msgNode.put("content", "");
                }
                
                // 添加 tool_calls 数组 - 这是修复 tool_call_id not found 的关键
                ArrayNode toolCallsArray = objectMapper.createArrayNode();
                for (Message.ToolCallInfo toolCall : message.getToolCalls()) {
                    ObjectNode toolCallNode = objectMapper.createObjectNode();
                    toolCallNode.put("id", toolCall.getId());
                    toolCallNode.put("type", "function");
                    
                    ObjectNode functionNode = objectMapper.createObjectNode();
                    functionNode.put("name", toolCall.getName());
                    // arguments 已经是 JSON 字符串，需要作为 RawValue 设置以避免双重转义
                    try {
                        JsonNode argsNode = objectMapper.readTree(toolCall.getArguments());
                        functionNode.set("arguments", argsNode);
                    } catch (Exception e) {
                        // 如果解析失败，作为字符串设置
                        functionNode.put("arguments", toolCall.getArguments());
                    }
                    
                    toolCallNode.set("function", functionNode);
                    toolCallsArray.add(toolCallNode);
                }
                msgNode.set("tool_calls", toolCallsArray);
            } else {
                msgNode.put("content", contentBlocksToString(message.getContent()));
            }
            
            messages.add(msgNode);
        }
        body.set("messages", messages);
        
        // 工具
        List<Tool<?, ?, ?>> tools = request.getTools();
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (Tool<?, ?, ?> tool : tools) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                
                ObjectNode functionNode = objectMapper.createObjectNode();
                functionNode.put("name", tool.getName());
                functionNode.put("description", tool.getDescription());
                functionNode.set("parameters", tool.getInputSchema());
                
                toolNode.set("function", functionNode);
                toolsArray.add(toolNode);
            }
            body.set("tools", toolsArray);
        }
        
        return body;
    }
    
    /**
     * 解析 API 响应
     */
    private ApiResponse parseApiResponse(HttpResponse<String> httpResponse) throws JsonProcessingException {
        if (httpResponse.statusCode() != 200) {
            return ApiResponse.builder()
                    .success(false)
                    .errorMessage("HTTP " + httpResponse.statusCode() + ": " + httpResponse.body())
                    .build();
        }
        
        JsonNode responseJson = objectMapper.readTree(httpResponse.body());
        
        // 检查错误
        if (responseJson.has("error")) {
            String errorMsg = responseJson.get("error").get("message").asText();
            return ApiResponse.builder()
                    .success(false)
                    .errorMessage(errorMsg)
                    .build();
        }
        
        // 解析响应内容
        JsonNode choices = responseJson.get("choices");
        if (choices == null || choices.isEmpty()) {
            return ApiResponse.builder()
                    .success(false)
                    .errorMessage("无效的 API 响应格式")
                    .build();
        }
        
        JsonNode choice = choices.get(0);
        JsonNode message = choice.get("message");
        
        // 构建响应
        ApiResponse.ApiResponseBuilder builder = ApiResponse.builder()
                .success(true);
        
        // 解析内容
        String content = null;
        if (message.has("content")) {
            content = message.get("content").asText();
            builder.content(content);
        }
        
        // 解析工具调用
        List<Message.ToolCallInfo> toolCallInfos = null;
        if (message.has("tool_calls")) {
            ArrayNode toolCalls = (ArrayNode) message.get("tool_calls");
            toolCallInfos = new java.util.ArrayList<>();
            for (JsonNode toolCall : toolCalls) {
                String toolCallId = toolCall.get("id").asText();
                JsonNode function = toolCall.get("function");
                String toolName = function.get("name").asText();
                JsonNode arguments = function.get("arguments");
                
                builder.addToolUse(toolCallId, toolName, arguments);
                
                // 保存 tool_call 信息到列表
                String argsStr = arguments != null && !arguments.isNull() ? arguments.toString() : "{}";
                toolCallInfos.add(new Message.ToolCallInfo(toolCallId, toolName, argsStr));
            }
        }
        
        // 构建 assistant 消息，包含 tool_calls 信息
        // 这是修复 tool_call_id not found 问题的关键
        Message assistantMsg = Message.createAssistantMessageWithToolCalls(content, toolCallInfos);
        builder.messageObj(assistantMsg);
        
        return builder.build();
    }
    
    /**
     * 发送流式请求
     * 
     * @param request API 请求
     * @param handler 流式响应处理器
     * @return CompletableFuture 异步结果
     */
    public CompletableFuture<Void> sendStreamingRequest(ApiRequest request, StreamingResponseHandler handler) {
        return CompletableFuture.runAsync(() -> {
            try {
                ObjectNode body = buildRequestBody(request);
                body.put("stream", true); // 启用流式响应
                
                String jsonBody = objectMapper.writeValueAsString(body);
                
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                
                // 发送请求并获取流式响应
                HttpResponse<InputStream> response = httpClient.send(
                        httpRequest, 
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                
                if (response.statusCode() != 200) {
                    throw new RuntimeException("HTTP " + response.statusCode());
                }
                
                // 处理流式响应
                handler.processStream(response.body());
                
            } catch (Exception e) {
                logger.severe("流式请求失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        executorService.shutdown();
    }
    
    // Getters
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public Duration getTimeout() { return timeout; }
    public int getMaxRetries() { return maxRetries; }
    
    // Setters
    public void setBaseUrl(String baseUrl) { 
        this.baseUrl = baseUrl; 
    }
    
    public void setApiKey(String apiKey) { 
        this.apiKey = apiKey; 
    }
    
    public void setTimeout(Duration timeout) { 
        this.timeout = timeout; 
        // Recreate HttpClient with new timeout
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .executor(executorService)
                .build();
    }
    
    public void setMaxRetries(int maxRetries) { 
        this.maxRetries = maxRetries; 
    }
    
    /**
     * 流式 API 响应
     */
    public static class StreamingApiResponse {
        private final String content;
        
        public StreamingApiResponse(String content) {
            this.content = content;
        }
        
        public String getContent() { return content; }
    }
}
