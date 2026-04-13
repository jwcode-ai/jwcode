package com.jwcode.core.team;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 团队成员模型
 * 表示用户在团队中的成员关系
 */
public class TeamMember {
    
    public enum Role {
        ADMIN,    // 管理员 - 可以管理团队设置和成员
        MEMBER,   // 成员 - 可以参与协作
        GUEST     // 访客 - 只读访问
    }
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("teamId")
    private String teamId;
    
    @JsonProperty("role")
    private Role role;
    
    @JsonProperty("joinedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime joinedAt;
    
    public TeamMember() {
        this.role = Role.MEMBER;
        this.joinedAt = LocalDateTime.now();
    }
    
    public TeamMember(String userId, String teamId, Role role) {
        this();
        this.userId = userId;
        this.teamId = teamId;
        this.role = role;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getTeamId() {
        return teamId;
    }
    
    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    /**
     * 检查是否有管理员权限
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
    
    /**
     * 检查是否可以管理成员
     */
    public boolean canManageMembers() {
        return role == Role.ADMIN;
    }
    
    /**
     * 检查是否可以修改团队设置
     */
    public boolean canManageSettings() {
        return role == Role.ADMIN;
    }
    
    /**
     * 检查是否可以删除团队
     */
    public boolean canDeleteTeam() {
        return role == Role.ADMIN;
    }
    
    /**
     * 检查是否有写权限
     */
    public boolean canWrite() {
        return role == Role.ADMIN || role == Role.MEMBER;
    }
    
    /**
     * 检查是否有读权限
     */
    public boolean canRead() {
        return true; // 所有角色都有读权限
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamMember that = (TeamMember) o;
        return Objects.equals(userId, that.userId) && Objects.equals(teamId, that.teamId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, teamId);
    }
    
    @Override
    public String toString() {
        return "TeamMember{" +
                "userId='" + userId + '\'' +
                ", teamId='" + teamId + '\'' +
                ", role=" + role +
                ", joinedAt=" + joinedAt +
                '}';
    }
}
