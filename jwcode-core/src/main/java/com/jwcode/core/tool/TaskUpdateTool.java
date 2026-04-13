package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStatus;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.TaskUpdateInput;
import com.jwcode.core.tool.output.TaskUpdateOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * TaskUpdate 工具 - 更新任务
 * 
 * 更新任务的标题、描述、状态、优先级、进度和输出内容。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskUpdateTool implements Tool<TaskUpdateInput, TaskUpdateOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskUpdateTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final TaskStore taskStore;
    
    public TaskUpdateTool() {
        this.taskStore = TaskStore.getInstance();
    }
    
    public TaskUpdateTool(TaskStore taskStore) {
        this.taskStore = taskStore;
    }
    
    @Override
    public String getName() {
        return "TaskUpdate";
    }
    
    @Override
    public String getDescription() {
        return "更新任务信息。支持更新标题、描述、状态、优先级、进度和输出内容。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 TaskUpdate 工具更新任务信息。
               
               参数:
               - id: 任务ID（必需）
               - title: 新标题（可选）
               - description: 新描述（可选）
               - status: 新状态（可选，值: PENDING, RUNNING, COMPLETED, FAILED, STOPPED, CANCELLED）
               - priority: 新优先级 1-10（可选）
               - tags: 新标签列表（可选）
               - progress: 新进度 0-100（可选）
               - outputAppend: 追加的输出内容（可选）
               
               示例:
               - {"id": "task-xxx", "status": "RUNNING"} - 更新任务状态为运行中
               - {"id": "task-xxx", "progress": 50} - 更新任务进度为 50%
               - {"id": "task-xxx", "outputAppend": "处理完成部分 A"} - 追加任务输出
               - {"id": "task-xxx", "status": "COMPLETED", "progress": 100} - 标记任务完成
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
                        "title": {"type": "string", "description": "新标题"},
                        "description": {"type": "string", "description": "新描述"},
                        "status": {"type": "string", "description": "新状态: PENDING, RUNNING, COMPLETED, FAILED, STOPPED, CANCELLED"},
                        "priority": {"type": "integer", "description": "新优先级 1-10"},
                        "tags": {"type": "array", "items": {"type": "string"}, "description": "新标签列表"},
                        "progress": {"type": "integer", "description": "新进度 0-100"},
                        "outputAppend": {"type": "string", "description": "追加的输出内容"}
                    },
                    "required": ["id"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<TaskUpdateInput> getInputType() {
        return new TypeReference<TaskUpdateInput>() {};
    }
    
    @Override
    public TypeReference<TaskUpdateOutput> getOutputType() {
        return new TypeReference<TaskUpdateOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<TaskUpdateOutput>> call(
            TaskUpdateInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Task task = taskStore.get(input.id());
                
                if (task == null) {
                    logger.warn("Task not found for update: {}", input.id());
                    return ToolResult.error("任务不存在: " + input.id());
                }
                
                TaskStatus oldStatus = task.getStatus();
                List<String> updatedFields = new ArrayList<>();
                
                // 更新标题
                if (input.title() != null && !input.title().trim().isEmpty()) {
                    task.setTitle(input.title().trim());
                    updatedFields.add("title");
                }
                
                // 更新描述
                if (input.description() != null) {
                    task.setDescription(input.description());
                    updatedFields.add("description");
                }
                
                // 更新状态
                if (input.status() != null && !input.status().trim().isEmpty()) {
                    TaskStatus newStatus = TaskStatus.fromString(input.status().trim());
                    task.updateStatus(newStatus);
                    updatedFields.add("status (" + oldStatus + " -> " + newStatus + ")");
                }
                
                // 更新优先级
                if (input.priority() != null) {
                    task.setPriority(Math.max(1, Math.min(10, input.priority())));
                    updatedFields.add("priority");
                }
                
                // 更新标签
                if (input.tags() != null) {
                    task.getTags().clear();
                    input.tags().forEach(tag -> {
                        if (tag != null && !tag.trim().isEmpty()) {
                            task.addTag(tag.trim());
                        }
                    });
                    updatedFields.add("tags");
                }
                
                // 更新进度
                if (input.progress() != null) {
                    int progress = Math.max(0, Math.min(100, input.progress()));
                    task.updateProgress(progress);
                    updatedFields.add("progress (" + progress + "%)");
                }
                
                // 追加输出
                if (input.outputAppend() != null && !input.outputAppend().isEmpty()) {
                    task.appendOutputLine(input.outputAppend());
                    updatedFields.add("output");
                }
                
                // 保存更新
                taskStore.update(task);
                
                logger.info("Task updated: {} - fields: {}", task.getId(), updatedFields);
                
                // 构建消息
                StringBuilder message = new StringBuilder();
                message.append("任务更新成功！\n\n");
                message.append("任务ID: ").append(task.getId()).append("\n");
                message.append("当前状态: ").append(task.getStatus()).append("\n");
                message.append("当前进度: ").append(task.getProgress()).append("%\n");
                
                if (!updatedFields.isEmpty()) {
                    message.append("\n更新的字段:\n");
                    updatedFields.forEach(field -> message.append("  - ").append(field).append("\n"));
                }
                
                TaskUpdateOutput output = TaskUpdateOutput.success(task.getId(), message.toString());
                
                ToolResult<TaskUpdateOutput> result = ToolResult.success(output);
                result.setContent(message.toString());
                result.setMetadata(java.util.Map.of(
                    "taskId", task.getId(),
                    "updatedFields", updatedFields,
                    "newStatus", task.getStatus().name()
                ));
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to update task", e);
                return ToolResult.error("更新任务失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(TaskUpdateInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        if (input.id() == null || input.id().trim().isEmpty()) {
            return ToolValidationResult.invalid("id 是必需的");
        }
        
        if (input.priority() != null && (input.priority() < 1 || input.priority() > 10)) {
            return ToolValidationResult.invalid("priority 必须在 1-10 之间");
        }
        
        if (input.progress() != null && (input.progress() < 0 || input.progress() > 100)) {
            return ToolValidationResult.invalid("progress 必须在 0-100 之间");
        }
        
        if (input.status() != null) {
            try {
                TaskStatus.fromString(input.status());
            } catch (Exception e) {
                return ToolValidationResult.invalid("无效的状态值: " + input.status());
            }
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TaskUpdateInput input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(TaskUpdateInput input) {
        // 如果更新为最终状态，可能有破坏性
        if (input != null && input.status() != null) {
            String status = input.status().toUpperCase();
            return status.equals("FAILED") || status.equals("STOPPED") || status.equals("CANCELLED");
        }
        return false;
    }
}
