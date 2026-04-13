package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskScheduler;
import com.jwcode.core.task.TaskStatus;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.TaskStopInput;
import com.jwcode.core.tool.output.TaskStopOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * TaskStop 工具 - 停止任务
 * 
 * 停止运行中的任务，清理相关资源。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskStopTool implements Tool<TaskStopInput, TaskStopOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskStopTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final TaskStore taskStore;
    private final TaskScheduler taskScheduler;
    
    public TaskStopTool() {
        this.taskStore = TaskStore.getInstance();
        this.taskScheduler = TaskScheduler.getInstance();
    }
    
    public TaskStopTool(TaskStore taskStore, TaskScheduler taskScheduler) {
        this.taskStore = taskStore;
        this.taskScheduler = taskScheduler;
    }
    
    @Override
    public String getName() {
        return "TaskStop";
    }
    
    @Override
    public String getDescription() {
        return "停止任务。停止运行中的任务，清理相关资源。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 TaskStop 工具停止运行中的任务。
               
               参数:
               - id: 任务ID（必需）
               - force: 是否强制停止（可选，默认 true）
               
               示例:
               - {"id": "task-xxx"} - 停止指定任务
               - {"id": "task-xxx", "force": false} - 尝试优雅停止
               
               注意:
               - 只能停止状态为 PENDING 或 RUNNING 的任务
               - 已完成的任务无法停止
               - 停止后任务状态将变为 STOPPED
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string", "description": "任务ID"},
                        "force": {"type": "boolean", "description": "是否强制停止", "default": true}
                    },
                    "required": ["id"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<TaskStopInput> getInputType() {
        return new TypeReference<TaskStopInput>() {};
    }
    
    @Override
    public TypeReference<TaskStopOutput> getOutputType() {
        return new TypeReference<TaskStopOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<TaskStopOutput>> call(
            TaskStopInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Task task = taskStore.get(input.id());
                
                if (task == null) {
                    logger.warn("Task not found: {}", input.id());
                    return ToolResult.error("任务不存在: " + input.id());
                }
                
                TaskStatus previousStatus = task.getStatus();
                
                // 检查任务是否已经在完成状态
                if (previousStatus.isFinished()) {
                    String msg = "任务已经是 " + previousStatus.getDescription() + " 状态，无需停止";
                    logger.warn("Cannot stop {} task: {}", previousStatus, task.getId());
                    return ToolResult.error(msg);
                }
                
                // 尝试通过调度器停止任务
                boolean cancelled = taskScheduler.cancel(task.getId());
                
                if (cancelled) {
                    // 如果任务正在运行，调度器会更新状态
                    task = taskStore.get(task.getId());
                    logger.info("Task stopped: {}", task.getId());
                } else {
                    // 任务未在调度器中运行，直接更新状态
                    task.markStopped();
                    taskStore.update(task);
                    logger.info("Task marked as stopped: {}", task.getId());
                }
                
                // 构建消息
                StringBuilder content = new StringBuilder();
                content.append("任务已停止\n");
                content.append("=" .repeat(40)).append("\n\n");
                content.append("任务ID: ").append(task.getId()).append("\n");
                content.append("任务标题: ").append(task.getTitle()).append("\n");
                content.append("之前状态: ").append(previousStatus.getDescription()).append("\n");
                content.append("当前状态: ").append(task.getStatus().getDescription()).append("\n");
                
                if (previousStatus == TaskStatus.RUNNING) {
                    long duration = task.getDurationMillis();
                    if (duration > 0) {
                        content.append("运行时间: ")
                               .append(duration / 1000)
                               .append(" 秒\n");
                    }
                }
                
                TaskStopOutput output = TaskStopOutput.success(
                    task.getId(),
                    previousStatus.name(),
                    content.toString()
                );
                
                ToolResult<TaskStopOutput> result = ToolResult.success(output);
                result.setContent(content.toString());
                result.setMetadata(java.util.Map.of(
                    "taskId", task.getId(),
                    "previousStatus", previousStatus.name(),
                    "currentStatus", task.getStatus().name()
                ));
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to stop task", e);
                return ToolResult.error("停止任务失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(TaskStopInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        if (input.id() == null || input.id().trim().isEmpty()) {
            return ToolValidationResult.invalid("id 是必需的");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TaskStopInput input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(TaskStopInput input) {
        return true;
    }
    
    @Override
    public boolean requiresApproval(TaskStopInput input) {
        // 停止任务可能需要确认，特别是强制停止
        return input != null && Boolean.TRUE.equals(input.force());
    }
}
