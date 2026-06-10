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
    Boolean requireApproval,
    
    /** 是否作为后台任务执行（OS级进程隔离），用于长时任务如 mvn clean install */
    @JsonProperty("background")
    Boolean background,

    /** 策略上下文 — 命令类别/意图提示，供 ExecPolicyEngine 做更精准的判断 */
    @JsonProperty("policy_context")
    String policyContext
) {
    
    public BashInput {
        if (timeout == null) {
            timeout = 600000; // 默认 10 分钟
        }
        if (requireApproval == null) {
            requireApproval = true;
        }
        if (background == null) {
            background = false;
        }
    }
    
    public BashInput(String command) {
        this(command, null, null, null, null, null, null, null);
    }

    public BashInput(String command, String description) {
        this(command, description, null, null, null, null, null, null);
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
     * 是否应作为后台任务执行（OS 级进程隔离）
     */
    public boolean isBackground() {
        return background != null && background;
    }
    
    /**
     * 检查是否是危险命令（含自毁命令）
     */
    public boolean isDangerousCommand() {
        if (command == null) return false;
        
        String[] dangerousPatterns = {
            // Unix 危险模式
            "rm -rf /", "rm -rf /*", "dd if=/dev/zero",
            ":(){ :|:& };:", "> /dev/sda", "mkfs.",
            "chmod -R 777 /", "chown -R", "mv /* /dev/null",
            // Windows 自毁模式 —— 会杀死 JWCode 自身所在的 JVM 进程
            "taskkill /f /im java",        // taskkill /F /IM java.exe
            "taskkill /f /im javaw",       // taskkill /F /IM javaw.exe
            "taskkill /f /im jp2launcher", // Eclipse/IDE launcher
            "stop-process -name java",     // PowerShell: Stop-Process -Name java
            "stop-process -name javaw",    // PowerShell: Stop-Process -Name javaw
            "stop-process java",           // PowerShell 缩写
            "kill -9",                     // Unix: 强制杀进程（可能杀自己）
            "killall java",                // Unix: 杀所有 Java
            "pkill java",                  // Unix: 杀所有 Java
        };
        
        String lowerCmd = command.toLowerCase();
        for (String pattern : dangerousPatterns) {
            if (lowerCmd.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        // 额外检测：taskkill 通过 PID 杀进程（可能是当前进程）
        if (lowerCmd.contains("taskkill") && lowerCmd.contains("/pid")) {
            return true;
        }
        
        // 检测 wmic process delete（Windows 进程删除）
        if (lowerCmd.contains("wmic") && lowerCmd.contains("process") && 
            (lowerCmd.contains("delete") || lowerCmd.contains("call terminate"))) {
            return true;
        }
        
        // 检测 PowerShell 杀进程的更多变体
        if ((lowerCmd.contains("get-process") || lowerCmd.contains("ps ")) && 
            (lowerCmd.contains("stop-process") || lowerCmd.contains("kill"))) {
            return true;
        }
        
        return false;
    }
}
