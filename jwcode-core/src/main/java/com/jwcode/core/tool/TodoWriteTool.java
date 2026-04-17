package com.jwcode.core.tool;

import com.jwcode.core.tool.input.TodoItem;
import com.jwcode.core.tool.input.TodoWriteInput;
import com.jwcode.core.tool.output.TodoWriteOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * TodoWrite 工具
 * 用于创建、更新或删除待办事项
 * 
 * 支持结构化的 TodoItem 格式
 */
public class TodoWriteTool implements Tool<TodoWriteInput, TodoWriteOutput, Void> {
    
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
        return "Create, edit, and delete todos. An equivalent to saving your current work context to a local file.";
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool to record tasks or notes that you want to remember. " +
               "The tool operates on a file at the root of your project called .jwcode/todos.txt. " +
               "Each todo should be on its own line. You can add, edit, or delete todos.";
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
                
                // 读取当前待办事项（解析为 TodoItem）
                List<TodoItem> todos = readTodos();
                
                // 执行操作
                List<TodoItem> resultTodos = executeOperation(input, todos);
                
                // 保存待办事项（转换为 Markdown）
                saveTodos(resultTodos);
                
                // 转换回字符串列表用于输出
                List<String> resultStrings = resultTodos.stream()
                        .map(TodoItem::toMarkdown)
                        .toList();
                
                String message = generateMessage(input);
                return ToolResult.success(TodoWriteOutput.success(resultStrings, message));
                
            } catch (Exception e) {
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
     * 执行待办事项操作
     */
    private List<TodoItem> executeOperation(TodoWriteInput input, List<TodoItem> todos) {
        String operation = input.operation() != null ? input.operation() : "add";
        
        return switch (operation) {
            case "add" -> {
                List<TodoItem> result = new ArrayList<>(todos);
                // 优先使用结构化的 todos，否则使用简单的 todo 字段
                if (input.todos() != null && !input.todos().isEmpty()) {
                    result.addAll(input.todos());
                } else if (input.todo() != null && !input.todo().isEmpty()) {
                    result.add(TodoItem.of(input.todo()));
                }
                yield result;
            }
            case "edit" -> {
                List<TodoItem> result = new ArrayList<>(todos);
                int index = input.index() != null ? input.index() : 0;
                if (index >= 0 && index < result.size()) {
                    TodoItem original = result.get(index);
                    if (input.newContent() != null) {
                        result.set(index, new TodoItem(
                                original.id(),
                                input.newContent(),
                                original.status(),
                                original.priority()
                        ));
                    }
                }
                yield result;
            }
            case "delete" -> {
                List<TodoItem> result = new ArrayList<>(todos);
                int index = input.index() != null ? input.index() : 0;
                if (index >= 0 && index < result.size()) {
                    result.remove(index);
                }
                yield result;
            }
            case "replace_all" -> {
                List<TodoItem> newTodos = input.getTodosOrDefault();
                yield new ArrayList<>(newTodos);
            }
            default -> todos;
        };
    }
    
    private String generateMessage(TodoWriteInput input) {
        String operation = input.operation() != null ? input.operation() : "add";
        return switch (operation) {
            case "add" -> "已添加待办事项: " + input.todo();
            case "edit" -> "已更新待办事项 #" + input.index();
            case "delete" -> "已删除待办事项 #" + input.index();
            case "replace_all" -> "已更新所有待办事项 (" + 
                    (input.todos() != null ? input.todos().size() : 0) + " 项)";
            default -> "操作完成";
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
        if (operation != null && !List.of("add", "edit", "delete", "replace_all").contains(operation)) {
            return ToolValidationResult.invalid("无效的操作类型: " + operation);
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(TodoWriteInput input) {
        // 写入操作，不是只读的
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
}