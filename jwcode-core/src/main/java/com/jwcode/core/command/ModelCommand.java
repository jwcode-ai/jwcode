package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 模型命令 - 切换 AI 模型
 */
public class ModelCommand implements Command {
    
    private static final java.util.List<String> AVAILABLE_MODELS = java.util.List.of(
        "MiniMax-M2.7",
        "MiniMax-Text-01",
        "claude-3-5-sonnet",
        "gpt-4"
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
        if (args.length == 0) {
            // 显示当前模型和可用模型
            StringBuilder sb = new StringBuilder();
            sb.append("可用模型:\n");
            
            String currentModel = session != null ? session.getModel() : "default";
            
            for (String model : AVAILABLE_MODELS) {
                if (model.equals(currentModel)) {
                    sb.append("  * ").append(model).append(" (当前)\n");
                } else {
                    sb.append("    ").append(model).append("\n");
                }
            }
            
            return CommandResult.success(sb.toString());
        }
        
        // 切换模型
        String newModel = args[0];
        if (!AVAILABLE_MODELS.contains(newModel)) {
            return CommandResult.error("未知模型: " + newModel + 
                "\n可用模型: " + String.join(", ", AVAILABLE_MODELS));
        }
        
        if (session != null) {
            session.setModel(newModel);
            return CommandResult.success("已切换到模型: " + newModel);
        }
        
        return CommandResult.error("无活动会话，无法切换模型");
    }
}
