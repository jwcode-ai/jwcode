package com.jwcode.core.llm;

import com.jwcode.core.config.JwcodeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * LLM 连接测试器
 * 
 * 用于验证配置是否正确，API 是否可访问
 */
public class LLMTest {
    
    private static final Logger logger = Logger.getLogger(LLMTest.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * 测试配置是否可用
     */
    public static LLMTestResult testConfig(JwcodeConfig config) {
        if (config == null) {
            return LLMTestResult.error("配置为空", "请检查配置文件路径");
        }
        
        logger.info("[LLMTest] Testing config for provider: " + config.getDefaultProvider());
        
        JwcodeConfig.ProviderConfig provider = config.getCurrentProvider();
        if (provider == null) {
            return LLMTestResult.error(
                "找不到默认 provider: " + config.getDefaultProvider(),
                "请检查配置文件中的 default-provider 设置"
            );
        }
        
        // 测试 API 连通性
        return testApiConnectivity(provider);
    }
    
    /**
     * 测试 API 连通性
     */
    public static LLMTestResult testApiConnectivity(JwcodeConfig.ProviderConfig provider) {
        long startTime = System.currentTimeMillis();
        
        try {
            String apiKey = provider.getCurrentApiKey();
            String baseUrl = provider.getBaseUrl();
            
            if (apiKey == null || apiKey.isEmpty()) {
                return LLMTestResult.error(
                    "API Key 为空",
                    "请在配置文件中添加有效的 API Key"
                );
            }
            
            logger.info("[LLMTest] Testing API at: " + baseUrl);
            
            // 构建简单测试请求
            String testBody = buildTestRequest(provider);
            
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(testBody))
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            long latencyMs = System.currentTimeMillis() - startTime;
            
            if (response.statusCode() == 200) {
                // 解析响应
                JsonNode json = mapper.readTree(response.body());
                String model = json.path("model").asText("unknown");
                
                return LLMTestResult.success(
                    "API 连接成功! 模型: " + model + ", 延迟: " + latencyMs + "ms",
                    latencyMs
                );
                
            } else if (response.statusCode() == 401) {
                return LLMTestResult.error(
                    "API Key 无效 (401)",
                    "请检查配置文件中的 API Key 是否正确"
                );
                
            } else if (response.statusCode() == 429) {
                return LLMTestResult.error(
                    "请求过于频繁 (429)",
                    "请稍后重试，或检查配额限制"
                );
                
            } else {
                return LLMTestResult.error(
                    "API 返回错误: HTTP " + response.statusCode(),
                    "响应: " + response.body().substring(0, Math.min(200, response.body().length()))
                );
            }
            
        } catch (java.net.ConnectException e) {
            return LLMTestResult.error(
                "无法连接到 API 服务器",
                "请检查网络连接和 base-url 配置"
            );
            
        } catch (java.net.http.HttpTimeoutException e) {
            return LLMTestResult.error(
                "连接超时",
                "请检查网络连接，或增加超时时间"
            );
            
        } catch (Exception e) {
            return LLMTestResult.error(
                "测试失败: " + e.getMessage(),
                "请检查配置和网络连接"
            );
        }
    }
    
    /**
     * 构建测试请求
     */
    private static String buildTestRequest(JwcodeConfig.ProviderConfig provider) {
        // 使用第一个模型，或默认模型
        String model = "gpt-4o-mini";  // 默认模型
        if (provider.getModels() != null && !provider.getModels().isEmpty()) {
            model = provider.getModels().get(0).getId();
        }
        
        return "{\n" +
            "  \"model\": \"" + model + "\",\n" +
            "  \"messages\": [{\"role\": \"user\", \"content\": \"Hi\"}],\n" +
            "  \"max_tokens\": 10\n" +
            "}";
    }
    
    /**
     * 快速测试 - 使用默认配置
     */
    public static LLMTestResult quickTest() {
        try {
            java.nio.file.Path configPath = java.nio.file.Path.of(
                System.getProperty("user.home"), ".jwcode", "config.yaml"
            );
            
            if (!java.nio.file.Files.exists(configPath)) {
                return LLMTestResult.error(
                    "配置文件不存在: " + configPath,
                    "请运行 'jwcode config' 创建配置文件"
                );
            }
            
            JwcodeConfig config = JwcodeConfig.load(configPath.toString());
            return testConfig(config);
            
        } catch (Exception e) {
            return LLMTestResult.error(
                "加载配置失败: " + e.getMessage(),
                "请检查配置文件格式"
            );
        }
    }
    
    /**
     * 主方法 - 用于命令行测试
     */
    public static void main(String[] args) {
        System.out.println("=== JWCode LLM Connection Test ===\n");
        
        LLMTestResult result = quickTest();
        
        System.out.println("Status: " + (result.isAvailable() ? "✓ 可用" : "✗ 不可用"));
        System.out.println("Message: " + result.getMessage());
        
        if (result.getLatencyMs() > 0) {
            System.out.println("Latency: " + result.getLatencyMs() + "ms");
        }
        
        if (result.hasSuggestion()) {
            System.out.println("\nSuggestion: " + result.getSuggestion());
        }
        
        System.exit(result.isAvailable() ? 0 : 1);
    }
}
