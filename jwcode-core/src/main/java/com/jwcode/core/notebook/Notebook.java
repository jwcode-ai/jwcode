package com.jwcode.core.notebook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Notebook 模型
 * 表示 Jupyter Notebook 文件
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Notebook {
    
    /**
     * Notebook 元数据
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotebookMetadata {
        @JsonProperty("kernelspec")
        private KernelSpec kernelSpec;
        
        @JsonProperty("language_info")
        private LanguageInfo languageInfo;
        
        private Map<String, Object> additionalProperties = new HashMap<>();
        
        public KernelSpec getKernelSpec() { return kernelSpec; }
        public void setKernelSpec(KernelSpec kernelSpec) { this.kernelSpec = kernelSpec; }
        
        public LanguageInfo getLanguageInfo() { return languageInfo; }
        public void setLanguageInfo(LanguageInfo languageInfo) { this.languageInfo = languageInfo; }
        
        public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
        public void setAdditionalProperties(Map<String, Object> additionalProperties) { 
            this.additionalProperties = additionalProperties; 
        }
    }
    
    /**
     * 内核规范
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KernelSpec {
        private String name;
        private String displayName;
        private String language;
        
        @JsonProperty("display_name")
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
    
    /**
     * 语言信息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LanguageInfo {
        private String name;
        private String version;
        private String mimetype;
        private String codemirrorMode;
        private String nbconvertExporter;
        private String pygmentsLexer;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getMimetype() { return mimetype; }
        public void setMimetype(String mimetype) { this.mimetype = mimetype; }
        
        @JsonProperty("codemirror_mode")
        public String getCodemirrorMode() { return codemirrorMode; }
        public void setCodemirrorMode(String codemirrorMode) { this.codemirrorMode = codemirrorMode; }
        
        @JsonProperty("nbconvert_exporter")
        public String getNbconvertExporter() { return nbconvertExporter; }
        public void setNbconvertExporter(String nbconvertExporter) { this.nbconvertExporter = nbconvertExporter; }
        
        @JsonProperty("pygments_lexer")
        public String getPygmentsLexer() { return pygmentsLexer; }
        public void setPygmentsLexer(String pygmentsLexer) { this.pygmentsLexer = pygmentsLexer; }
    }
    
    // Notebook 格式版本
    @JsonProperty("nbformat")
    private int nbformat = 4;
    
    @JsonProperty("nbformat_minor")
    private int nbformatMinor = 5;
    
    // Notebook 元数据
    private NotebookMetadata metadata;
    
    // Cell 列表
    private List<NotebookCell> cells;
    
    // 文件路径（非 JSON 属性）
    private transient String filePath;
    
    public Notebook() {
        this.cells = new ArrayList<>();
        this.metadata = new NotebookMetadata();
    }
    
    /**
     * 创建空的 Notebook
     * 
     * @param kernelName 内核名称
     * @param language 语言
     * @return 新的 Notebook
     */
    public static Notebook create(String kernelName, String language) {
        Notebook notebook = new Notebook();
        
        KernelSpec kernelSpec = new KernelSpec();
        kernelSpec.setName(kernelName);
        kernelSpec.setDisplayName(kernelName);
        kernelSpec.setLanguage(language);
        notebook.metadata.setKernelSpec(kernelSpec);
        
        LanguageInfo languageInfo = new LanguageInfo();
        languageInfo.setName(language);
        notebook.metadata.setLanguageInfo(languageInfo);
        
        return notebook;
    }
    
    // Getters and Setters
    
    public int getNbformat() { return nbformat; }
    public void setNbformat(int nbformat) { this.nbformat = nbformat; }
    
    @JsonProperty("nbformat_minor")
    public int getNbformatMinor() { return nbformatMinor; }
    public void setNbformatMinor(int nbformatMinor) { this.nbformatMinor = nbformatMinor; }
    
    public NotebookMetadata getMetadata() { return metadata; }
    public void setMetadata(NotebookMetadata metadata) { this.metadata = metadata; }
    
    public List<NotebookCell> getCells() { return cells; }
    public void setCells(List<NotebookCell> cells) { this.cells = cells; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    // Convenience methods
    
    /**
     * 获取内核名称
     */
    public String getKernelName() {
        if (metadata != null && metadata.getKernelSpec() != null) {
            return metadata.getKernelSpec().getName();
        }
        return null;
    }
    
    /**
     * 获取语言
     */
    public String getLanguage() {
        if (metadata != null && metadata.getLanguageInfo() != null) {
            return metadata.getLanguageInfo().getName();
        }
        if (metadata != null && metadata.getKernelSpec() != null) {
            return metadata.getKernelSpec().getLanguage();
        }
        return null;
    }
    
    /**
     * 添加 Cell
     */
    public void addCell(NotebookCell cell) {
        if (cells == null) {
            cells = new ArrayList<>();
        }
        cells.add(cell);
    }
    
    /**
     * 在指定位置插入 Cell
     */
    public void insertCell(int index, NotebookCell cell) {
        if (cells == null) {
            cells = new ArrayList<>();
        }
        cells.add(index, cell);
    }
    
    /**
     * 移除 Cell
     */
    public boolean removeCell(NotebookCell cell) {
        if (cells == null) {
            return false;
        }
        return cells.remove(cell);
    }
    
    /**
     * 移除指定位置的 Cell
     */
    public NotebookCell removeCell(int index) {
        if (cells == null || index < 0 || index >= cells.size()) {
            return null;
        }
        return cells.remove(index);
    }
    
    /**
     * 获取指定位置的 Cell
     */
    public NotebookCell getCell(int index) {
        if (cells == null || index < 0 || index >= cells.size()) {
            return null;
        }
        return cells.get(index);
    }
    
    /**
     * 通过 ID 获取 Cell
     */
    public NotebookCell getCellById(String id) {
        if (cells == null || id == null) {
            return null;
        }
        return cells.stream()
            .filter(c -> id.equals(c.getId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取 Cell 数量
     */
    public int getCellCount() {
        return cells != null ? cells.size() : 0;
    }
    
    /**
     * 获取代码 Cell 数量
     */
    public long getCodeCellCount() {
        if (cells == null) {
            return 0;
        }
        return cells.stream().filter(NotebookCell::isCode).count();
    }
    
    /**
     * 获取 Markdown Cell 数量
     */
    public long getMarkdownCellCount() {
        if (cells == null) {
            return 0;
        }
        return cells.stream().filter(NotebookCell::isMarkdown).count();
    }
    
    /**
     * 清除所有输出
     */
    public void clearAllOutputs() {
        if (cells != null) {
            for (NotebookCell cell : cells) {
                cell.clearOutputs();
            }
        }
    }
    
    /**
     * 获取所有代码 Cell
     */
    public List<NotebookCell> getCodeCells() {
        if (cells == null) {
            return List.of();
        }
        return cells.stream()
            .filter(NotebookCell::isCode)
            .toList();
    }
    
    /**
     * 移动 Cell
     */
    public boolean moveCell(int fromIndex, int toIndex) {
        if (cells == null || fromIndex < 0 || fromIndex >= cells.size() 
            || toIndex < 0 || toIndex >= cells.size()) {
            return false;
        }
        NotebookCell cell = cells.remove(fromIndex);
        cells.add(toIndex, cell);
        return true;
    }
    
    /**
     * 创建副本
     */
    public Notebook copy() {
        Notebook copy = new Notebook();
        copy.nbformat = this.nbformat;
        copy.nbformatMinor = this.nbformatMinor;
        
        if (this.metadata != null) {
            // 深度复制元数据
            copy.metadata = new NotebookMetadata();
            if (this.metadata.getKernelSpec() != null) {
                KernelSpec ks = new KernelSpec();
                ks.setName(this.metadata.getKernelSpec().getName());
                ks.setDisplayName(this.metadata.getKernelSpec().getDisplayName());
                ks.setLanguage(this.metadata.getKernelSpec().getLanguage());
                copy.metadata.setKernelSpec(ks);
            }
            if (this.metadata.getLanguageInfo() != null) {
                LanguageInfo li = new LanguageInfo();
                li.setName(this.metadata.getLanguageInfo().getName());
                li.setVersion(this.metadata.getLanguageInfo().getVersion());
                copy.metadata.setLanguageInfo(li);
            }
        }
        
        if (this.cells != null) {
            copy.cells = new ArrayList<>();
            for (NotebookCell cell : this.cells) {
                copy.cells.add(cell.copy());
            }
        }
        
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("Notebook[format=%d.%d, cells=%d, language=%s]",
            nbformat, nbformatMinor, getCellCount(), getLanguage());
    }
}
