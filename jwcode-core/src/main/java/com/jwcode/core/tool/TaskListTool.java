package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStatus;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.TaskListInput;
import com.jwcode.core.tool.output.TaskListOutput;
import com.jwcode.core.tool.output.TaskListOutput.TaskSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * TaskList 工具 - 列出任务
 * 
 * 列出所有任务，支持按状态过滤、活跃任务过滤和分页。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskListTool implements Tool<TaskListInput, TaskListOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskListTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final TaskStore taskStore;
    
    public TaskListTool() {
        this.taskStore = TaskStore.getInstance();
    }
    
    public TaskListTool(TaskStore taskStore) {
        this.taskStore = taskStore;
    }
    
    @Override
    public String getName() {
        return "TaskList";
    }
    
    @Override
    public String getDescription() {
        return "列出所有任务。支持按状态过滤、活跃任务过滤和分页。";
    }
    
    @Override
    public String getPrompt() {
        return """
               TaskList — 查看所有任务及其当前状态。

               ## 使用时机：
               - 上下文压缩后，用 TaskList 恢复任务进度认知
               - 完成一个任务后，查看下一个待处理的任务
               - 用户询问当前进度时，展示任务列表
               - 恢复会话时，重新了解未完成的工作
               - 使用 activeOnly 聚焦于未完成的活跃任务

               参数:
               - activeOnly: 是否只显示活跃任务（可选，默认 false）
               - status: 按状态过滤（可选，值: PENDING, RUNNING, COMPLETED, FAILED, STOPPED, CANCELLED）
               - tag: 按标签过滤（可选）

               示例:
               - {} - 列出所有任务
               - {"activeOnly": true} - 只列出活跃任务
               - {"status": "PENDING"} - 列出待处理任务
               - {"tag": "bug"} - 按标签过滤
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "activeOnly": {"type": "boolean", "description": "只显示活跃任务", "default": false},
                        "status": {"type": "string", "description": "按状态过滤"},
                        "tag": {"type": "string", "description": "按标签过滤"}
                    }
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<TaskListInput> getInputType() {
        return new TypeReference<TaskListInput>() {};
    }
    
    @Override
    public TypeReference<TaskListOutput> getOutputType() {
        return new TypeReference<TaskListOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<TaskListOutput>> call(
            TaskListInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取任务列表
                List<Task> allTasks;
                
                if (input.activeOnly()) {
                    // 只获取活跃任务
                    allTasks = taskStore.listActive();
                } else if (input.status() != null && !input.status().trim().isEmpty()) {
                    // 按状态过滤
                    TaskStatus status = TaskStatus.fromString(input.status().trim());
                    allTasks = taskStore.listByStatus(status);
                } else if (input.tag() != null && !input.tag().trim().isEmpty()) {
                    // 按标签过滤
                    allTasks = taskStore.listByTag(input.tag().trim());
                } else {
                    // 获取所有任务
                    allTasks = taskStore.list();
                }
                
                // 按创建时间倒序排序
                allTasks.sort(Comparator.comparing(Task::getCreatedAt, 
                    Comparator.nullsLast(Comparator.reverseOrder())));
                
                int total = allTasks.size();
                
                // 转换为摘要（全量返回，不分页）
                List<TaskSummary> summaries = allTasks.stream()
                    .map(TaskSummary::fromTask)
                    .collect(Collectors.toList());
                
                logger.debug("Listed all {} tasks", total);
                
                // 构建表格输出
                StringBuilder content = new StringBuilder();
                content.append("任务列表\n");
                content.append("=" .repeat(80)).append("\n\n");
                content.append("总计: ").append(total).append(" 个任务\n\n");
                
                if (summaries.isEmpty()) {
                    content.append("暂无任务\n");
                } else {
                    // 表头
                    content.append(String.format("%-36s %-25s %-10s %-8s %-8s\n",
                        "任务ID", "标题", "状态", "优先级", "进度"));
                    content.append("-".repeat(80)).append("\n");
                    
                    // 任务行
                    for (TaskSummary summary : summaries) {
                        String title = summary.title();
                        if (title != null && title.length() > 22) {
                            title = title.substring(0, 19) + "...";
                        }
                        
                        content.append(String.format("%-36s %-25s %-10s %-8d %-8s\n",
                            summary.id(),
                            title != null ? title : "-",
                            summary.status(),
                            summary.priority(),
                            summary.progress() + "%"
                        ));
                    }
                }
                
                TaskListOutput output = TaskListOutput.success(summaries, total);
                
                ToolResult<TaskListOutput> result = ToolResult.success(output);
                result.setContent(content.toString());
                result.setMetadata(java.util.Map.of(
                    "total", total
                ));
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to list tasks", e);
                return ToolResult.error("列出任务失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(TaskListInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TaskListInput input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(TaskListInput input) {
        return true;
    }
}
