package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import com.jwcode.core.session.Session;
import com.jwcode.core.service.ToolExecutionService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * SkillManager - 技能管理器
 * 
 * 功能说明：
 * 负责技能的加载、验证和执行管理。
 * 支持本地技能和远程技能的加载，处理技能参数的解析和验证。
 * 
 * 上下文关系：
 * - 被 SkillTool 调用
 * - 与 SkillRegistry 协作管理技能
 * - 与 ToolExecutionService 协作执行技能
 * - 支持分叉 Agent 执行技能
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SkillManager {
    
    /**
     * 技能注册表
     */
    private final SkillRegistry skillRegistry;
    
    /**
     * 工具执行服务
     */
    private final ToolExecutionService toolExecutionService;
    
    /**
     * HTTP 客户端（用于远程技能加载）
     */
    private final HttpClient httpClient;
    
    /**
     * 远程技能缓存
     */
    private final Map<String, SkillRegistry.SkillDefinition> remoteSkillCache;
    
    /**
     * 技能执行历史记录
     */
    private final List<SkillExecutionRecord> executionHistory;
    
    /**
     * 最大历史记录数量
     */
    private static final int MAX_HISTORY_SIZE = 100;
    
    /**
     * 构造函数
     * 
     * @param skillRegistry 技能注册表
     * @param toolExecutionService 工具执行服务
     */
    public SkillManager(SkillRegistry skillRegistry, 
                        ToolExecutionService toolExecutionService) {
        this.skillRegistry = skillRegistry;
        this.toolExecutionService = toolExecutionService;
        this.httpClient = HttpClient.newHttpClient();
        this.remoteSkillCache = new ConcurrentHashMap<>();
        this.executionHistory = new ArrayList<>();
    }
    
    /**
     * 构造函数（简化版，用于测试）
     * 
     * @param skillRegistry 技能注册表
     */
    public SkillManager(SkillRegistry skillRegistry) {
        this(skillRegistry, null);
    }
    
    /**
     * 获取技能注册表
     * 
     * @return 技能注册表
     */
    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }
    
    /**
     * 验证技能名称
     * 
     * @param skillName 技能名称
     * @return 验证结果
     */
    public ValidationResult validateSkillName(String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return ValidationResult.error("技能名称不能为空");
        }
        
        // 去除前导斜杠
        String cleanName = skillName.startsWith("/") ? skillName.substring(1) : skillName;
        
        // 检查是否在注册表中
        if (skillRegistry.contains(cleanName)) {
            return ValidationResult.valid(cleanName);
        }
        
        // 检查是否是远程技能
        if (remoteSkillCache.containsKey(cleanName)) {
            return ValidationResult.valid(cleanName);
        }
        
        return ValidationResult.error("未找到技能：" + skillName);
    }
    
    /**
     * 验证技能参数
     * 
     * @param skillName 技能名称
     * @param params 技能参数
     * @return 验证结果
     */
    public ValidationResult validateParameters(String skillName, Map<String, Object> params) {
        SkillRegistry.SkillDefinition skill = skillRegistry.findByName(skillName)
                .or(() -> Optional.ofNullable(remoteSkillCache.get(skillName)))
                .orElse(null);
        
        if (skill == null) {
            return ValidationResult.error("技能不存在：" + skillName);
        }
        
        Map<String, Object> paramDefs = skill.getParameters();
        List<String> errors = new ArrayList<>();
        
        // 检查必需参数
        for (Map.Entry<String, Object> entry : paramDefs.entrySet()) {
            String paramName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> paramDef = (Map<String, Object>) entry.getValue();
            
            Boolean required = (Boolean) paramDef.get("required");
            if (Boolean.TRUE.equals(required) && (params == null || !params.containsKey(paramName))) {
                errors.add("缺少必需参数：" + paramName);
            }
        }
        
        // 检查参数类型
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> paramDef = (Map<String, Object>) paramDefs.get(paramName);
                
                if (paramDef != null) {
                    String expectedType = (String) paramDef.get("type");
                    if (!validateType(paramValue, expectedType)) {
                        errors.add("参数 " + paramName + " 类型错误，期望：" + expectedType);
                    }
                }
            }
        }
        
        if (errors.isEmpty()) {
            return ValidationResult.valid();
        } else {
            return ValidationResult.error(String.join("; ", errors));
        }
    }
    
    /**
     * 验证参数类型
     */
    private boolean validateType(Object value, String expectedType) {
        if (value == null) {
            return true; // null 值由必需性检查处理
        }
        
        switch (expectedType) {
            case "string":
                return value instanceof String;
            case "integer":
            case "int":
                return value instanceof Integer;
            case "number":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "array":
                return value instanceof List;
            case "object":
                return value instanceof Map;
            default:
                return true; // 未知类型默认通过
        }
    }
    
    /**
     * 检查技能执行权限
     * 
     * @param skillName 技能名称
     * @param session 会话
     * @param canUseToolFn 权限检查函数
     * @return 权限检查结果
     */
    public CanUseToolFn.PermissionResult checkPermissions(String skillName, Session session, 
                                             CanUseToolFn canUseToolFn) {
        if (canUseToolFn == null) {
            return CanUseToolFn.PermissionResult.allow();
        }
        
        return canUseToolFn.check("SkillTool", Map.of("skill", skillName));
    }
    
    /**
     * 执行技能（内联模式）
     * 
     * @param skillName 技能名称
     * @param params 技能参数
     * @param session 会话
     * @param onProgress 进度回调
     * @return 执行结果
     */
    public CompletableFuture<ToolResult<String>> executeSkill(
            String skillName,
            Map<String, Object> params,
            Session session,
            Consumer<ToolProgress<String>> onProgress) {
        
        CompletableFuture<ToolResult<String>> future = new CompletableFuture<>();
        
        // 记录执行开始
        long startTime = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();
        
        if (onProgress != null) {
            onProgress.accept(ToolProgress.withMessage(executionId, "执行技能：" + skillName));
        }
        
        try {
            // 获取技能定义
            SkillRegistry.SkillDefinition skill = skillRegistry.findByName(skillName)
                    .or(() -> Optional.ofNullable(remoteSkillCache.get(skillName)))
                    .orElseThrow(() -> new NoSuchElementException("技能不存在：" + skillName));
            
            if (onProgress != null) {
                onProgress.accept(ToolProgress.withMessage(executionId, "技能验证通过"));
            }
            
            // 构建技能执行命令
            String command = buildSkillCommand(skill, params, session);
            
            if (onProgress != null) {
                onProgress.accept(ToolProgress.withMessage(executionId, "执行命令：" + command));
            }
            
            // 执行命令（通过 BashTool）
            Map<String, Object> bashArgs = new HashMap<>();
            bashArgs.put("command", command);
            bashArgs.put("description", "执行技能：" + skillName);
            
            toolExecutionService.executeTool("BashTool", bashArgs)
                    .whenComplete((result, error) -> {
                        long endTime = System.currentTimeMillis();
                        
                        // 记录执行历史
                        recordExecution(executionId, skillName, params, 
                                       result != null ? result.message : null,
                                       error, endTime - startTime);
                        
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            if (onProgress != null) {
                                onProgress.accept(ToolProgress.withMessage(executionId, "技能执行完成"));
                            }
                            // Convert ToolExecutionResult to ToolResult<String>
                            ToolResult<String> toolResult = result.success 
                                ? ToolResult.success(result.message)
                                : ToolResult.error(result.error);
                            future.complete(toolResult);
                        }
                    });
                    
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 执行技能（分叉 Agent 模式）
     * 
     * @param skillName 技能名称
     * @param params 技能参数
     * @param session 会话
     * @param forkedAgentName 分叉 Agent 名称
     * @param onProgress 进度回调
     * @return 执行结果
     */
    public CompletableFuture<ToolResult<String>> executeForkedSkill(
            String skillName,
            Map<String, Object> params,
            Session session,
            String forkedAgentName,
            Consumer<ToolProgress<String>> onProgress) {
        
        CompletableFuture<ToolResult<String>> future = new CompletableFuture<>();
        
        String executionId = UUID.randomUUID().toString();
        
        if (onProgress != null) {
            onProgress.accept(ToolProgress.withMessage(executionId, 
                    "启动分叉 Agent " + forkedAgentName + " 执行技能：" + skillName));
        }
        
        try {
            // 获取技能定义
            SkillRegistry.SkillDefinition skill = skillRegistry.findByName(skillName)
                    .or(() -> Optional.ofNullable(remoteSkillCache.get(skillName)))
                    .orElseThrow(() -> new NoSuchElementException("技能不存在：" + skillName));
            
            // 构建分叉 Agent 提示
            String prompt = buildForkedAgentPrompt(skill, params, session);
            
            if (onProgress != null) {
                onProgress.accept(ToolProgress.withMessage(executionId, 
                        "分叉 Agent 提示：" + prompt.substring(0, Math.min(100, prompt.length())) + "..."));
            }
            
            // 通过 AgentTool 执行
            Map<String, Object> agentArgs = new HashMap<>();
            agentArgs.put("name", forkedAgentName);
            agentArgs.put("prompt", prompt);
            agentArgs.put("mode", "fork");
            
            toolExecutionService.executeTool("AgentTool", agentArgs)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            // Convert ToolExecutionResult to ToolResult<String>
                            ToolResult<String> toolResult = result.success 
                                ? ToolResult.success(result.message)
                                : ToolResult.error(result.error);
                            future.complete(toolResult);
                        }
                    });
                    
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 构建技能执行命令
     */
    private String buildSkillCommand(SkillRegistry.SkillDefinition skill, Map<String, Object> params, Session session) {
        StringBuilder command = new StringBuilder();
        
        // 根据技能名称构建不同的命令
        switch (skill.getName()) {
            case "commit":
                command.append("git commit -m \"").append(escapeShell((String) params.get("message"))).append("\"");
                @SuppressWarnings("unchecked")
                List<String> files = (List<String>) params.get("files");
                if (files != null && !files.isEmpty()) {
                    command.append(" ");
                    for (String file : files) {
                        command.append(escapeShell(file)).append(" ");
                    }
                }
                break;
                
            case "review":
                command.append("echo \"代码审查：");
                @SuppressWarnings("unchecked")
                List<String> reviewFiles = (List<String>) params.get("files");
                if (reviewFiles != null) {
                    for (String file : reviewFiles) {
                        command.append("文件：").append(file).append("\\n");
                    }
                }
                command.append("\"");
                break;
                
            case "test":
                command.append("npm test");
                String pattern = (String) params.get("pattern");
                if (pattern != null) {
                    command.append(" -- ").append(escapeShell(pattern));
                }
                break;
                
            case "search":
                String query = (String) params.get("query");
                String type = (String) params.get("type");
                if ("file".equals(type)) {
                    command.append("find . -name \"*").append(escapeShell(query)).append("*\"");
                } else {
                    command.append("grep -r \"").append(escapeShell(query)).append("\" .");
                }
                break;
                
            default:
                command.append("echo \"执行技能：").append(skill.getName()).append("\"");
        }
        
        return command.toString();
    }
    
    /**
     * 构建分叉 Agent 提示
     */
    private String buildForkedAgentPrompt(SkillRegistry.SkillDefinition skill, Map<String, Object> params, Session session) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请执行以下技能：").append(skill.getName()).append("\n\n");
        prompt.append("技能描述：").append(skill.getDescription()).append("\n\n");
        
        if (params != null && !params.isEmpty()) {
            prompt.append("参数：\n");
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                prompt.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        prompt.append("\n请按照技能要求完成任务，并返回详细结果。");
        
        return prompt.toString();
    }
    
    /**
     * 从远程 URL 加载技能
     * 
     * @param url 远程 URL
     * @return 技能定义
     * @throws IOException IO 异常
     * @throws InterruptedException 中断异常
     */
    public SkillRegistry.SkillDefinition loadRemoteSkill(String url) throws IOException, InterruptedException {
        // 检查缓存
        if (remoteSkillCache.containsKey(url)) {
            return remoteSkillCache.get(url);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("加载远程技能失败，HTTP 状态码：" + response.statusCode());
        }
        
        // 解析 JSON 响应（简化实现）
        String json = response.body();
        SkillRegistry.SkillDefinition skill = parseSkillFromJson(json);
        
        // 缓存技能
        remoteSkillCache.put(skill.getName(), skill);
        
        return skill;
    }
    
    /**
     * 从 JSON 解析技能定义（简化实现）
     */
    private SkillRegistry.SkillDefinition parseSkillFromJson(String json) {
        // 简化实现，实际应该使用 JSON 库解析
        String name = extractJsonValue(json, "name");
        String description = extractJsonValue(json, "description");
        String category = extractJsonValue(json, "category");
        
        return SkillRegistry.SkillDefinition.builder()
                .name(name != null ? name : "unknown")
                .description(description != null ? description : "远程技能")
                .category(category != null ? category : "remote")
                .build();
    }
    
    /**
     * 从 JSON 提取字符串值（简化实现）
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 转义 Shell 特殊字符
     */
    private String escapeShell(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\"", "\\\"")
                  .replace("$", "\\$")
                  .replace("`", "\\`")
                  .replace("!", "\\!");
    }
    
    /**
     * 记录技能执行历史
     */
    private void recordExecution(String executionId, String skillName, Map<String, Object> params,
                                 Object result, Throwable error, long durationMs) {
        SkillExecutionRecord record = new SkillExecutionRecord(
                executionId, skillName, params, result, error, durationMs);
        
        executionHistory.add(record);
        
        // 限制历史记录大小
        while (executionHistory.size() > MAX_HISTORY_SIZE) {
            executionHistory.remove(0);
        }
    }
    
    /**
     * 获取技能执行历史
     * 
     * @param limit 限制数量
     * @return 执行历史记录
     */
    public List<SkillExecutionRecord> getExecutionHistory(int limit) {
        int size = executionHistory.size();
        if (limit >= size) {
            return new ArrayList<>(executionHistory);
        }
        return new ArrayList<>(executionHistory.subList(size - limit, size));
    }
    
    /**
     * 清除执行历史
     */
    public void clearExecutionHistory() {
        executionHistory.clear();
    }
    
    /**
     * 技能执行记录类
     */
    public static class SkillExecutionRecord {
        private final String executionId;
        private final String skillName;
        private final Map<String, Object> params;
        private final Object result;
        private final Throwable error;
        private final long durationMs;
        private final Date timestamp;
        
        public SkillExecutionRecord(String executionId, String skillName, Map<String, Object> params,
                                    Object result, Throwable error, long durationMs) {
            this.executionId = executionId;
            this.skillName = skillName;
            this.params = params != null ? new HashMap<>(params) : null;
            this.result = result;
            this.error = error;
            this.durationMs = durationMs;
            this.timestamp = new Date();
        }
        
        public String getExecutionId() {
            return executionId;
        }
        
        public String getSkillName() {
            return skillName;
        }
        
        public Map<String, Object> getParams() {
            return params;
        }
        
        public Object getResult() {
            return result;
        }
        
        public Throwable getError() {
            return error;
        }
        
        public long getDurationMs() {
            return durationMs;
        }
        
        public Date getTimestamp() {
            return timestamp;
        }
        
        public boolean isSuccess() {
            return error == null;
        }
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult valid(String skillName) {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
    
    /**
     * 权限结果类（使用 CanUseToolFn.PermissionResult）
     */
    public static class PermissionResult extends CanUseToolFn.PermissionResult {
        
        public PermissionResult(CanUseToolFn.Behavior behavior, String reason) {
            super(behavior, reason);
        }
        
        public static PermissionResult allow() {
            return new PermissionResult(CanUseToolFn.Behavior.ALLOW, null);
        }
        
        public static PermissionResult deny(String reason) {
            return new PermissionResult(CanUseToolFn.Behavior.DENY, reason);
        }
        
        public static PermissionResult askUser(String reason) {
            return new PermissionResult(CanUseToolFn.Behavior.ASK_USER, reason);
        }
    }
}