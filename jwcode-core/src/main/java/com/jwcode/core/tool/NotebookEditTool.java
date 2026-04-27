package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.notebook.Notebook;
import com.jwcode.core.notebook.NotebookCell;
import com.jwcode.core.notebook.NotebookParser;
import com.jwcode.core.repl.REPLExecutor;
import com.jwcode.core.repl.REPLFactory;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NotebookEdit 工具 - 编辑 Jupyter Notebook
 * 支持读取、编辑、添加、删除 Cell，以及执行 Cell（使用 REPL）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class NotebookEditTool implements Tool<NotebookEditTool.Input, NotebookEditTool.Output, NotebookEditTool.Progress> {
    
    private static final Logger logger = Logger.getLogger(NotebookEditTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    private final NotebookParser parser;
    private final REPLFactory replFactory;
    
    public NotebookEditTool() {
        this(new NotebookParser(), REPLFactory.getInstance());
    }
    
    public NotebookEditTool(NotebookParser parser, REPLFactory replFactory) {
        this.parser = parser;
        this.replFactory = replFactory;
    }
    
    @Override
    public String getName() {
        return "NotebookEdit";
    }
    
    @Override
    public String getDescription() {
        return "编辑 Jupyter Notebook (.ipynb) 文件。支持读取、编辑、添加、删除 Cell 以及执行代码。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 NotebookEdit 工具编辑 Jupyter Notebook 文件。
               
               参数:
               - path: Notebook 文件路径（必需）
               - operation: 操作类型（必需）
                 * "read": 读取 Notebook
                 * "edit": 编辑 Cell
                 * "add": 添加 Cell
                 * "delete": 删除 Cell
                 * "execute": 执行 Cell
                 * "save": 保存 Notebook（通常在修改后自动保存）
               - cell_index: Cell 索引（用于 edit/delete/execute）
               - cell_id: Cell ID（替代 cell_index）
               - cell_type: Cell 类型（用于 add，可选值: "code", "markdown", "raw"）
               - content: Cell 内容
               - execute: 是否在添加/编辑后执行（可选，默认 false）
               
               示例:
               - {"path": "analysis.ipynb", "operation": "read"}
               - {"path": "analysis.ipynb", "operation": "add", "cell_type": "code", "content": "print('hello')"}
               - {"path": "analysis.ipynb", "operation": "edit", "cell_index": 0, "content": "print('updated')"}
               - {"path": "analysis.ipynb", "operation": "delete", "cell_index": 1}
               - {"path": "analysis.ipynb", "operation": "execute", "cell_index": 0}
               
               注意:
               - 修改会自动保存到文件
               - 执行代码 Cell 时会自动检测语言并使用相应的 REPL
               - 如果 cell_index 和 cell_id 同时提供，优先使用 cell_id
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Notebook 文件路径"
                        },
                        "operation": {
                            "type": "string",
                            "description": "操作类型",
                            "enum": ["read", "edit", "add", "delete", "execute", "move"]
                        },
                        "cell_index": {
                            "type": "integer",
                            "description": "Cell 索引"
                        },
                        "cell_id": {
                            "type": "string",
                            "description": "Cell ID"
                        },
                        "cell_type": {
                            "type": "string",
                            "description": "Cell 类型",
                            "enum": ["code", "markdown", "raw"]
                        },
                        "content": {
                            "type": "string",
                            "description": "Cell 内容"
                        },
                        "target_index": {
                            "type": "integer",
                            "description": "目标位置（用于 move 操作）"
                        },
                        "execute": {
                            "type": "boolean",
                            "description": "是否在添加/编辑后执行",
                            "default": false
                        }
                    },
                    "required": ["path", "operation"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error(validation.getErrors().get(0));
                }
                
                Path path = Paths.get(input.path);
                String operation = input.operation.toLowerCase();
                
                return switch (operation) {
                    case "read" -> readNotebook(path);
                    case "add" -> addCell(path, input);
                    case "edit" -> editCell(path, input);
                    case "delete" -> deleteCell(path, input);
                    case "execute" -> executeCell(path, input);
                    case "move" -> moveCell(path, input);
                    default -> ToolResult.error("Unknown operation: " + operation);
                };
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "NotebookEdit error", e);
                return ToolResult.error("NotebookEdit error: " + e.getMessage());
            }
        });
    }
    
    /**
     * 读取 Notebook
     */
    private ToolResult<Output> readNotebook(Path path) {
        try {
            Notebook notebook = parser.parse(path);
            
            Output output = new Output();
            output.success = true;
            output.message = "Notebook loaded successfully";
            output.notebookPath = path.toString();
            output.cells = convertCells(notebook.getCells());
            output.cellCount = notebook.getCellCount();
            output.language = notebook.getLanguage();
            
            return ToolResult.success(output);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to read notebook", e);
            return ToolResult.error("Failed to read notebook: " + e.getMessage());
        }
    }
    
    /**
     * 添加 Cell
     */
    private ToolResult<Output> addCell(Path path, Input input) {
        try {
            Notebook notebook;
            if (java.nio.file.Files.exists(path)) {
                notebook = parser.parse(path);
            } else {
                // 创建新的 Notebook
                String language = input.language != null ? input.language : "python";
                notebook = Notebook.create(language, language);
            }
            
            // 创建新 Cell
            NotebookCell.CellType cellType = parseCellType(input.cell_type);
            if (cellType == null) {
                cellType = NotebookCell.CellType.CODE;
            }
            
            NotebookCell newCell = new NotebookCell(cellType);
            if (input.content != null) {
                newCell.setSource(input.content);
            }
            
            // 确定插入位置
            int insertIndex = notebook.getCellCount();
            if (input.cell_index != null && input.cell_index >= 0 && input.cell_index <= notebook.getCellCount()) {
                insertIndex = input.cell_index;
            }
            
            notebook.insertCell(insertIndex, newCell);
            
            // 如果需要执行
            if (Boolean.TRUE.equals(input.execute) && newCell.isCode()) {
                executeCellContent(newCell, notebook.getLanguage());
            }
            
            // 保存
            parser.save(notebook, path);
            
            Output output = new Output();
            output.success = true;
            output.message = "Cell added at index " + insertIndex;
            output.notebookPath = path.toString();
            output.cellId = newCell.getId();
            output.cellIndex = insertIndex;
            output.cells = convertCells(notebook.getCells());
            output.cellCount = notebook.getCellCount();
            
            return ToolResult.success(output);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to add cell", e);
            return ToolResult.error("Failed to add cell: " + e.getMessage());
        }
    }
    
    /**
     * 编辑 Cell
     */
    private ToolResult<Output> editCell(Path path, Input input) {
        try {
            Notebook notebook = parser.parse(path);
            
            NotebookCell cell = findCell(notebook, input);
            if (cell == null) {
                return ToolResult.error("Cell not found");
            }
            
            // 更新内容
            if (input.content != null) {
                cell.setSource(input.content);
            }
            
            // 如果需要执行
            if (Boolean.TRUE.equals(input.execute) && cell.isCode()) {
                executeCellContent(cell, notebook.getLanguage());
            }
            
            // 保存
            parser.save(notebook, path);
            
            Output output = new Output();
            output.success = true;
            output.message = "Cell updated successfully";
            output.notebookPath = path.toString();
            output.cellId = cell.getId();
            output.cellIndex = notebook.getCells().indexOf(cell);
            output.cells = convertCells(notebook.getCells());
            
            return ToolResult.success(output);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to edit cell", e);
            return ToolResult.error("Failed to edit cell: " + e.getMessage());
        }
    }
    
    /**
     * 删除 Cell
     */
    private ToolResult<Output> deleteCell(Path path, Input input) {
        try {
            Notebook notebook = parser.parse(path);
            
            NotebookCell cell = findCell(notebook, input);
            if (cell == null) {
                return ToolResult.error("Cell not found");
            }
            
            int removedIndex = notebook.getCells().indexOf(cell);
            notebook.removeCell(cell);
            
            // 保存
            parser.save(notebook, path);
            
            Output output = new Output();
            output.success = true;
            output.message = "Cell deleted at index " + removedIndex;
            output.notebookPath = path.toString();
            output.cellIndex = removedIndex;
            output.cells = convertCells(notebook.getCells());
            output.cellCount = notebook.getCellCount();
            
            return ToolResult.success(output);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to delete cell", e);
            return ToolResult.error("Failed to delete cell: " + e.getMessage());
        }
    }
    
    /**
     * 执行 Cell
     */
    private ToolResult<Output> executeCell(Path path, Input input) {
        try {
            Notebook notebook = parser.parse(path);
            
            NotebookCell cell = findCell(notebook, input);
            if (cell == null) {
                return ToolResult.error("Cell not found");
            }
            
            if (!cell.isCode()) {
                return ToolResult.error("Cannot execute non-code cell");
            }
            
            // 执行 Cell
            String executionResult = executeCellContent(cell, notebook.getLanguage());
            
            // 保存
            parser.save(notebook, path);
            
            Output output = new Output();
            output.success = true;
            output.message = "Cell executed successfully";
            output.notebookPath = path.toString();
            output.cellId = cell.getId();
            output.cellIndex = notebook.getCells().indexOf(cell);
            output.executionResult = executionResult;
            output.cells = convertCells(notebook.getCells());
            
            return ToolResult.success(output);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to execute cell", e);
            return ToolResult.error("Failed to execute cell: " + e.getMessage());
        }
    }
    
    /**
     * 移动 Cell
     */
    private ToolResult<Output> moveCell(Path path, Input input) {
        try {
            if (input.target_index == null) {
                return ToolResult.error("target_index is required for move operation");
            }
            
            Notebook notebook = parser.parse(path);
            
            int fromIndex;
            if (input.cell_index != null) {
                fromIndex = input.cell_index;
            } else if (input.cell_id != null) {
                NotebookCell cell = notebook.getCellById(input.cell_id);
                if (cell == null) {
                    return ToolResult.error("Cell not found: " + input.cell_id);
                }
                fromIndex = notebook.getCells().indexOf(cell);
            } else {
                return ToolResult.error("Either cell_index or cell_id is required");
            }
            
            boolean success = notebook.moveCell(fromIndex, input.target_index);
            if (!success) {
                return ToolResult.error("Failed to move cell: invalid index");
            }
            
            // 保存
            parser.save(notebook, path);
            
            Output output = new Output();
            output.success = true;
            output.message = "Cell moved from " + fromIndex + " to " + input.target_index;
            output.notebookPath = path.toString();
            output.cellIndex = input.target_index;
            output.cells = convertCells(notebook.getCells());
            
            return ToolResult.success(output);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to move cell", e);
            return ToolResult.error("Failed to move cell: " + e.getMessage());
        }
    }
    
    /**
     * 查找 Cell
     */
    private NotebookCell findCell(Notebook notebook, Input input) {
        if (input.cell_id != null) {
            return notebook.getCellById(input.cell_id);
        }
        if (input.cell_index != null) {
            return notebook.getCell(input.cell_index);
        }
        return null;
    }
    
    /**
     * 解析 Cell 类型
     */
    private NotebookCell.CellType parseCellType(String type) {
        if (type == null) {
            return null;
        }
        try {
            return NotebookCell.CellType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * 执行 Cell 内容
     */
    private String executeCellContent(NotebookCell cell, String language) {
        if (!cell.isCode() || language == null) {
            return null;
        }
        
        REPLExecutor executor = replFactory.getExecutor(language);
        if (executor == null) {
            cell.addOutput(createErrorOutput("REPL not available for language: " + language));
            return null;
        }
        
        String code = cell.getSourceString();
        REPLExecutor.ExecutionResult result = executor.execute(code);
        
        // 清除之前的输出
        cell.clearOutputs();
        
        // 添加新的输出
        if (result.success()) {
            if (result.output() != null && !result.output().isEmpty()) {
                cell.addOutput(createStreamOutput(result.output()));
            }
        } else {
            cell.addOutput(createErrorOutput(result.error()));
        }
        
        // 更新执行计数
        Integer currentCount = cell.getExecutionCount();
        cell.setExecutionCount(currentCount != null ? currentCount + 1 : 1);
        
        return result.success() ? result.output() : result.error();
    }
    
    /**
     * 创建流输出
     */
    private NotebookCell.CellOutput createStreamOutput(String text) {
        NotebookCell.CellOutput output = new NotebookCell.CellOutput();
        output.setOutputType("stream");
        output.setText(text);
        output.setName("stdout");
        return output;
    }
    
    /**
     * 创建错误输出
     */
    private NotebookCell.CellOutput createErrorOutput(String error) {
        NotebookCell.CellOutput output = new NotebookCell.CellOutput();
        output.setOutputType("error");
        output.setText(error);
        return output;
    }
    
    /**
     * 转换 Cells 为输出格式
     */
    private List<CellInfo> convertCells(List<NotebookCell> cells) {
        if (cells == null) {
            return new ArrayList<>();
        }
        
        List<CellInfo> result = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            NotebookCell cell = cells.get(i);
            CellInfo info = new CellInfo();
            info.index = i;
            info.id = cell.getId();
            info.type = cell.getCellType().name().toLowerCase();
            info.content = cell.getSourceString();
            info.executionCount = cell.getExecutionCount();
            info.outputCount = cell.getOutputs() != null ? cell.getOutputs().size() : 0;
            result.add(info);
        }
        return result;
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input == null) {
            return builder.addError("输入不能为空").build();
        }
        
        if (input.path == null || input.path.trim().isEmpty()) {
            builder.addError("path 是必需的");
        }
        
        if (input.operation == null) {
            builder.addError("operation 是必需的");
        } else {
            Set<String> validOps = Set.of("read", "edit", "add", "delete", "execute", "move");
            if (!validOps.contains(input.operation.toLowerCase())) {
                builder.addError("无效的操作: " + input.operation);
            }
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        if (input == null || input.operation == null) {
            return false;
        }
        return "read".equalsIgnoreCase(input.operation);
    }
    
    @Override
    public boolean isDestructive(Input input) {
        if (input == null || input.operation == null) {
            return false;
        }
        return "delete".equalsIgnoreCase(input.operation);
    }
    
    @Override
    public boolean requiresApproval(Input input) {
        return isDestructive(input);
    }
    
    /**
     * 输入类型
     */
    public static class Input {
        public String path;
        public String operation;
        public Integer cell_index;
        public String cell_id;
        public String cell_type;
        public String content;
        public Integer target_index;
        public Boolean execute;
        public String language;
        
        public Input() {}
    }
    
    /**
     * 输出类型
     */
    public static class Output {
        public boolean success;
        public String message;
        public String notebookPath;
        public String cellId;
        public Integer cellIndex;
        public Integer cellCount;
        public String language;
        public String executionResult;
        public List<CellInfo> cells;
        
        public Output() {}
    }
    
    /**
     * Cell 信息
     */
    public static class CellInfo {
        public int index;
        public String id;
        public String type;
        public String content;
        public Integer executionCount;
        public int outputCount;
    }
    
    /**
     * 进度类型
     */
    public static class Progress {
        private final String operation;
        private final int progress;
        
        public Progress(String operation, int progress) {
            this.operation = operation;
            this.progress = progress;
        }
        
        public String getOperation() { return operation; }
        public int getProgress() { return progress; }
    }
}
