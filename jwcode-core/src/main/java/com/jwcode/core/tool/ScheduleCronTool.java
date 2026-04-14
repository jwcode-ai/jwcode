package com.jwcode.core.tool;

import com.jwcode.core.tool.input.CronInput;
import com.jwcode.core.tool.output.CronOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
                // 根据参数判断操作类型
                if (input.id() != null && !input.id().isEmpty()) {
                    // 删除或列出单个任务
                    return handleDelete(input);
                } else if (input.cronExpression() != null) {
                    // 创建任务
                    return handleCreate(input);
                } else {
                    // 列出所有任务
                    return handleList();
                }
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
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(CronInput input) {
        return input.id() != null && input.cronExpression() == null;
    }
    
    @Override
    public boolean isDestructive(CronInput input) {
        return input.id() != null && input.cronExpression() == null;
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