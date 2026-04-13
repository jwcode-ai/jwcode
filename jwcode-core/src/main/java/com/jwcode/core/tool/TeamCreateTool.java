package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.team.Team;
import com.jwcode.core.team.TeamManager;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 团队创建工具
 * 用于创建新的工作团队
 */
public class TeamCreateTool implements Tool<TeamCreateTool.Input, TeamCreateTool.Output, Void> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TeamManager teamManager;
    
    public TeamCreateTool() {
        this.teamManager = TeamManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "TeamCreate";
    }
    
    @Override
    public String getDescription() {
        return "创建新的工作团队。用于多用户协作场景。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 TeamCreate 工具创建新团队。
               
               参数:
               - name: 团队名称（必需）
               - description: 团队描述（可选）
               - ownerId: 创建者ID（可选，默认使用当前用户）
               
               示例:
               - {"name": "后端开发组"} - 创建简单团队
               - {"name": "前端团队", "description": "负责用户界面开发"} - 创建带描述的团队
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "name": {
                            "type": "string",
                            "description": "团队名称"
                        },
                        "description": {
                            "type": "string",
                            "description": "团队描述"
                        },
                        "ownerId": {
                            "type": "string",
                            "description": "创建者ID"
                        }
                    },
                    "required": ["name"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 参数验证
                if (input == null || input.name == null || input.name.trim().isEmpty()) {
                    return ToolResult.error("团队名称不能为空");
                }
                
                // 获取 ownerId
                String ownerId = input.ownerId;
                if (ownerId == null || ownerId.isEmpty()) {
                    // 从上下文获取当前用户ID
                    ownerId = getCurrentUserId(context);
                }
                
                // 创建团队
                Team team = teamManager.createTeam(
                        input.name,
                        input.description,
                        ownerId
                );
                
                // 构建输出
                Output output = new Output();
                output.success = true;
                output.id = team.getId();
                output.name = team.getName();
                output.description = team.getDescription();
                output.ownerId = team.getOwnerId();
                output.message = "团队创建成功: " + team.getName() + " (ID: " + team.getId() + ")";
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                return ToolResult.error("创建团队失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId(ToolExecutionContext context) {
        // 从系统属性或环境变量获取
        String userId = System.getProperty("jwcode.user.id");
        if (userId == null || userId.isEmpty()) {
            userId = System.getenv("JWCODE_USER_ID");
        }
        // 默认用户
        if (userId == null || userId.isEmpty()) {
            userId = "user_default";
        }
        return userId;
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.name == null || input.name.trim().isEmpty()) {
            return ToolValidationResult.invalid("团队名称不能为空");
        }
        if (input.name.length() > 100) {
            return ToolValidationResult.invalid("团队名称不能超过100个字符");
        }
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(Input input) {
        return false;
    }
    
    // 输入类
    public static class Input {
        public String name;
        public String description;
        public String ownerId;
    }
    
    // 输出类
    public static class Output {
        public boolean success;
        public String id;
        public String name;
        public String description;
        public String ownerId;
        public String message;
    }
}
