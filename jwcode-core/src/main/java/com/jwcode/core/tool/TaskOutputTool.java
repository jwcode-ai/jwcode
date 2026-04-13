package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.TaskOutputInput;
import com.jwcode.core.tool.output.TaskOutputResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * TaskOutput 工具 - 获取任务输出
 * 
 * 获取任务的输出内容，支持增量输出和分页。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskOutputTool implements Tool<TaskOutputInput, TaskOutputResult, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskOutputTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static final int DEFAULT_LIMIT = 10000; // 默认最大返回 10000 字符
    
    private final TaskStore taskStore;
    
    public TaskOutputTool() {
        this.taskStore = TaskStore.getInstance();
    }
    
    public TaskOutputTool(TaskStore taskStore) {
        this.taskStore = taskStore;
    }
    
    @Override
    public String getName() {
        return "TaskOutput";
    }
    
    @Override
    public String getDescription() {
        return "获取任务输出内容。支持增量输出和分页，用于查看长运行任务的输出。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 TaskOutput 工具获取任务的输出内容。
               
               参数:
               - id: 任务ID（必需）
               - offset: 输出内容偏移量（可选，默认 0）
               - limit: 最大返回字符数（可选，默认 10000，-1 表示全部）
               
               示例:
               - {"id": "task-xxx"} - 获取任务全部输出
               - {"id": "task-xxx", "offset": 1000} - 从第 1000 个字符开始获取
               - {"id": "task-xxx", "limit": 5000} - 最多获取 5000 个字符
               
               注意:
               - 对于长输出任务，建议使用 offset 参数实现增量读取
               - 返回的 hasMore 字段指示是否还有更多内容
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
                        "offset": {"type": "integer", "description": "起始偏移量", "default": 0},
                        "limit": {"type": "integer", "description": "最大返回字符数", "default": 10000}
                    },
                    "required": ["id"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<TaskOutputInput> getInputType() {
        return new TypeReference<TaskOutputInput>() {};
    }
    
    @Override
    public TypeReference<TaskOutputResult> getOutputType() {
        return new TypeReference<TaskOutputResult>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<TaskOutputResult>> call(
            TaskOutputInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Task task = taskStore.get(input.id());
                
                if (task == null) {
                    logger.warn("Task not found: {}", input.id());
                    return ToolResult.error("任务不存在: " + input.id());
                }
                
                String fullOutput = task.getOutputString();
                int totalLength = fullOutput.length();
                int offset = input.offset() != null ? input.offset() : 0;
                int limit = (input.limit() != null && input.limit() > 0) 
                    ? input.limit() 
                    : DEFAULT_LIMIT;
                
                // 确保偏移量有效
                if (offset < 0) offset = 0;
                if (offset > totalLength) offset = totalLength;
                
                // 计算结束位置
                int endPos = Math.min(offset + limit, totalLength);
                
                // 截取输出
                String outputSlice = fullOutput.substring(offset, endPos);
                boolean hasMore = endPos < totalLength;
                
                logger.debug("Task output retrieved: {} - offset: {}, length: {}, hasMore: {}",
                    task.getId(), offset, outputSlice.length(), hasMore);
                
                // 构建消息
                StringBuilder content = new StringBuilder();
                content.append("任务输出 - ").append(task.getTitle()).append("\n");
                content.append("=" .repeat(60)).append("\n\n");
                content.append("任务ID: ").append(task.getId()).append("\n");
                content.append("任务状态: ").append(task.getStatus()).append("\n");
                content.append("输出长度: ").append(totalLength).append(" 字符\n");
                content.append("本次读取: ").append(offset).append(" - ").append(endPos).append("\n");
                content.append("是否有更多: ").append(hasMore ? "是" : "否").append("\n");
                content.append("\n--- 输出内容 ---\n\n");
                content.append(outputSlice);
                
                if (hasMore) {
                    content.append("\n\n[... 还有 ")
                           .append(totalLength - endPos)
                           .append(" 字符，使用 offset=")
                           .append(endPos)
                           .append(" 继续读取 ...]");
                }
                
                TaskOutputResult output = TaskOutputResult.success(
                    task.getId(), 
                    outputSlice, 
                    totalLength, 
                    offset, 
                    hasMore
                );
                
                ToolResult<TaskOutputResult> result = ToolResult.success(output);
                result.setContent(content.toString());
                result.setMetadata(java.util.Map.of(
                    "taskId", task.getId(),
                    "totalLength", totalLength,
                    "offset", offset,
                    "sliceLength", outputSlice.length(),
                    "hasMore", hasMore
                ));
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to get task output", e);
                return ToolResult.error("获取任务输出失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(TaskOutputInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        if (input.id() == null || input.id().trim().isEmpty()) {
            return ToolValidationResult.invalid("id 是必需的");
        }
        
        if (input.offset() != null && input.offset() < 0) {
            return ToolValidationResult.invalid("offset 不能为负数");
        }
        
        if (input.limit() != null && input.limit() != -1 && input.limit() < 1) {
            return ToolValidationResult.invalid("limit 必须大于 0 或等于 -1");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TaskOutputInput input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(TaskOutputInput input) {
        return true;
    }
}
