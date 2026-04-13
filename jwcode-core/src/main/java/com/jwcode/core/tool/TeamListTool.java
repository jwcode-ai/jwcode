package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.team.Team;
import com.jwcode.core.team.TeamManager;
import com.jwcode.core.team.TeamMember;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 团队列表工具
 * 用于列出团队和成员信息
 */
public class TeamListTool implements Tool<TeamListTool.Input, TeamListTool.Output, Void> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TeamManager teamManager;
    
    public TeamListTool() {
        this.teamManager = TeamManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "TeamList";
    }
    
    @Override
    public String getDescription() {
        return "列出所有团队或团队成员信息。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 TeamList 工具查看团队信息。
               
               参数:
               - teamId: 团队ID（可选，指定则查看该团队详情）
               - userId: 用户ID（可选，指定则查看该用户的团队）
               - includeMembers: 是否包含成员信息（可选，默认 false）
               
               示例:
               - {} - 列出所有团队
               - {"teamId": "team_abc123"} - 查看指定团队详情
               - {"includeMembers": true} - 列出所有团队及成员
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "teamId": {
                            "type": "string",
                            "description": "团队ID（指定则查看详情）"
                        },
                        "userId": {
                            "type": "string",
                            "description": "用户ID（指定则查看该用户的团队）"
                        },
                        "includeMembers": {
                            "type": "boolean",
                            "description": "是否包含成员信息"
                        }
                    }
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
                Output output = new Output();
                output.teams = new ArrayList<>();
                
                boolean includeMembers = input != null && input.includeMembers;
                
                if (input != null && input.teamId != null && !input.teamId.isEmpty()) {
                    // 查看指定团队详情
                    Team team = teamManager.getTeam(input.teamId).orElse(null);
                    if (team != null) {
                        output.teams.add(convertToTeamInfo(team, includeMembers));
                        output.message = "团队详情";
                    } else {
                        output.message = "团队不存在: " + input.teamId;
                    }
                } else if (input != null && input.userId != null && !input.userId.isEmpty()) {
                    // 查看指定用户的团队
                    List<Team> userTeams = teamManager.getTeamsForUser(input.userId);
                    for (Team team : userTeams) {
                        output.teams.add(convertToTeamInfo(team, includeMembers));
                    }
                    output.message = "用户 " + input.userId + " 的团队列表（共 " + userTeams.size() + " 个）";
                } else {
                    // 列出所有团队
                    List<Team> allTeams = teamManager.getAllTeams();
                    for (Team team : allTeams) {
                        output.teams.add(convertToTeamInfo(team, includeMembers));
                    }
                    output.message = "所有团队列表（共 " + allTeams.size() + " 个）";
                }
                
                output.success = true;
                output.totalCount = output.teams.size();
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                return ToolResult.error("获取团队列表失败: " + e.getMessage());
            }
        });
    }
    
    private TeamInfo convertToTeamInfo(Team team, boolean includeMembers) {
        TeamInfo info = new TeamInfo();
        info.id = team.getId();
        info.name = team.getName();
        info.description = team.getDescription();
        info.ownerId = team.getOwnerId();
        info.createdAt = team.getCreatedAt() != null ? team.getCreatedAt().toString() : null;
        info.memberCount = teamManager.getMembers(team.getId()).size();
        
        if (includeMembers) {
            List<TeamMember> members = teamManager.getMembers(team.getId());
            info.members = members.stream()
                    .map(m -> {
                        MemberInfo mi = new MemberInfo();
                        mi.userId = m.getUserId();
                        mi.role = m.getRole().name();
                        mi.joinedAt = m.getJoinedAt() != null ? m.getJoinedAt().toString() : null;
                        return mi;
                    })
                    .collect(Collectors.toList());
        }
        
        return info;
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
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }
    
    @Override
    public boolean isDestructive(Input input) {
        return false;
    }
    
    // 输入类
    public static class Input {
        public String teamId;
        public String userId;
        public boolean includeMembers;
    }
    
    // 输出类
    public static class Output {
        public boolean success;
        public String message;
        public int totalCount;
        public List<TeamInfo> teams;
    }
    
    // 团队信息
    public static class TeamInfo {
        public String id;
        public String name;
        public String description;
        public String ownerId;
        public String createdAt;
        public int memberCount;
        public List<MemberInfo> members;
    }
    
    // 成员信息
    public static class MemberInfo {
        public String userId;
        public String role;
        public String joinedAt;
    }
}
