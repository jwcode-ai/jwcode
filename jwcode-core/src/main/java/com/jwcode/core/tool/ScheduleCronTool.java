package com.jwcode.core.tool;

import com.jwcode.core.tool.input.CronInput;
import com.jwcode.core.tool.output.CronOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cron 定时任务管理工具
 * 支持创建、删除和列出定时任务
 */
public class ScheduleCronTool implements Tool<CronInput, CronOutput, CronOutput.CronJobInfo> {
    
    private static final String CRON_FILE = ".jwcode/crons.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path cronFilePath;
    private final Map<String, ScheduledExecutorService> scheduledTasks;
    private final ScheduledExecutorService scheduler;
    
    public ScheduleCronTool() {
        this(Paths.get(System.getProperty("user.dir")));
    }
    
    public ScheduleCronTool(Path workingDirectory) {
        this.cronFilePath = workingDirectory.resolve(CRON_FILE);
        this.scheduledTasks = new HashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    @Override
    public String getName() {
        return "ScheduleCron";
    }
    
    @Override
    public String getDescription() {
        return "Create, delete, and list scheduled cron jobs that run commands at specific times.";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "action": {
                      "type": "string",
                      "enum": ["create", "delete", "list"],
                      "description": "操作类型: create=创建定时任务, delete=删除定时任务, list=列出所有任务"
                    },
                    "id": {
                      "type": "string",
                      "description": "任务ID（delete操作必填）"
                    },
                    "cronExpression": {
                      "type": "string",
                      "description": "cron表达式，5-6个字段，如 '0 9 * * *'（create操作必填）"
                    },
                    "command": {
                      "type": "string",
                      "description": "要执行的命令（create操作必填）"
                    },
                    "description": {
                      "type": "string",
                      "description": "任务描述（可选）"
                    }
                  },
                  "required": ["action"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool to create, delete, and list scheduled cron jobs. " +
               "The tool operates on a file at the root of your project called .jwcode/crons.json. " +
               "Each job has an ID, cron expression, command, and description.";
    }
    
    @Override
    public CompletableFuture<ToolResult<CronOutput>> call(
            CronInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<CronOutput.CronJobInfo>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 根据 action 参数判断操作类型
                String action = input.action() != null ? input.action().trim().toLowerCase() : "";
                return switch (action) {
                    case "create" -> handleCreate(input);
                    case "delete" -> handleDelete(input);
                    case "list", "" -> handleList();
                    default -> ToolResult.error("未知操作类型: " + action + "，支持: create / delete / list");
                };
            } catch (Exception e) {
                return ToolResult.error("操作失败: " + e.getMessage());
            }
        });
    }
    
    private ToolResult<CronOutput> handleCreate(CronInput input) throws Exception {
        // 验证 cron 表达式
        if (!isValidCronExpression(input.cronExpression())) {
            return ToolResult.error("无效的 cron 表达式: " + input.cronExpression());
        }
        
        // 读取现有任务
        List<CronOutput.CronJobInfo> jobs = loadJobs();
        
        // 生成新任务 ID
        String id = UUID.randomUUID().toString().substring(0, 8);
        
        // 创建任务信息
        CronOutput.CronJobInfo jobInfo = new CronOutput.CronJobInfo(
                id,
                input.cronExpression(),
                input.command(),
                input.description() != null ? input.description() : ""
        );
        
        // 添加到列表
        jobs.add(jobInfo);
        
        // 保存
        saveJobs(jobs);
        
        return ToolResult.success(CronOutput.success(id, "已创建定时任务: " + id));
    }
    
    private ToolResult<CronOutput> handleDelete(CronInput input) throws Exception {
        List<CronOutput.CronJobInfo> jobs = loadJobs();
        
        Optional<CronOutput.CronJobInfo> jobToDelete = jobs.stream()
                .filter(j -> j.id().equals(input.id()))
                .findFirst();
        
        if (jobToDelete.isEmpty()) {
            return ToolResult.error("未找到任务: " + input.id());
        }
        
        // 移除任务
        jobs.removeIf(j -> j.id().equals(input.id()));
        saveJobs(jobs);
        
        return ToolResult.success(CronOutput.success(input.id(), "已删除定时任务: " + input.id()));
    }
    
    private ToolResult<CronOutput> handleList() throws Exception {
        List<CronOutput.CronJobInfo> jobs = loadJobs();
        return ToolResult.success(CronOutput.list(jobs));
    }
    
    private List<CronOutput.CronJobInfo> loadJobs() throws Exception {
        if (Files.exists(cronFilePath)) {
            String content = Files.readString(cronFilePath);
            // 简单解析 JSON（实际使用 Jackson）
            return parseJobsFromJson(content);
        }
        return new ArrayList<>();
    }
    
    private void saveJobs(List<CronOutput.CronJobInfo> jobs) throws Exception {
        Files.createDirectories(cronFilePath.getParent());
        String json = jobsToJson(jobs);
        Files.writeString(cronFilePath, json);
    }
    
    private boolean isValidCronExpression(String cron) {
        // 简单的验证：检查是否包含 5 个字段
        String[] parts = cron.trim().split("\\s+");
        return parts.length >= 5 && parts.length <= 6;
    }
    
    private List<CronOutput.CronJobInfo> parseJobsFromJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return MAPPER.readValue(json, new TypeReference<List<CronOutput.CronJobInfo>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    private String jobsToJson(List<CronOutput.CronJobInfo> jobs) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jobs);
        } catch (Exception e) {
            return "[]";
        }
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<CronInput> getInputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<CronOutput> getOutputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(CronInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        String action = input.action() != null ? input.action().trim().toLowerCase() : "list";
        return switch (action) {
            case "create" -> {
                if (input.cronExpression() == null || input.cronExpression().isBlank())
                    yield ToolValidationResult.invalid("create 操作需要 cronExpression 参数");
                if (input.command() == null || input.command().isBlank())
                    yield ToolValidationResult.invalid("create 操作需要 command 参数");
                if (!isValidCronExpression(input.cronExpression()))
                    yield ToolValidationResult.invalid("无效的 cron 表达式: " + input.cronExpression());
                yield ToolValidationResult.valid();
            }
            case "delete" -> {
                if (input.id() == null || input.id().isBlank())
                    yield ToolValidationResult.invalid("delete 操作需要 id 参数");
                yield ToolValidationResult.valid();
            }
            case "list" -> ToolValidationResult.valid();
            default -> ToolValidationResult.invalid("未知操作类型: " + action + "，支持: create / delete / list");
        };
    }
    
    @Override
    public boolean isReadOnly(CronInput input) {
        if (input == null) return true;
        String action = input.action() != null ? input.action().trim().toLowerCase() : "list";
        return action.equals("list");
    }
    
    @Override
    public boolean isDestructive(CronInput input) {
        if (input == null) return false;
        String action = input.action() != null ? input.action().trim().toLowerCase() : "";
        return action.equals("delete");
    }
    
    @Override
    public boolean requiresApproval(CronInput input) {
        return input.command() != null && (
                input.command().contains("rm") || 
                input.command().contains("del") ||
                input.command().contains("format")
        );
    }
}