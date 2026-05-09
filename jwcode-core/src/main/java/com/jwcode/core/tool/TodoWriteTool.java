package com.jwcode.core.tool;

import com.jwcode.core.api.TodoWriteBroadcaster;
import com.jwcode.core.tool.input.TodoItem;
import com.jwcode.core.tool.input.TodoWriteInput;
import com.jwcode.core.tool.output.TodoWriteOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * TodoWrite 工具 — 增强版
 * 
 * <p>用于创建、更新或删除待办事项，支持严格的状态机纪律：</p>
 * <ul>
 *   <li><b>exactly one in_progress</b> — 任意时刻最多只有一个待办事项处于 in_progress 状态</li>
 *   <li><b>content/activeForm 双形式</b> — pending/completed 显示 content，in_progress 显示 activeForm</li>
 *   <li><b>状态机</b>：pending → in_progress → completed（不可逆）</li>
 *   <li><b>诚实纪律</b>：失败时不能标 completed，保持 in_progress</li>
 * </ul>
 * 
 * <p>操作类型：</p>
 * <ul>
 *   <li><b>add</b> — 添加新待办事项（默认 pending）</li>
 *   <li><b>edit</b> — 编辑指定索引的待办事项</li>
 *   <li><b>delete</b> — 删除指定索引的待办事项</li>
 *   <li><b>replace_all</b> — 替换所有待办事项</li>
 *   <li><b>mark</b> — 标记指定索引的待办事项状态（自动维护 exactly one in_progress）</li>
 * </ul>
 */
public class TodoWriteTool implements Tool<TodoWriteInput, TodoWriteOutput, Void> {
    
    private static final Logger logger = Logger.getLogger(TodoWriteTool.class.getName());
    private static final String TODO_FILE = ".jwcode/todos.txt";
    private final Path todoFilePath;
    
    public TodoWriteTool() {
        this.todoFilePath = Paths.get(System.getProperty("user.dir"), TODO_FILE);
    }
    
    public TodoWriteTool(Path workingDirectory) {
        this.todoFilePath = workingDirectory.resolve(TODO_FILE);
    }
    
    @Override
    public String getName() {
        return "TodoWrite";
    }
    
    @Override
    public String getDescription() {
        return "Create, edit, and manage todos with strict state machine discipline. " +
               "Supports content/activeForm dual form, exactly-one-in_progress enforcement.";
    }
    
    @Override
    public String getPrompt() {
        return """
            Use this tool to manage task progress with a structured todo list.
            
            **State Machine**: pending → in_progress → completed
            - Exactly ONE task must be in_progress at any time.
            - Never mark a task as completed if tests are failing or work is incomplete.
            - Use activeForm for the in_progress display (e.g., "Running tests" instead of "Run tests").
            
            **Operations**:
            - add: Add new todo items (default: pending)
            - edit: Edit a todo at given index
            - delete: Delete a todo at given index
            - replace_all: Replace all todos
            - mark: Mark a todo's status (auto-enforces exactly-one in_progress)
            
            **Dual Form**:
            - content: Command form for pending/completed (e.g., "Run tests")
            - activeForm: Progressive form for in_progress (e.g., "Running tests")
            """;
    }
    
    @Override
    public CompletableFuture<ToolResult<TodoWriteOutput>> call(
            TodoWriteInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 确保目录存在
                Files.createDirectories(todoFilePath.getParent());
                
                // 读取当前待办事项
                List<TodoItem> todos = readTodos();
                
                // 执行操作
                List<TodoItem> resultTodos = executeOperation(input, todos);
                
                // 保存待办事项
                saveTodos(resultTodos);
                
                // 【修复】通过 WebSocket 广播待办事项更新
                broadcastTodoUpdate(resultTodos);
                
                // 构建输出
                List<String> resultStrings = resultTodos.stream()
                        .map(TodoItem::toMarkdown)
                        .toList();
                
                String message = generateMessage(input, resultTodos);
                return ToolResult.success(TodoWriteOutput.success(resultStrings, message));
                
            } catch (Exception e) {
                logger.warning("TodoWrite failed: " + e.getMessage());
                return ToolResult.error("操作失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 从文件读取待办事项，解析为 TodoItem 列表
     */
    private List<TodoItem> readTodos() throws Exception {
        List<TodoItem> items = new ArrayList<>();
        
        if (Files.exists(todoFilePath)) {
            List<String> lines = Files.readAllLines(todoFilePath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                TodoItem item = TodoItem.fromMarkdown(trimmed);
                if (item != null) {
                    items.add(item);
                } else {
                    // 兼容旧格式：非 Markdown 格式的行作为普通待办事项
                    items.add(TodoItem.of(trimmed));
                }
            }
        }
        
        return items;
    }
    
    /**
     * 将 TodoItem 列表保存为 Markdown 格式
     */
    private void saveTodos(List<TodoItem> todos) throws Exception {
        List<String> lines = todos.stream()
                .map(TodoItem::toMarkdown)
                .toList();
        Files.write(todoFilePath, lines);
    }
    
    /**
     * 执行待办事项操作 — 核心状态机逻辑
     */
    private List<TodoItem> executeOperation(TodoWriteInput input, List<TodoItem> todos) {
        String operation = input.operation() != null ? input.operation() : "add";
        
        return switch (operation) {
            case "add" -> executeAdd(input, todos);
            case "edit" -> executeEdit(input, todos);
            case "delete" -> executeDelete(input, todos);
            case "replace_all" -> executeReplaceAll(input, todos);
            case "mark" -> executeMark(input, todos);
            default -> todos;
        };
    }
    
    /**
     * 添加新待办事项
     */
    private List<TodoItem> executeAdd(TodoWriteInput input, List<TodoItem> todos) {
        List<TodoItem> result = new ArrayList<>(todos);
        
        if (input.todos() != null && !input.todos().isEmpty()) {
            // 添加结构化的待办事项列表
            for (TodoItem item : input.todos()) {
                // 如果添加的是 in_progress，确保唯一性
                if ("in_progress".equals(item.status())) {
                    result = ensureSingleInProgress(result);
                }
                result.add(item);
            }
        } else if (input.todo() != null && !input.todo().isEmpty()) {
            // 添加简单的待办事项
            String status = input.status() != null ? input.status() : "pending";
            String activeForm = input.activeForm() != null ? input.activeForm() : null;
            
            if ("in_progress".equals(status)) {
                result = ensureSingleInProgress(result);
            }
            
            result.add(TodoItem.of(input.todo(), activeForm, status));
        }
        
        return result;
    }
    
    /**
     * 编辑指定索引的待办事项
     */
    private List<TodoItem> executeEdit(TodoWriteInput input, List<TodoItem> todos) {
        List<TodoItem> result = new ArrayList<>(todos);
        int index = input.index() != null ? input.index() : 0;
        
        if (index >= 0 && index < result.size()) {
            TodoItem original = result.get(index);
            String newContent = input.todo() != null ? input.todo() : original.content();
            String newActiveForm = input.activeForm() != null ? input.activeForm() : original.activeForm();
            String newStatus = input.status() != null ? input.status() : original.status();
            
            // 如果修改为 in_progress，确保唯一性
            if ("in_progress".equals(newStatus) && !"in_progress".equals(original.status())) {
                result = ensureSingleInProgress(result);
            }
            
            result.set(index, TodoItem.of(
                original.id(), newContent, newActiveForm, newStatus, original.priority()
            ));
        }
        
        return result;
    }
    
    /**
     * 删除指定索引的待办事项
     */
    private List<TodoItem> executeDelete(TodoWriteInput input, List<TodoItem> todos) {
        List<TodoItem> result = new ArrayList<>(todos);
        int index = input.index() != null ? input.index() : 0;
        
        if (index >= 0 && index < result.size()) {
            result.remove(index);
        }
        
        return result;
    }
    
    /**
     * 替换所有待办事项
     */
    private List<TodoItem> executeReplaceAll(TodoWriteInput input, List<TodoItem> todos) {
        List<TodoItem> newTodos = input.getTodosOrDefault();
        
        // 确保 in_progress 唯一性
        long inProgressCount = newTodos.stream()
                .filter(t -> "in_progress".equals(t.status()))
                .count();
        if (inProgressCount > 1) {
            // 只保留第一个 in_progress，其余改为 pending
            boolean firstFound = false;
            List<TodoItem> adjusted = new ArrayList<>();
            for (TodoItem item : newTodos) {
                if ("in_progress".equals(item.status())) {
                    if (!firstFound) {
                        firstFound = true;
                        adjusted.add(item);
                    } else {
                        adjusted.add(item.withStatus("pending"));
                    }
                } else {
                    adjusted.add(item);
                }
            }
            return adjusted;
        }
        
        return new ArrayList<>(newTodos);
    }
    
    /**
     * 标记指定索引的待办事项状态 — 核心状态机操作
     * 
     * <p>严格纪律：</p>
     * <ul>
     *   <li>标记为 in_progress → 自动将其他 in_progress 改为 pending</li>
     *   <li>标记为 completed → 不自动推进下一个（由调用方决定）</li>
     *   <li>不允许从 completed 回退到 pending/in_progress</li>
     * </ul>
     */
    private List<TodoItem> executeMark(TodoWriteInput input, List<TodoItem> todos) {
        List<TodoItem> result = new ArrayList<>(todos);
        int index = input.index() != null ? input.index() : 0;
        String newStatus = input.status() != null ? input.status() : "in_progress";
        
        if (index < 0 || index >= result.size()) {
            return result;
        }
        
        TodoItem target = result.get(index);
        String currentStatus = target.status();
        
        // 纪律：不允许从 completed 回退
        if ("completed".equals(currentStatus)) {
            logger.warning("Cannot change status of completed todo: " + target.content());
            return result;
        }
        
        // 纪律：不允许从 in_progress 到 pending（只能前进）
        if ("in_progress".equals(currentStatus) && "pending".equals(newStatus)) {
            logger.warning("Cannot regress from in_progress to pending: " + target.content());
            return result;
        }
        
        // 标记为 in_progress → 确保唯一性
        if ("in_progress".equals(newStatus)) {
            result = ensureSingleInProgress(result);
            // 重新获取 target（索引可能因删除而改变）
            if (index < result.size()) {
                result.set(index, result.get(index).withStatus("in_progress"));
            }
        } else if ("completed".equals(newStatus)) {
            result.set(index, target.withStatus("completed"));
        }
        
        return result;
    }
    
    /**
     * 确保列表中最多只有一个 in_progress 项
     * 将其他 in_progress 项改为 pending
     */
    private List<TodoItem> ensureSingleInProgress(List<TodoItem> todos) {
        boolean found = false;
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem item : todos) {
            if ("in_progress".equals(item.status())) {
                if (!found) {
                    found = true;
                    result.add(item);
                } else {
                    // 将多余的 in_progress 改为 pending
                    result.add(item.withStatus("pending"));
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }
    
    /**
     * 生成操作消息
     */
    private String generateMessage(TodoWriteInput input, List<TodoItem> todos) {
        String operation = input.operation() != null ? input.operation() : "add";
        
        // 找到当前 in_progress 的项
        Optional<TodoItem> inProgress = todos.stream()
                .filter(t -> "in_progress".equals(t.status()))
                .findFirst();
        
        String inProgressInfo = inProgress
                .map(t -> " 当前进行: " + t.getDisplayText())
                .orElse("");
        
        return switch (operation) {
            case "add" -> "✅ 已添加待办事项 (" + todos.size() + " 项)" + inProgressInfo;
            case "edit" -> "✅ 已更新待办事项 #" + input.index() + inProgressInfo;
            case "delete" -> "✅ 已删除待办事项 #" + input.index() + " (" + todos.size() + " 项剩余)" + inProgressInfo;
            case "replace_all" -> "✅ 已更新所有待办事项 (" + todos.size() + " 项)" + inProgressInfo;
            case "mark" -> {
                String statusDisplay = switch (input.status()) {
                    case "completed" -> "已完成";
                    case "in_progress" -> "进行中";
                    default -> input.status();
                };
                yield "✅ 待办事项 #" + input.index() + " 已标记为 " + statusDisplay + inProgressInfo;
            }
            default -> "操作完成" + inProgressInfo;
        };
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<TodoWriteInput> getInputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<TodoWriteOutput> getOutputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(TodoWriteInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        String operation = input.operation();
        if (operation != null && !List.of("add", "edit", "delete", "replace_all", "mark").contains(operation)) {
            return ToolValidationResult.invalid("无效的操作类型: " + operation);
        }
        
        // mark 操作需要 status
        if ("mark".equals(operation) && input.status() == null) {
            return ToolValidationResult.invalid("mark 操作需要指定 status 参数");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TodoWriteInput input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(TodoWriteInput input) {
        return "delete".equals(input.operation()) || "replace_all".equals(input.operation());
    }
    
    @Override
    public boolean requiresApproval(TodoWriteInput input) {
        return isDestructive(input);
    }
    
    @Override
    public Set<SideEffect> getSideEffects() {
        return Set.of(SideEffect.SESSION_MUTATION);
    }
    
    @Override
    public ToolCategory getCategory() {
        return ToolCategory.METACOGNITION;
    }

    // ==================== WebSocket 广播 ====================

    /**
     * 【修复】通过 TodoWriteBroadcaster 广播待办事项更新到前端
     */
    private void broadcastTodoUpdate(List<TodoItem> todos) {
        try {
            if (!TodoWriteBroadcaster.isConfigured()) {
                return; // WebSocket 未配置，跳过广播
            }

            TodoWriteBroadcaster broadcaster = TodoWriteBroadcaster.getInstance();
            if (broadcaster == null) {
                return;
            }

            // 广播全量待办列表（使用列表中的位置作为 index）
            List<com.jwcode.core.api.TodoItemDto> dtos = new ArrayList<>();
            for (int i = 0; i < todos.size(); i++) {
                TodoItem item = todos.get(i);
                dtos.add(new com.jwcode.core.api.TodoItemDto(
                        item.content(),
                        item.activeForm(),
                        item.status(),
                        i));
            }

            broadcaster.broadcastTodoUpdate("default", dtos);

            // 统计进度并广播
            long completed = todos.stream().filter(t -> "completed".equals(t.status())).count();
            Optional<TodoItem> inProgress = todos.stream()
                    .filter(t -> "in_progress".equals(t.status()))
                    .findFirst();
            String description = inProgress.map(TodoItem::getDisplayText).orElse(null);
            broadcaster.broadcastTodoProgress("default", (int) completed, todos.size(), description);

            logger.fine("TodoWriteBroadcast: " + todos.size() + " items broadcast");
        } catch (Exception e) {
            logger.warning("TodoWriteBroadcast failed: " + e.getMessage());
        }
    }
}
