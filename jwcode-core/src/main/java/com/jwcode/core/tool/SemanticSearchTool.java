package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.index.CodebaseIndexer;
import com.jwcode.core.index.VectorStore;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SemanticSearchTool — 语义搜索工具。
 *
 * <p>允许子Agent 用自然语言搜索代码库，基于 {@link CodebaseIndexer} 的向量索引。
 * 是传统 {@link GrepTool} / {@link GlobTool} 关键词搜索的语义增强版。</p>
 *
 * <p>输入参数：
 * <ul>
 *   <li>query — 自然语言搜索查询（必填）</li>
 *   <li>topK — 返回结果数（可选，默认 10）</li>
 *   <li>fileTypes — 文件扩展名过滤（可选，如 .java,.ts）</li>
 * </ul>
 * </p>
 */
public class SemanticSearchTool implements Tool<SemanticSearchTool.SemanticSearchInput,
        SemanticSearchTool.SemanticSearchOutput, Void> {

    private static final Logger logger = Logger.getLogger(SemanticSearchTool.class.getName());

    private final CodebaseIndexer indexer;

    public SemanticSearchTool(CodebaseIndexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public String getName() {
        return "SemanticSearchTool";
    }

    @Override
    public String getDescription() {
        return "用自然语言语义搜索代码库。在需要理解代码意图而非精确关键词匹配时使用。";
    }

    @Override
    public String getPrompt() {
        return """
            使用 SemanticSearchTool 用自然语言搜索代码库。

            适用场景：
            - "找用户认证相关的代码" （语义理解，而非关键词 grep "auth"）
            - "项目中怎么处理异常日志的"
            - "数据库连接池的配置在哪里"

            不适用场景：
            - 精确字符串搜索 → 用 GrepTool
            - 按文件名查找 → 用 GlobTool

            参数:
            - query: 自然语言查询（必填）
            - topK: 返回结果数（可选，默认: 10，最大: 20）
            - fileTypes: 文件扩展名过滤（可选，如 "java,ts"）

            示例:
            - {"query": "用户登录认证逻辑"}
            - {"query": "WebSocket消息广播", "topK": 5}
            - {"query": "配置加载", "fileTypes": "java", "topK": 10}
            """;
    }

    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(SemanticSearchInput.class);
    }

    @Override
    public TypeReference<SemanticSearchInput> getInputType() {
        return new TypeReference<SemanticSearchInput>() {};
    }

    @Override
    public TypeReference<SemanticSearchOutput> getOutputType() {
        return new TypeReference<SemanticSearchOutput>() {};
    }

    @Override
    public CompletableFuture<ToolResult<SemanticSearchOutput>> call(
            SemanticSearchInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                if (input.query == null || input.query.isBlank()) {
                    return ToolResult.error("query 参数不能为空");
                }

                // 参数约束
                int k = Math.min(Math.max(input.topK, 1), 20);

                Set<String> fileTypes = null;
                if (input.fileTypes != null && !input.fileTypes.isBlank()) {
                    fileTypes = Arrays.stream(input.fileTypes.split(","))
                        .map(String::trim)
                        .map(ft -> ft.startsWith(".") ? ft.toLowerCase() : "." + ft.toLowerCase())
                        .collect(Collectors.toSet());
                }

                logger.fine("SemanticSearch: query='" + input.query + "', topK=" + k
                    + ", fileTypes=" + fileTypes);

                // 执行搜索
                List<VectorStore.SearchResult> results = indexer.search(
                    input.query, k, fileTypes);

                // 格式化输出
                SemanticSearchOutput output = new SemanticSearchOutput();
                output.query = input.query;
                output.totalResults = results.size();
                output.results = new ArrayList<>();

                for (VectorStore.SearchResult r : results) {
                    SearchResultItem item = new SearchResultItem();
                    item.filePath = r.getFilePath();
                    item.startLine = r.getStartLine();
                    item.endLine = r.getEndLine();
                    item.similarity = Math.round(r.getSimilarity() * 10000.0) / 100.0; // 保留两位
                    item.language = r.getMetadata().get("language") != null
                        ? r.getMetadata().get("language").toString() : "Unknown";
                    item.codeSnippet = r.getChunkText();
                    output.results.add(item);
                }

                if (output.results.isEmpty()) {
                    output.summary = "未找到匹配结果。建议：尝试更通用的描述，或使用 GrepTool 进行精确搜索。";
                } else {
                    output.summary = "找到 " + output.totalResults + " 个相关代码片段。";
                }

                output.summary = output.summary + " (" + output.totalResults + " 条结果)";
                return ToolResult.success(output);

            } catch (Exception e) {
                logger.warning("SemanticSearch failed: " + e.getMessage());
                return ToolResult.error("语义搜索失败: " + e.getMessage()
                    + "。请尝试使用 GrepTool。");
            }
        });
    }

    @Override
    public ToolValidationResult validate(SemanticSearchInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.query == null || input.query.isBlank()) {
            return ToolValidationResult.invalid("query 不能为空");
        }
        return ToolValidationResult.valid();
    }

    @Override
    public boolean isReadOnly(SemanticSearchInput input) {
        return true;
    }

    // ==================== 输入/输出模型 ====================

    /**
     * 搜索输入
     */
    public static class SemanticSearchInput {
        /** 自然语言查询 */
        public String query;

        /** 返回数量（默认 10） */
        public int topK = 10;

        /** 文件扩展名过滤（逗号分隔，如 "java,ts"） */
        public String fileTypes;
    }

    /**
     * 搜索输出
     */
    public static class SemanticSearchOutput {
        public String query;
        public String summary;
        public int totalResults;
        public List<SearchResultItem> results;
    }

    /**
     * 单条搜索结果
     */
    public static class SearchResultItem {
        public String filePath;
        public int startLine;
        public int endLine;
        public String language;
        public double similarity; // 0-100 百分比
        public String codeSnippet;
    }
}
