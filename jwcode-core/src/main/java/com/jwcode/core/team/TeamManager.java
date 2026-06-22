package com.jwcode.core.team;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * 团队管理器
 * 管理团队的创建、删除、成员管理等操作
 */
public class TeamManager {

    private static final Logger LOGGER = Logger.getLogger(TeamManager.class.getName());

    private static final String DATA_DIR = ".jwcode";
    private static final String TEAMS_FILE = "teams.json";
    private static final String MEMBERS_FILE = "team_members.json";
    
    private static TeamManager instance;
    
    private final Map<String, Team> teams;
    private final Map<String, List<TeamMember>> teamMembers;
    private final ObjectMapper objectMapper;
    private final Path dataDirectory;
    private final Path teamsFilePath;
    private final Path membersFilePath;
    
    private TeamManager() {
        this.teams = new ConcurrentHashMap<>();
        this.teamMembers = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        String userDir = System.getProperty("user.dir");
        this.dataDirectory = Paths.get(userDir, DATA_DIR);
        this.teamsFilePath = dataDirectory.resolve(TEAMS_FILE);
        this.membersFilePath = dataDirectory.resolve(MEMBERS_FILE);
        
        loadData();
    }
    
    public static synchronized TeamManager getInstance() {
        if (instance == null) {
            instance = new TeamManager();
        }
        return instance;
    }
    
    /**
     * 创建新团队
     * @param name 团队名称
     * @param description 团队描述
     * @param ownerId 创建者ID
     * @return 创建的团队
     */
    public Team createTeam(String name, String description, String ownerId) {
        String teamId = generateTeamId();
        Team team = new Team(teamId, name, ownerId);
        team.setDescription(description);
        
        teams.put(teamId, team);
        
        // 自动将创建者添加为管理员
        addMember(teamId, ownerId, TeamMember.Role.ADMIN);
        
        saveData();
        return team;
    }
    
    /**
     * 删除团队
     * @param teamId 团队ID
     * @param userId 操作用户ID（必须是owner）
     * @return 是否删除成功
     */
    public boolean deleteTeam(String teamId, String userId) {
        Team team = teams.get(teamId);
        if (team == null) {
            return false;
        }
        
        if (!team.getOwnerId().equals(userId)) {
            throw new IllegalStateException("只有团队所有者可以删除团队");
        }
        
        teams.remove(teamId);
        teamMembers.remove(teamId);
        saveData();
        return true;
    }
    
    /**
     * 获取团队
     */
    public Optional<Team> getTeam(String teamId) {
        return Optional.ofNullable(teams.get(teamId));
    }
    
    /**
     * 获取所有团队
     */
    public List<Team> getAllTeams() {
        return new ArrayList<>(teams.values());
    }
    
    /**
     * 获取用户所属的所有团队
     */
    public List<Team> getTeamsForUser(String userId) {
        return teamMembers.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(member -> member.getUserId().equals(userId)))
                .map(entry -> teams.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 添加成员到团队
     */
    public TeamMember addMember(String teamId, String userId, TeamMember.Role role) {
        if (!teams.containsKey(teamId)) {
            throw new IllegalArgumentException("团队不存在: " + teamId);
        }
        
        TeamMember member = new TeamMember(userId, teamId, role);
        teamMembers.computeIfAbsent(teamId, k -> new ArrayList<>()).add(member);
        saveData();
        return member;
    }
    
    /**
     * 从团队中移除成员
     */
    public boolean removeMember(String teamId, String userId, String requesterId) {
        if (!teams.containsKey(teamId)) {
            return false;
        }
        
        // 检查权限
        Team team = teams.get(teamId);
        boolean isOwner = team.getOwnerId().equals(requesterId);
        boolean isSelf = userId.equals(requesterId);
        
        if (!isOwner && !isSelf) {
            throw new IllegalStateException("没有权限移除成员");
        }
        
        // 不能移除owner
        if (userId.equals(team.getOwnerId())) {
            throw new IllegalStateException("不能移除团队所有者");
        }
        
        List<TeamMember> members = teamMembers.get(teamId);
        if (members != null) {
            boolean removed = members.removeIf(m -> m.getUserId().equals(userId));
            if (removed) {
                saveData();
            }
            return removed;
        }
        return false;
    }
    
    /**
     * 更新成员角色
     */
    public boolean updateMemberRole(String teamId, String userId, TeamMember.Role newRole, String requesterId) {
        if (!teams.containsKey(teamId)) {
            return false;
        }
        
        // 检查权限（只有管理员可以修改角色）
        TeamMember requester = getMember(teamId, requesterId);
        if (requester == null || !requester.canManageMembers()) {
            throw new IllegalStateException("没有权限修改成员角色");
        }
        
        Team team = teams.get(teamId);
        if (userId.equals(team.getOwnerId()) && newRole != TeamMember.Role.ADMIN) {
            throw new IllegalStateException("不能修改团队所有者的角色");
        }
        
        List<TeamMember> members = teamMembers.get(teamId);
        if (members != null) {
            for (TeamMember member : members) {
                if (member.getUserId().equals(userId)) {
                    member.setRole(newRole);
                    saveData();
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 获取团队成员
     */
    public List<TeamMember> getMembers(String teamId) {
        return new ArrayList<>(teamMembers.getOrDefault(teamId, new ArrayList<>()));
    }
    
    /**
     * 获取特定成员
     */
    public TeamMember getMember(String teamId, String userId) {
        return teamMembers.getOrDefault(teamId, new ArrayList<>()).stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 检查用户是否是团队成员
     */
    public boolean isMember(String teamId, String userId) {
        return getMember(teamId, userId) != null;
    }
    
    /**
     * 更新团队设置
     */
    public boolean updateTeamSettings(String teamId, Map<String, String> settings, String userId) {
        Team team = teams.get(teamId);
        if (team == null) {
            return false;
        }
        
        TeamMember member = getMember(teamId, userId);
        if (member == null || !member.canManageSettings()) {
            throw new IllegalStateException("没有权限修改团队设置");
        }
        
        team.setSettings(settings);
        saveData();
        return true;
    }
    
    /**
     * 生成唯一团队ID
     */
    private String generateTeamId() {
        return "team_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * 加载数据
     */
    private void loadData() {
        try {
            if (Files.exists(teamsFilePath)) {
                String content = Files.readString(teamsFilePath);
                List<Team> teamList = objectMapper.readValue(content, new TypeReference<List<Team>>() {});
                teamList.forEach(t -> teams.put(t.getId(), t));
            }
            
            if (Files.exists(membersFilePath)) {
                String content = Files.readString(membersFilePath);
                Map<String, List<TeamMember>> membersMap = objectMapper.readValue(
                        content, new TypeReference<Map<String, List<TeamMember>>>() {});
                // 过滤 _comment 等非 List 字段，避免反序列化失败
                membersMap.entrySet().removeIf(e -> !(e.getValue() instanceof List));
                teamMembers.putAll(membersMap);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load team data: " + e.getMessage());
        }
    }
    
    /**
     * 保存数据
     */
    private void saveData() {
        try {
            Files.createDirectories(dataDirectory);
            
            String teamsContent = objectMapper.writeValueAsString(new ArrayList<>(teams.values()));
            Files.writeString(teamsFilePath, teamsContent);
            
            String membersContent = objectMapper.writeValueAsString(teamMembers);
            Files.writeString(membersFilePath, membersContent);
        } catch (IOException e) {
            LOGGER.warning("Failed to save team data: " + e.getMessage());
        }
    }
    
    /**
     * 重新加载数据
     */
    public void reload() {
        teams.clear();
        teamMembers.clear();
        loadData();
    }
}
