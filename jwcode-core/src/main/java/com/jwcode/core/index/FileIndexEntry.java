package com.jwcode.core.index;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FileIndexEntry — 单个文件的索引条目。
 *
 * <p>包含文件元信息、分块列表、嵌入向量等。</p>
 */
public class FileIndexEntry {

    /** 文件路径（相对于工作区根） */
    private String relativePath;

    /** 编程语言（从扩展名推断） */
    private String language;

    /** 文件大小（字节） */
    private long fileSize;

    /** 内容哈希（用于增量更新检测） */
    private String contentHash;

    /** 符号表（类名、方法名、函数名等） */
    private List<String> symbols;

    /** 代码块列表 */
    private List<Chunk> chunks;

    /** 最后修改时间 */
    private Instant lastModified;

    /** 索引时间 */
    private Instant indexedAt;

    // ==================== 内部类 ====================

    /**
     * 代码块：一段连续的代码文本及其嵌入向量
     */
    public static class Chunk {
        /** 块ID（全局唯一） */
        private String chunkId;

        /** 块文本内容 */
        private String text;

        /** 起始行号（1-based） */
        private int startLine;

        /** 结束行号（1-based） */
        private int endLine;

        /** 嵌入向量 */
        private float[] embedding;

        /** 额外元数据（如所属函数名） */
        private Map<String, String> metadata;

        public Chunk() {}

        public Chunk(String chunkId, String text, int startLine, int endLine) {
            this.chunkId = chunkId;
            this.text = text;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        public String getChunkId() { return chunkId; }
        public void setChunkId(String chunkId) { this.chunkId = chunkId; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public int getStartLine() { return startLine; }
        public void setStartLine(int startLine) { this.startLine = startLine; }

        public int getEndLine() { return endLine; }
        public void setEndLine(int endLine) { this.endLine = endLine; }

        public float[] getEmbedding() { return embedding; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }

        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Chunk)) return false;
            Chunk chunk = (Chunk) o;
            return Objects.equals(chunkId, chunk.chunkId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkId);
        }
    }

    // ==================== 构造函数 ====================

    public FileIndexEntry() {}

    public FileIndexEntry(String relativePath, String language, long fileSize,
                          String contentHash, Instant lastModified) {
        this.relativePath = relativePath;
        this.language = language;
        this.fileSize = fileSize;
        this.contentHash = contentHash;
        this.lastModified = lastModified;
        this.indexedAt = Instant.now();
    }

    // ==================== 便捷方法 ====================

    /**
     * 从文件扩展名推断语言
     */
    public static String inferLanguage(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".java")) return "Java";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "Kotlin";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "TypeScript";
        if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".mjs")) return "JavaScript";
        if (name.endsWith(".py") || name.endsWith(".pyi")) return "Python";
        if (name.endsWith(".go")) return "Go";
        if (name.endsWith(".rs")) return "Rust";
        if (name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".cxx")) return "C++";
        if (name.endsWith(".c")) return "C";
        if (name.endsWith(".h") || name.endsWith(".hpp")) return "C/C++ Header";
        if (name.endsWith(".rb")) return "Ruby";
        if (name.endsWith(".php")) return "PHP";
        if (name.endsWith(".swift")) return "Swift";
        if (name.endsWith(".cs")) return "C#";
        if (name.endsWith(".vue")) return "Vue";
        if (name.endsWith(".svelte")) return "Svelte";
        if (name.endsWith(".sql")) return "SQL";
        if (name.endsWith(".xml")) return "XML";
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return "YAML";
        if (name.endsWith(".json")) return "JSON";
        if (name.endsWith(".md") || name.endsWith(".mdx")) return "Markdown";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "HTML";
        if (name.endsWith(".css")) return "CSS";
        if (name.endsWith(".scss") || name.endsWith(".sass")) return "SCSS";
        if (name.endsWith(".sh")) return "Shell";
        if (name.endsWith(".bat") || name.endsWith(".cmd")) return "Batch";
        if (name.endsWith(".ps1")) return "PowerShell";
        if (name.endsWith(".properties") || name.endsWith(".cfg") || name.endsWith(".ini")) return "Config";
        if (name.endsWith(".gradle")) return "Gradle";
        return "Unknown";
    }

    // ==================== Getters & Setters ====================

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }

    public List<Chunk> getChunks() { return chunks; }
    public void setChunks(List<Chunk> chunks) { this.chunks = chunks; }

    public Instant getLastModified() { return lastModified; }
    public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }

    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }

    @Override
    public String toString() {
        return "FileIndexEntry{path='" + relativePath + "', lang='" + language
            + "', size=" + fileSize + ", chunks="
            + (chunks != null ? chunks.size() : 0) + "}";
    }
}
