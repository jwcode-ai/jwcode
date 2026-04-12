# OpenAI 兼容模式模型加载器

本文档描述了重构后的模型加载功能，该功能统一使用 OpenAI 兼容的 API 格式。

## 概述

本次重构简化了原有的多提供商支持架构，统一使用 **OpenAI 兼容模式**。这意味着所有模型调用都使用标准的 OpenAI API 格式：

- **端点**: `/v1/chat/completions`
- **请求格式**: OpenAI Chat Completions API 格式
- **响应格式**: OpenAI Chat Completions API 格式
- **工具调用**: OpenAI Function Calling 格式

## 架构变化

### 简化前（多提供商模式）

```
ProviderType: KIMI, ANTHROPIC, MINIMAX, OPENAI, AZURE, OLLAMA, CUSTOM
         ↓
不同的请求/响应格式转换
         ↓
复杂的兼容性检查
```

### 简化后（OpenAI 兼容模式）

```
ProviderType: OPENAI_COMPATIBLE (单一模式)
         ↓
统一的 OpenAI 格式
         ↓
简化架构，易于维护
```

## 核心组件

### 1. ProviderType（简化）

```java
public enum ProviderType {
    OPENAI_COMPATIBLE("openai_compatible", "OpenAI Compatible", true);
    
    // 所有提供商都使用 OpenAI 兼容格式
    public static String normalizeApiUrl(String url) {
        // 确保 URL 以 /v1/chat/completions 结尾
    }
}
```

### 2. ModelLoader（统一实现）

```java
public class ModelLoader {
    // 统一使用 OpenAI 兼容格式
    public String buildRequestBody(ModelConfig config, ApiRequest request);
    public ApiResponse parseResponse(String responseBody);
    public CompletableFuture<ModelTestResult> testModel(ModelConfig config);
    public CompletableFuture<ApiResponse> sendRequest(ModelConfig config, ApiRequest request);
}
```

### 3. ApiClientV2（简化）

```java
public class ApiClientV2 {
    // 配置
    private String baseUrl;      // API 端点
    private String apiKey;       // API 密钥
    private String modelName;    // 模型名称
    private Duration timeout;    // 超时时间
    private int maxRetries;      // 重试次数
    
    // 核心方法
    public CompletableFuture<ApiResponse> sendRequest(ApiRequest request);
    public CompletableFuture<ApiResponse> sendMessage(String message);
    public CompletableFuture<ModelTestResult> testModel(String model);
}
```

## 配置方式

### 1. 配置文件（~/.jwcode/config.properties）

```properties
# API 端点（任何 OpenAI 兼容的端点）
api.endpoint=https://api.openai.com/v1
# 或 MiniMax: https://api.minimaxi.com/v1
# 或其他兼容端点...

# API 密钥
api.key=your-api-key-here
# 或从环境变量读取: api.key.env=OPENAI_API_KEY

# 模型名称
api.model=gpt-3.5-turbo
# 或 gpt-4, MiniMax-M2.7, 等其他 OpenAI 兼容模型

# 超时时间（毫秒）
api.timeout=60000
```

### 2. 代码配置

```java
// 方式1：使用默认配置（从配置文件加载）
ApiClientV2 client = new ApiClientV2();

// 方式2：使用指定端点和密钥
ApiClientV2 client = new ApiClientV2(
    "https://api.openai.com/v1",
    "your-api-key"
);

// 方式3：使用完整参数
ApiClientV2 client = new ApiClientV2(
    "https://api.openai.com/v1",
    "your-api-key",
    "gpt-4",
    Duration.ofSeconds(60),
    3
);
```

## 使用示例

### 简单调用

```java
ApiClientV2 client = new ApiClientV2();

// 发送简单消息
ApiResponse response = client.sendMessage("Hello, world!").join();
if (response.isSuccess()) {
    System.out.println(response.getContent());
}
```

### 带上下文的调用

```java
List<Message> messages = Arrays.asList(
    Message.createSystemMessage("You are a helpful assistant."),
    Message.createUserMessage("What is Java?")
);

ApiResponse response = client.sendMessage("gpt-3.5-turbo", messages).join();
```

### 工具调用

```java
List<Tool<?, ?, ?>> tools = Arrays.asList(
    new BashTool(),
    new FileReadTool()
);

ApiResponse response = client.sendMessageWithTools(
    "gpt-4", 
    messages, 
    tools
).join();

// 处理工具调用
if (response.hasToolUse()) {
    for (ToolUse toolUse : response.getToolUses()) {
        System.out.println("Tool: " + toolUse.getName());
        System.out.println("Args: " + toolUse.getInput());
    }
}
```

### 测试模型可用性

```java
ModelTestResult result = client.testModel("gpt-4").join();
if (result.isAvailable()) {
    System.out.println("Model available, latency: " + result.getLatencyMs() + "ms");
} else {
    System.err.println("Model unavailable: " + result.getMessage());
}
```

## 支持的提供商

任何兼容 OpenAI API 格式的提供商都可以使用：

| 提供商 | 端点示例 | 说明 |
|--------|----------|------|
| OpenAI | `https://api.openai.com/v1` | 官方 API |
| MiniMax | `https://api.minimaxi.com/v1` | 国内可用 |
| Azure OpenAI | `https://{resource}.openai.azure.com/openai/deployments/{deployment}` | 企业版 |
| Kimi | 通过兼容模式 | 使用兼容端点 |
| Ollama | `http://localhost:11434/v1` | 本地模型 |
| 其他 | 任何兼容端点 | 自定义部署 |

## 请求/响应格式

### 请求格式（OpenAI 标准）

```json
{
  "model": "gpt-3.5-turbo",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello!"}
  ],
  "max_tokens": 1024,
  "stream": false,
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "bash",
        "description": "Execute bash commands",
        "parameters": {...}
      }
    }
  ]
}
```

### 响应格式（OpenAI 标准）

```json
{
  "id": "chatcmpl-xxx",
  "model": "gpt-3.5-turbo",
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "Hello! How can I help you?",
      "tool_calls": [...]
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 10,
    "total_tokens": 30
  }
}
```

## 迁移指南

### 从旧版本迁移

1. **更新配置文件**
   - 移除 `api.key.env` 中的特定提供商前缀
   - 确保端点 URL 指向 OpenAI 兼容的端点

2. **代码调整**
   - `ApiClientV2` 的使用方式保持不变
   - 无需再处理不同提供商的兼容性问题

3. **模型名称**
   - 使用提供商支持的 OpenAI 兼容模型名称
   - 例如：`gpt-3.5-turbo`, `gpt-4`, `MiniMax-M2.7` 等

## 优势

1. **简化架构**: 单一模式，代码更易维护
2. **广泛兼容**: 支持所有 OpenAI 兼容的 API 提供商
3. **标准格式**: 使用业界标准的 OpenAI API 格式
4. **易于扩展**: 新的兼容提供商无需额外开发
5. **减少错误**: 无需处理不同格式之间的转换问题

## 注意事项

1. 确保使用的端点真正兼容 OpenAI API 格式
2. 模型名称需要是提供商支持的 OpenAI 兼容模型
3. 某些提供商可能不支持所有 OpenAI 功能（如工具调用、流式响应等）
