package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.TaskCreateInput;
import com.jwcode.core.tool.output.TaskCreateOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * TaskCreate 工具 - 创建新任务
 * 
 * 用于创建和管理待办任务，支持子任务创建。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskCreateTool implements Tool<TaskCreateInput, TaskCreateOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskCreateTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final TaskStore taskStore;
    
    public TaskCreateTool() {
        this.taskStore = TaskStore.getInstance();
    }
    
    public TaskCreateTool(TaskStore taskStore) {
        this.taskStore = taskStore;
    }
    
    @Override
    public String getName() {
        return "TaskCreate";
    }
    
    @Override
    public String getDescription() {
        return "创建新任务。用于跟踪和管理待办事项，支持子任务创建。";
    }
    
    @Override
    public String getPrompt() {
        return """
               TaskCreate — 创建新任务以跟踪工作进度。

               ## 何时使用：
               - 多步骤任务（3+ 步）— 为每个步骤创建一个任务来跟踪进度
               - 用户明确要求使用任务列表跟踪进度
               - 用户提供了一系列需要完成的事项（编号列表或逗号分隔）
               - 收到新指令后，将需求拆解为可跟踪的任务
               - 在 Plan/Goal 模式下，将计划分解为具体任务

               ## 何时跳过：
               - 单个简单的任务（1 步就能完成）
               - 琐碎任务（少于 3 个步骤的简单变更）
               - 纯对话或信息咨询
               - 快速的文件查看或搜索

               参数:
               - title: 任务标题（必需）
               - description: 任务描述（可选）
               - priority: 优先级 1-10（可选，默认 5）
               - tags: 标签列表（可选）
               - parentId: 父任务ID（可选，用于创建子任务）

               示例:
               - {"title": "修复登录bug"} - 创建简单任务
               - {"title": "实现用户管理", "description": "包括增删改查功能", "priority": 8} - 创建高优先级任务
               - {"title": "编写单元测试", "parentId": "task-xxx"} - 创建子任务
               - {"title": "优化性能", "tags": ["performance", "backend"]} - 创建带标签的任务
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "title": {"type": "string", "description": "任务标题"},
                        "description": {"type": "string", "description": "任务描述"},
                        "priority": {"type": "integer", "description": "优先级 1-10", "default": 5},
                        "tags": {"type": "array", "items": {"type": "string"}, "description": "标签列表"},
                        "parentId": {"type": "string", "description": "父任务ID"}
                    },
                    "required": ["title"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<TaskCreateInput> getInputType() {
        return new TypeReference<TaskCreateInput>() {};
    }
    
    @Override
    public TypeReference<TaskCreateOutput> getOutputType() {
        return new TypeReference<TaskCreateOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<TaskCreateOutput>> call(
            TaskCreateInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 创建任务对象
                Task task;
                if (input.parentId() != null && !input.parentId().isEmpty()) {
                    // 检查父任务是否存在
                    Task parentTask = taskStore.get(input.parentId());
                    if (parentTask == null) {
                        logger.warn("Parent task not found: {}", input.parentId());
                        return ToolResult.error("父任务不存在: " + input.parentId());
                    }
                    task = new Task(input.title(), input.description(), input.parentId());
                } else {
                    task = new Task(input.title(), input.description());
                }
                
                // 设置优先级
                if (input.priority() != null) {
                    task.setPriority(input.priority());
                }
                
                // 设置标签
                if (input.tags() != null && !input.tags().isEmpty()) {
                    input.tags().forEach(task::addTag);
                }
                
                // 保存任务
                taskStore.create(task);
                
                logger.info("Task created: {} - {}", task.getId(), task.getTitle());
                
                // 构建消息
                StringBuilder message = new StringBuilder();
                message.append("任务创建成功！\n\n");
                message.append("任务ID: ").append(task.getId()).append("\n");
                message.append("标题: ").append(task.getTitle()).append("\n");
                if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                    message.append("描述: ").append(task.getDescription()).append("\n");
                }
                message.append("优先级: ").append(task.getPriority()).append("\n");
                message.append("状态: ").append(task.getStatus()).append("\n");
                
                if (task.isSubTask()) {
                    message.append("父任务: ").append(task.getParentId()).append("\n");
                }
                
                TaskCreateOutput output = TaskCreateOutput.success(task.getId(), message.toString());
                
                ToolResult<TaskCreateOutput> result = ToolResult.success(output);
                result.setContent(message.toString());
                result.setMetadata(java.util.Map.of(
                    "taskId", task.getId(),
                    "isSubTask", task.isSubTask()
                ));
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to create task", e);
                return ToolResult.error("创建任务失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(TaskCreateInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        if (input.title() == null || input.title().trim().isEmpty()) {
            return ToolValidationResult.invalid("title 是必需的");
        }
        
        if (input.priority() != null && (input.priority() < 1 || input.priority() > 10)) {
            return ToolValidationResult.invalid("priority 必须在 1-10 之间");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TaskCreateInput input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(TaskCreateInput input) {
        return false;
    }
    
    @Override
    public boolean requiresApproval(TaskCreateInput input) {
        return false;
    }
}
