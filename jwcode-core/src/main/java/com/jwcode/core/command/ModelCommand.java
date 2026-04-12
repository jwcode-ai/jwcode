package com.jwcode.core.command;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.session.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型命令 - 切换 AI 模型
 * 
 * 从配置文件读取可用模型列表
 */
public class ModelCommand implements Command {
    
    // 如果配置文件中没有定义模型，使用这些通用模型名称作为示例
    private static final List<String> FALLBACK_MODELS = List.of(
        "kimi-k2.5",
        "kimi-k1.5",
        "kimi-128k",
        "gpt-4",
        "gpt-3.5-turbo"
    );
    
    @Override
    public String getName() {
        return "model";
    }
    
    @Override
    public String getDescription() {
        return "查看或切换 AI 模型";
    }
    
    @Override
    public String getUsage() {
        return "model [model-name]";
    }
    
    @Override
    public CommandResult execute(String[] args, Session session) {
        // 从配置获取可用模型列表
        List<String> availableModels = getAvailableModelsFromConfig();
        String currentModel = session != null ? session.getModel() : null;
        
        // 如果当前模型不在列表中，添加到列表
        if (currentModel != null && !availableModels.contains(currentModel)) {
            availableModels = new ArrayList<>(availableModels);
            availableModels.add(currentModel);
        }
        
        if (args.length == 0) {
            // 显示当前模型和可用模型
            StringBuilder sb = new StringBuilder();
            sb.append("可用模型:\n");
            
            for (String model : availableModels) {
                if (model.equals(currentModel)) {
                    sb.append("  * ").append(model).append(" (当前)\n");
                } else {
                    sb.append("    ").append(model).append("\n");
                }
            }
            
            sb.append("\n提示: 从配置文件 (~/.jwcode/config.yaml) 加载模型列表");
            return CommandResult.success(sb.toString());
        }
        
        // 切换模型
        String newModel = args[0];
        
        // 允许切换到任何模型（不限制在列表内），但给出警告
        if (!availableModels.contains(newModel)) {
            System.out.println("[提示] 模型 '" + newModel + "' 不在配置文件中，但尝试切换...");
        }
        
        if (session != null) {
            session.setModel(newModel);
            return CommandResult.success("已切换到模型: " + newModel);
        }
        
        return CommandResult.error("无活动会话，无法切换模型");
    }
    
    /**
     * 从配置文件获取可用模型列表
     */
    private List<String> getAvailableModelsFromConfig() {
        List<String> models = new ArrayList<>();
        
        try {
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
            
            if (provider != null && provider.getModels() != null) {
                for (JwcodeConfig.ModelDefinition model : provider.getModels()) {
                    if (model.getName() != null && !model.getName().isEmpty()) {
                        models.add(model.getName());
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误，使用后备列表
        }
        
        if (models.isEmpty()) {
            return FALLBACK_MODELS;
        }
        
        return models;
    }
    
    /**
     * 获取默认模型（从配置）
     */
    public static String getDefaultModel() {
        try {
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            JwcodeConfig.ModelDefinition model = config.getDefaultModel();
            if (model != null && model.getName() != null) {
                return model.getName();
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        // 后备：从旧版配置读取
        try {
            com.jwcode.common.config.ConfigLoader config = new com.jwcode.common.config.ConfigLoader();
            String model = (String) config.getConfig("api.model", false);
            if (model != null && !model.isEmpty()) {
                return model;
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        return null;  // 没有默认模型
    }
}
