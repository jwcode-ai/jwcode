package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.TaskGetInput;
import com.jwcode.core.tool.output.TaskGetOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * TaskGet 工具 - 获取任务详情
 * 
 * 根据任务ID查询完整任务信息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskGetTool implements Tool<TaskGetInput, TaskGetOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskGetTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    private final TaskStore taskStore;
    
    public TaskGetTool() {
        this.taskStore = TaskStore.getInstance();
    }
    
    public TaskGetTool(TaskStore taskStore) {
        this.taskStore = taskStore;
    }
    
    @Override
    public String getName() {
        return "TaskGet";
    }
    
    @Override
    public String getDescription() {
        return "获取任务详情。根据任务ID查询完整任务信息。";
    }
    
    @Override
    public String getPrompt() {
        return """
               TaskGet — 获取单个任务的详细信息。

               当你需要查看某个任务的完整详情（包括描述、标签、时间线等）时使用。
               TaskList 返回概览摘要，TaskGet 返回完整详情。

               参数:
               - id: 任务ID（必需）

               示例:
               - {"id": "task-xxx"} - 获取指定任务的详细信息
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string", "description": "任务ID"}
                    },
                    "required": ["id"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<TaskGetInput> getInputType() {
        return new TypeReference<TaskGetInput>() {};
    }
    
    @Override
    public TypeReference<TaskGetOutput> getOutputType() {
        return new TypeReference<TaskGetOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<TaskGetOutput>> call(
            TaskGetInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Task task = taskStore.get(input.id());
                
                if (task == null) {
                    logger.warn("Task not found: {}", input.id());
                    return ToolResult.error("任务不存在: " + input.id());
                }
                
                logger.debug("Task retrieved: {}", task.getId());
                
                // 构建详细消息
                StringBuilder content = new StringBuilder();
                content.append("任务详情\n");
                content.append("=" .repeat(40)).append("\n\n");
                content.append("任务ID: ").append(task.getId()).append("\n");
                content.append("标题: ").append(task.getTitle()).append("\n");
                
                if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                    content.append("描述: ").append(task.getDescription()).append("\n");
                }
                
                content.append("状态: ").append(task.getStatus()).append("\n");
                content.append("优先级: ").append(task.getPriority()).append("\n");
                content.append("进度: ").append(task.getProgress()).append("%\n");
                
                if (task.getTags() != null && !task.getTags().isEmpty()) {
                    content.append("标签: ").append(String.join(", ", task.getTags())).append("\n");
                }
                
                if (task.isSubTask()) {
                    content.append("父任务ID: ").append(task.getParentId()).append("\n");
                }
                
                content.append("\n");
                content.append("创建时间: ").append(task.getCreatedAt()).append("\n");
                content.append("更新时间: ").append(task.getUpdatedAt()).append("\n");
                
                if (task.getStartedAt() != null) {
                    content.append("开始时间: ").append(task.getStartedAt()).append("\n");
                }
                
                if (task.getCompletedAt() != null) {
                    content.append("完成时间: ").append(task.getCompletedAt()).append("\n");
                }
                
                long duration = task.getDurationMillis();
                if (duration > 0) {
                    Duration d = Duration.ofMillis(duration);
                    content.append("持续时间: ")
                           .append(d.toMinutes()).append(" 分 ")
                           .append(d.toSecondsPart()).append(" 秒\n");
                }
                
                TaskGetOutput output = TaskGetOutput.fromTask(task);
                
                ToolResult<TaskGetOutput> result = ToolResult.success(output);
                result.setContent(content.toString());
                result.setMetadata(java.util.Map.of(
                    "taskId", task.getId(),
                    "status", task.getStatus().name()
                ));
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to get task", e);
                return ToolResult.error("获取任务失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(TaskGetInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        if (input.id() == null || input.id().trim().isEmpty()) {
            return ToolValidationResult.invalid("id 是必需的");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TaskGetInput input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(TaskGetInput input) {
        return true;
    }
}
