package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * BashTool 的输入参数
 * 
 * 对标 JavaScript 项目的 BashTool input schema
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BashInput(
    @NotBlank(message = "command 是必需的")
    @JsonProperty("command")
    String command,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("timeout")
    Integer timeout,
    
    @JsonProperty("cwd")
    String cwd,
    
    @JsonProperty("env")
    Map<String, String> env,
    
    @JsonProperty("require_approval")
    Boolean requireApproval
) {
    
    public BashInput {
        if (timeout == null) {
            timeout = 600000; // 默认 10 分钟
        }
        if (requireApproval == null) {
            requireApproval = true;
        }
    }
    
    public BashInput(String command) {
        this(command, null, null, null, null, null);
    }
    
    public BashInput(String command, String description) {
        this(command, description, null, null, null, null);
    }
    
    /**
     * 获取超时时间（毫秒）
     */
    public long getTimeoutMillis() {
        return timeout != null ? timeout : 600000;
    }
    
    /**
     * 检查是否需要用户确认
     */
    public boolean requiresApproval() {
        return requireApproval != null && requireApproval;
    }
    
    /**
     * 检查是否是危险命令
     */
    public boolean isDangerousCommand() {
        if (command == null) return false;
        
        String[] dangerousPatterns = {
            "rm -rf /", "rm -rf /*", "dd if=/dev/zero",
            ":(){ :|:& };:", "> /dev/sda", "mkfs.",
            "chmod -R 777 /", "chown -R", "mv /* /dev/null"
        };
        
        String lowerCmd = command.toLowerCase();
        for (String pattern : dangerousPatterns) {
            if (lowerCmd.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
