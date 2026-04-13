package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.team.Team;
import com.jwcode.core.team.TeamManager;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 团队删除工具
 * 用于删除团队（需要 owner 权限）
 */
public class TeamDeleteTool implements Tool<TeamDeleteTool.Input, TeamDeleteTool.Output, Void> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TeamManager teamManager;
    
    public TeamDeleteTool() {
        this.teamManager = TeamManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "TeamDelete";
    }
    
    @Override
    public String getDescription() {
        return "删除团队。只有团队所有者可以删除团队。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 TeamDelete 工具删除团队。
               
               参数:
               - id: 团队ID（必需）
               - userId: 操作用户ID（可选，默认使用当前用户）
               
               注意:
               - 只有团队所有者可以删除团队
               - 删除后无法恢复
               
               示例:
               - {"id": "team_abc123"} - 删除指定团队
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "id": {
                            "type": "string",
                            "description": "要删除的团队ID"
                        },
                        "userId": {
                            "type": "string",
                            "description": "操作用户ID"
                        }
                    },
                    "required": ["id"]
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
                if (input == null || input.id == null || input.id.trim().isEmpty()) {
                    return ToolResult.error("团队ID不能为空");
                }
                
                // 获取用户ID
                String userId = input.userId;
                if (userId == null || userId.isEmpty()) {
                    userId = getCurrentUserId(context);
                }
                
                // 获取团队信息
                Optional<Team> teamOpt = teamManager.getTeam(input.id);
                if (teamOpt.isEmpty()) {
                    Output output = new Output();
                    output.success = false;
                    output.error = "团队不存在: " + input.id;
                    return ToolResult.success(output);
                }
                
                Team team = teamOpt.get();
                
                // 权限检查
                if (!team.getOwnerId().equals(userId)) {
                    Output output = new Output();
                    output.success = false;
                    output.error = "权限不足：只有团队所有者可以删除团队";
                    return ToolResult.success(output);
                }
                
                // 删除团队
                boolean deleted = teamManager.deleteTeam(input.id, userId);
                
                Output output = new Output();
                output.success = deleted;
                output.message = deleted 
                        ? "团队删除成功: " + team.getName() 
                        : "团队删除失败";
                
                return ToolResult.success(output);
                
            } catch (IllegalStateException e) {
                Output output = new Output();
                output.success = false;
                output.error = e.getMessage();
                return ToolResult.success(output);
            } catch (Exception e) {
                return ToolResult.error("删除团队失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId(ToolExecutionContext context) {
        String userId = System.getProperty("jwcode.user.id");
        if (userId == null || userId.isEmpty()) {
            userId = System.getenv("JWCODE_USER_ID");
        }
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
        if (input.id == null || input.id.trim().isEmpty()) {
            return ToolValidationResult.invalid("团队ID不能为空");
        }
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(Input input) {
        return true;
    }
    
    @Override
    public boolean requiresApproval(Input input) {
        return true;
    }
    
    // 输入类
    public static class Input {
        public String id;
        public String userId;
    }
    
    // 输出类
    public static class Output {
        public boolean success;
        public String message;
        public String error;
    }
}
