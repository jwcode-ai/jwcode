package com.jwcode.core.tool;

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
                
                // 读取当前待办事项
                List<String> todos = readTodos();
                
                // 执行操作
                List<String> resultTodos = executeOperation(input, todos);
                
                // 保存待办事项
                saveTodos(resultTodos);
                
                String message = generateMessage(input);
                return ToolResult.success(TodoWriteOutput.success(resultTodos, message));
                
            } catch (Exception e) {
                return ToolResult.error("操作失败: " + e.getMessage());
            }
        });
    }
    
    private List<String> readTodos() throws Exception {
        if (Files.exists(todoFilePath)) {
            List<String> lines = Files.readAllLines(todoFilePath);
            return lines.stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
        return new ArrayList<>();
    }
    
    private void saveTodos(List<String> todos) throws Exception {
        Files.write(todoFilePath, todos);
    }
    
    private List<String> executeOperation(TodoWriteInput input, List<String> todos) {
        String operation = input.operation() != null ? input.operation() : "add";
        
        return switch (operation) {
            case "add" -> {
                List<String> result = new ArrayList<>(todos);
                if (input.todo() != null && !input.todo().isEmpty()) {
                    result.add(input.todo());
                }
                yield result;
            }
            case "edit" -> {
                List<String> result = new ArrayList<>(todos);
                int index = input.index() != null ? input.index() : 0;
                if (index >= 0 && index < result.size() && input.newContent() != null) {
                    result.set(index, input.newContent());
                }
                yield result;
            }
            case "delete" -> {
                List<String> result = new ArrayList<>(todos);
                int index = input.index() != null ? input.index() : 0;
                if (index >= 0 && index < result.size()) {
                    result.remove(index);
                }
                yield result;
            }
            case "replace_all" -> {
                if (input.todos() != null) {
                    yield new ArrayList<>(input.todos());
                }
                yield todos;
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
            case "replace_all" -> "已更新所有待办事项";
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