package com.jwcode.core.notebook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notebook 解析器
 * 解析和保存 .ipynb 文件（JSON 格式）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class NotebookParser {
    
    private static final Logger logger = Logger.getLogger(NotebookParser.class.getName());
    
    private final ObjectMapper mapper;
    
    public NotebookParser() {
        this.mapper = new ObjectMapper()
            // 序列化
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT)
            // 反序列化
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }
    
    /**
     * 解析 Notebook 文件
     * 
     * @param path 文件路径
     * @return Notebook 对象
     * @throws IOException 解析失败时抛出
     */
    public Notebook parse(Path path) throws IOException {
        logger.info("Parsing notebook: " + path);
        
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
        
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Notebook notebook = parseContent(content);
        notebook.setFilePath(path.toString());
        
        logger.info("Parsed notebook with " + notebook.getCellCount() + " cells");
        return notebook;
    }
    
    /**
     * 解析 Notebook 内容
     * 
     * @param content JSON 内容
     * @return Notebook 对象
     * @throws IOException 解析失败时抛出
     */
    public Notebook parseContent(String content) throws IOException {
        // 先解析为 Jackson 节点以便处理格式兼容性问题
        ObjectNode root = (ObjectNode) mapper.readTree(content);
        
        // 转换 source 字段格式（兼容字符串和数组两种格式）
        normalizeSourceFields(root);
        
        // 确保每个 cell 有 id
        addCellIds(root);
        
        // 转换为 Notebook 对象
        return mapper.treeToValue(root, Notebook.class);
    }
    
    /**
     * 规范化 source 字段格式
     * Jupyter Notebook 中 source 可以是字符串或字符串数组
     */
    private void normalizeSourceFields(ObjectNode root) {
        ArrayNode cells = (ArrayNode) root.get("cells");
        if (cells == null) {
            return;
        }
        
        for (var cell : cells) {
            ObjectNode cellNode = (ObjectNode) cell;
            var sourceNode = cellNode.get("source");
            
            if (sourceNode != null && sourceNode.isTextual()) {
                // 将字符串转换为数组
                String sourceText = sourceNode.asText();
                ArrayNode sourceArray = mapper.createArrayNode();
                if (!sourceText.isEmpty()) {
                    // 按行分割
                    String[] lines = sourceText.split("(?<=\\n)");
                    for (String line : lines) {
                        sourceArray.add(line);
                    }
                }
                cellNode.set("source", sourceArray);
            }
        }
    }
    
    /**
     * 为没有 id 的 cell 添加 id
     */
    private void addCellIds(ObjectNode root) {
        ArrayNode cells = (ArrayNode) root.get("cells");
        if (cells == null) {
            return;
        }
        
        int index = 0;
        for (var cell : cells) {
            ObjectNode cellNode = (ObjectNode) cell;
            if (!cellNode.has("id") || cellNode.get("id").isNull()) {
                cellNode.put("id", generateCellId(index));
            }
            index++;
        }
    }
    
    /**
     * 生成 Cell ID
     */
    private String generateCellId(int index) {
        return String.format("%08x", index);
    }
    
    /**
     * 保存 Notebook 到文件
     * 
     * @param notebook Notebook 对象
     * @param path 文件路径
     * @throws IOException 保存失败时抛出
     */
    public void save(Notebook notebook, Path path) throws IOException {
        logger.info("Saving notebook to: " + path);
        
        // 确保目录存在
        Files.createDirectories(path.getParent());
        
        // 转换为 JSON
        String content = toJson(notebook);
        
        // 写入文件
        Files.writeString(path, content, StandardCharsets.UTF_8);
        
        logger.info("Saved notebook with " + notebook.getCellCount() + " cells");
    }
    
    /**
     * 将 Notebook 转换为 JSON 字符串
     * 
     * @param notebook Notebook 对象
     * @return JSON 字符串
     */
    public String toJson(Notebook notebook) {
        try {
            // 使用自定义的 PrettyPrinter 确保输出格式与 Jupyter 兼容
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
            
            return mapper.writer(printer).writeValueAsString(notebook);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to serialize notebook", e);
            throw new RuntimeException("Failed to serialize notebook", e);
        }
    }
    
    /**
     * 验证 Notebook 格式
     * 
     * @param notebook Notebook 对象
     * @return 验证结果
     */
    public ValidationResult validate(Notebook notebook) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (notebook == null) {
            errors.add("Notebook is null");
            return new ValidationResult(false, errors, warnings);
        }
        
        // 检查版本
        if (notebook.getNbformat() != 4) {
            warnings.add("nbformat is not 4, may not be fully compatible");
        }
        
        // 检查 cells
        if (notebook.getCells() == null || notebook.getCells().isEmpty()) {
            warnings.add("Notebook has no cells");
        } else {
            // 检查每个 cell
            for (int i = 0; i < notebook.getCells().size(); i++) {
                NotebookCell cell = notebook.getCell(i);
                if (cell.getCellType() == null) {
                    errors.add("Cell " + i + " has no cell_type");
                }
                if (cell.getId() == null) {
                    warnings.add("Cell " + i + " has no id");
                }
            }
        }
        
        // 检查元数据
        if (notebook.getMetadata() == null) {
            warnings.add("Notebook has no metadata");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * 验证结果
     */
    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public String getErrorsString() {
            return String.join("\n", errors);
        }
        
        public String getWarningsString() {
            return String.join("\n", warnings);
        }
    }
    
    /**
     * 创建新的空 Notebook
     * 
     * @param kernelName 内核名称
     * @param language 语言
     * @return 新的 Notebook
     */
    public Notebook createNew(String kernelName, String language) {
        return Notebook.create(kernelName, language);
    }
    
    /**
     * 从代码字符串创建 Notebook
     * 
     * @param code 代码
     * @param language 语言
     * @return 新的 Notebook
     */
    public Notebook fromCode(String code, String language) {
        String kernelName = switch (language.toLowerCase()) {
            case "python" -> "python3";
            case "java" -> "java";
            case "javascript", "js" -> "javascript";
            default -> language;
        };
        
        Notebook notebook = Notebook.create(kernelName, language);
        NotebookCell cell = new NotebookCell(NotebookCell.CellType.CODE, code);
        notebook.addCell(cell);
        return notebook;
    }
    
    /**
     * 合并多个 Notebook
     * 
     * @param notebooks 要合并的 Notebook 列表
     * @return 合并后的 Notebook
     */
    public Notebook merge(List<Notebook> notebooks) {
        if (notebooks == null || notebooks.isEmpty()) {
            return new Notebook();
        }
        if (notebooks.size() == 1) {
            return notebooks.get(0).copy();
        }
        
        // 使用第一个 notebook 的元数据
        Notebook result = notebooks.get(0).copy();
        
        // 合并所有 cells
        List<NotebookCell> allCells = new ArrayList<>();
        for (Notebook notebook : notebooks) {
            if (notebook.getCells() != null) {
                for (NotebookCell cell : notebook.getCells()) {
                    allCells.add(cell.copy());
                }
            }
        }
        result.setCells(allCells);
        
        return result;
    }
    
    /**
     * 提取 Notebook 中的所有代码
     * 
     * @param notebook Notebook 对象
     * @return 代码字符串列表
     */
    public List<String> extractCode(Notebook notebook) {
        if (notebook == null || notebook.getCells() == null) {
            return List.of();
        }
        
        return notebook.getCodeCells().stream()
            .map(NotebookCell::getSourceString)
            .toList();
    }
    
    /**
     * 提取 Notebook 中的所有 Markdown
     * 
     * @param notebook Notebook 对象
     * @return Markdown 字符串列表
     */
    public List<String> extractMarkdown(Notebook notebook) {
        if (notebook == null || notebook.getCells() == null) {
            return List.of();
        }
        
        return notebook.getCells().stream()
            .filter(NotebookCell::isMarkdown)
            .map(NotebookCell::getSourceString)
            .toList();
    }
}
