package com.jwcode.core.team;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 团队模型
 * 表示一个协作团队的基本信息
 */
public class Team {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonProperty("ownerId")
    private String ownerId;
    
    @JsonProperty("settings")
    private Map<String, String> settings;
    
    public Team() {
        this.settings = new HashMap<>();
        this.createdAt = LocalDateTime.now();
    }
    
    public Team(String id, String name, String ownerId) {
        this();
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    
    public Map<String, String> getSettings() {
        return settings;
    }
    
    public void setSettings(Map<String, String> settings) {
        this.settings = settings != null ? settings : new HashMap<>();
    }
    
    public void addSetting(String key, String value) {
        this.settings.put(key, value);
    }
    
    public String getSetting(String key) {
        return this.settings.get(key);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return Objects.equals(id, team.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Team{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", ownerId='" + ownerId + '\'' +
                ", settings=" + settings +
                '}';
    }
}
