package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.llm.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 测试模型命令 - 验证模型配置是否正确
 */
public class TestModelCommand implements Command {
    
    @Override
    public String getName() {
        return "test-model";
    }
    
    @Override
    public String getDescription() {
        return "测试模型配置是否正确";
    }
    
    @Override
    public String getUsage() {
        return "test-model [模型名称]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                 🧪 模型配置测试                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        
        String[] argsArray = args != null && !args.isEmpty() ? args.split("\\s+") : new String[0];
        
        // 1. 检查 YAML 配置
        System.out.println("\n📋 步骤 1: 检查配置文件");
        JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
        if (config == null || config.getProviders().isEmpty()) {
            System.out.println("   ❌ 未找到 YAML 配置文件");
            System.out.println("   📄 期望位置: " + YamlConfigLoader.getInstance().getUserConfigPath());
            return CommandResult.error("配置文件不存在");
        }
        System.out.println("   ✅ 配置文件已加载");
        System.out.println("   📄 配置文件: " + YamlConfigLoader.getInstance().getUserConfigPath());
        
        // 2. 检查提供商配置
        System.out.println("\n📋 步骤 2: 检查提供商配置");
        JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
        if (provider == null) {
            System.out.println("   ❌ 未找到默认提供商配置");
            return CommandResult.error("提供商配置缺失");
        }
        System.out.println("   ✅ 提供商: " + config.getDefaultProvider());
        System.out.println("   🔗 端点: " + provider.getBaseUrl());
        
        // 3. 检查 API Key
        System.out.println("\n📋 步骤 3: 检查 API Key");
        String apiKey = provider.getCurrentApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("   ❌ 未配置 API Key");
            return CommandResult.error("API Key 未配置");
        }
        System.out.println("   ✅ API Key 已配置");
        System.out.println("   🔑 Key 前缀: " + apiKey.substring(0, Math.min(10, apiKey.length())) + "...");
        
        // 4. 检查模型配置
        System.out.println("\n📋 步骤 4: 检查模型配置");
        String modelName = argsArray.length > 0 ? argsArray[0] : null;
        JwcodeConfig.ModelDefinition model = null;
        
        if (modelName != null) {
            model = provider.findModel(modelName).orElse(null);
            if (model == null) {
                System.out.println("   ⚠️  模型 '" + modelName + "' 不在配置列表中，将尝试直接使用");
            }
        } else {
            model = config.getDefaultModel();
            if (model != null) {
                modelName = model.getName();
            }
        }
        
        if (modelName == null || modelName.isEmpty()) {
            System.out.println("   ❌ 未配置模型名称");
            return CommandResult.error("模型名称未配置");
        }
        
        System.out.println("   ✅ 模型: " + modelName);
        if (model != null) {
            System.out.println("   📝 ID: " + model.getId());
            System.out.println("   🌡️  Temperature: " + (model.getTemperature() != null ? model.getTemperature() : "未设置(使用默认值)"));
            System.out.println("   📊 Max Tokens: " + model.getMaxTokens());
            System.out.println("   📐 Context Window: " + model.getContextWindow());
        }
        
        // 5. 测试 API 连接
        System.out.println("\n📋 步骤 5: 测试 API 连接");
        System.out.println("   🔄 正在发送测试请求...");
        
        try {
            LLMTestResult result = testModel(config);
            
            if (result.isAvailable()) {
                System.out.println("   ✅ 模型连接成功!");
                System.out.println("   ⏱️  延迟: " + result.getLatencyMs() + " ms");
                System.out.println("   📨 消息: " + result.getMessage());
            } else {
                System.out.println("   ❌ 模型连接失败");
                System.out.println("   📨 错误: " + result.getMessage());
                if (result.hasSuggestion()) {
                    System.out.println("   💡 建议: " + result.getSuggestion());
                }
                return CommandResult.error("模型测试失败: " + result.getMessage());
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ 测试过程出错: " + e.getMessage());
            e.printStackTrace();
            return CommandResult.error("测试异常: " + e.getMessage());
        }
        
        // 6. 发送实际对话测试
        System.out.println("\n📋 步骤 6: 发送对话测试");
        System.out.println("   🔄 正在发送 'Hello'...");
        
        try {
            LLMResponse response = testConversation(config);
            
            if (response.isSuccess()) {
                System.out.println("   ✅ 对话测试成功!");
                System.out.println("   💬 响应: " + response.getContent().substring(0, Math.min(100, response.getContent().length())) + "...");
                if (response.getTotalTokens() > 0) {
                    System.out.println("   📊 Tokens: " + response.getPromptTokens() + " (输入) + " + 
                        response.getCompletionTokens() + " (输出) = " + response.getTotalTokens());
                }
            } else {
                System.out.println("   ❌ 对话测试失败");
                System.out.println("   📨 错误: " + response.getErrorMessage());
                return CommandResult.error("对话测试失败: " + response.getErrorMessage());
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ 对话测试出错: " + e.getMessage());
            e.printStackTrace();
            return CommandResult.error("对话测试异常: " + e.getMessage());
        }
        
        // 所有测试通过
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║              ✅ 所有测试通过!                              ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        
        return CommandResult.success("模型配置正确，可以正常使用");
    }
    
    /**
     * 测试模型可用性
     */
    private LLMTestResult testModel(JwcodeConfig config) throws Exception {
        return LLMTest.testConfig(config);
    }
    
    /**
     * 测试对话
     */
    private LLMResponse testConversation(JwcodeConfig config) throws Exception {
        LLMFactory factory = LLMFactory.fromConfig(config);
        LLMService llmService = factory.getLLMService();
        
        List<LLMMessage> messages = List.of(
            LLMMessage.system("You are a helpful assistant."),
            LLMMessage.user("Hello")
        );
        
        CompletableFuture<LLMResponse> future = llmService.chat(messages);
        return future.get();
    }
}
