package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ToolContext - 工具执行上下文
 * 
 * 功能说明：
 * 提供工具执行所需的环境信息和辅助功能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ToolContext {
    
    private final String workingDirectory;
    private final Map<String, Object> environment;
    private final String sessionId;
    private final boolean debug;
    
    public ToolContext(String workingDirectory, Map<String, Object> environment, String sessionId) {
        this(workingDirectory, environment, sessionId, false);
    }
    
    public ToolContext(String workingDirectory, Map<String, Object> environment, String sessionId, boolean debug) {
        this.workingDirectory = workingDirectory;
        this.environment = environment;
        this.sessionId = sessionId;
        this.debug = debug;
    }
    
    public String getWorkingDirectory() {
        return workingDirectory;
    }
    
    public Path getWorkingDirectoryPath() {
        return Path.of(workingDirectory);
    }
    
    public Map<String, Object> getEnvironment() {
        return environment;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public String getUserId() {
        // 从环境信息中获取用户 ID
        return environment != null ? (String) environment.get("userId") : null;
    }
    
    public boolean isInteractive() {
        return true; // 默认交互式
    }
    
    public Object getEnv(String key) {
        return environment != null ? environment.get(key) : null;
    }
    
    public static ToolContext current() {
        return new ToolContext(
            System.getProperty("user.dir"),
            new HashMap<>(System.getenv()),
            null
        );
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String workingDirectory = System.getProperty("user.dir");
        private Map<String, Object> environment = new HashMap<>();
        private String sessionId;
        private boolean debug = false;
        
        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }
        
        public Builder environment(Map<String, Object> environment) {
            this.environment = environment;
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }
        
        public ToolContext build() {
            return new ToolContext(workingDirectory, environment, sessionId, debug);
        }
    }
}
