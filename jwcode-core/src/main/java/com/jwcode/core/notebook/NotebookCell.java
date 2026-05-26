package com.jwcode.core.notebook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.*;

/**
 * Notebook Cell 模型
 * 表示 Jupyter Notebook 中的一个单元格
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotebookCell {
    
    /**
     * Cell 类型
     */
    public enum CellType {
        CODE,
        MARKDOWN,
        RAW
    }
    
    /**
     * Cell 输出
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CellOutput {
        @JsonProperty("output_type")
        private String outputType;
        
        private String text;
        private Map<String, Object> data;
        private Map<String, Object> metadata;
        private String name;
        private Integer executionCount;
        
        public CellOutput() {}
        
        public CellOutput(String outputType, String text) {
            this.outputType = outputType;
            this.text = text;
        }
        
        public String getOutputType() { return outputType; }
        public void setOutputType(String outputType) { this.outputType = outputType; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        @JsonProperty("execution_count")
        public Integer getExecutionCount() { return executionCount; }
        public void setExecutionCount(Integer executionCount) { this.executionCount = executionCount; }
    }
    
    // Cell 唯一标识
    @JsonProperty("id")
    private String id;
    
    // Cell 类型
    @JsonProperty("cell_type")
    private CellType cellType;
    
    // Cell 内容（代码或 Markdown）
    @JsonDeserialize(using = SourceDeserializer.class)
    private List<String> source;
    
    /**
     * 自定义反序列化器：处理 String 或 Array 格式的 source 字段
     */
    public static class SourceDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            List<String> result = new ArrayList<>();
            
            if (p.currentToken().isStructStart()) {
                // 是数组格式
                JsonNode node = p.getCodec().readTree(p);
                if (node.isArray()) {
                    for (JsonNode element : node) {
                        result.add(element.asText());
                    }
                }
            } else if (p.currentToken() == JsonToken.VALUE_STRING) {
                // 是字符串格式 - 转换为单元素列表
                result.add(p.getValueAsString());
            }
            
            return result;
        }
    }
    
    // 执行计数（仅 CODE 类型）
    @JsonProperty("execution_count")
    private Integer executionCount;
    
    // 输出列表（仅 CODE 类型）
    private List<CellOutput> outputs;
    
    // Cell 元数据
    private Map<String, Object> metadata;
    
    // 附件（用于 Markdown 中的图片等）
    private Map<String, Object> attachments;
    
    public NotebookCell() {
        this.source = new ArrayList<>();
        this.outputs = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    public NotebookCell(CellType cellType) {
        this();
        this.cellType = cellType;
        this.id = generateId();
    }
    
    public NotebookCell(CellType cellType, String content) {
        this(cellType);
        setSource(content);
    }
    
    /**
     * 生成唯一 ID
     */
    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    // Getters and Setters
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public CellType getCellType() { return cellType; }
    public void setCellType(CellType cellType) { this.cellType = cellType; }
    
    public List<String> getSource() { return source; }
    public void setSource(List<String> source) { this.source = source; }
    
    /**
     * 设置源代码（字符串形式）
     */
    public void setSource(String source) {
        if (source == null) {
            this.source = new ArrayList<>();
        } else {
            // 按行分割
            this.source = new ArrayList<>(Arrays.asList(source.split("\n")));
            // 确保每行以 \n 结尾（除了最后一行）
            for (int i = 0; i < this.source.size() - 1; i++) {
                this.source.set(i, this.source.get(i) + "\n");
            }
        }
    }
    
    /**
     * 获取源代码（字符串形式）
     */
    public String getSourceString() {
        if (source == null || source.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : source) {
            sb.append(line);
        }
        return sb.toString();
    }
    
    public Integer getExecutionCount() { return executionCount; }
    public void setExecutionCount(Integer executionCount) { this.executionCount = executionCount; }
    
    public List<CellOutput> getOutputs() { return outputs; }
    public void setOutputs(List<CellOutput> outputs) { this.outputs = outputs; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Map<String, Object> getAttachments() { return attachments; }
    public void setAttachments(Map<String, Object> attachments) { this.attachments = attachments; }
    
    // Convenience methods
    
    /**
     * 是否是代码单元格
     */
    public boolean isCode() {
        return cellType == CellType.CODE;
    }
    
    /**
     * 是否是 Markdown 单元格
     */
    public boolean isMarkdown() {
        return cellType == CellType.MARKDOWN;
    }
    
    /**
     * 是否是 Raw 单元格
     */
    public boolean isRaw() {
        return cellType == CellType.RAW;
    }
    
    /**
     * 添加输出
     */
    public void addOutput(CellOutput output) {
        if (outputs == null) {
            outputs = new ArrayList<>();
        }
        outputs.add(output);
    }
    
    /**
     * 清除所有输出
     */
    public void clearOutputs() {
        if (outputs != null) {
            outputs.clear();
        }
    }
    
    /**
     * 设置元数据值
     */
    public void setMetadataValue(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * 获取元数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key) {
        if (metadata == null) {
            return null;
        }
        return (T) metadata.get(key);
    }
    
    /**
     * 克隆 Cell
     */
    public NotebookCell copy() {
        NotebookCell copy = new NotebookCell();
        copy.id = generateId();
        copy.cellType = this.cellType;
        copy.source = new ArrayList<>(this.source);
        copy.executionCount = this.executionCount;
        if (this.outputs != null) {
            copy.outputs = new ArrayList<>(this.outputs);
        }
        if (this.metadata != null) {
            copy.metadata = new HashMap<>(this.metadata);
        }
        if (this.attachments != null) {
            copy.attachments = new HashMap<>(this.attachments);
        }
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("NotebookCell[id=%s, type=%s, lines=%d]", 
            id, cellType, source != null ? source.size() : 0);
    }
}
