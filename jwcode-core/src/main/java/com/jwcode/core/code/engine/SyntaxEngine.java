package com.jwcode.core.code.engine;

import com.jwcode.core.code.api.SyntaxQuery;
import com.jwcode.core.code.api.SyntaxTree;
import com.jwcode.core.code.api.TextEdit;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 语法引擎接口 - 解析器的统一抽象
 * 
 * <p>设计目标：</p>
 * <ul>
 *   <li>屏蔽不同解析器的实现差异</li>
 *   <li>支持 Tree-sitter、ANTLR、LSP 等多种后端</li>
 *   <li>提供统一的语言检测和解析能力</li>
 * </ul>
 * 
 * <p>实现类：</p>
 * <ul>
 *   <li>{@link TreeSitterEngine} - Tree-sitter JNI 绑定</li>
 *   <li>{@link LspSyntaxEngine} - Language Server Protocol</li>
 *   <li>{@link AntlrEngine} - ANTLR 生成的解析器</li>
 *   <li>{@link NativeJavaEngine} - Java 原生解析（如 JavaParser）</li>
 * </ul>
 * 
 * @author JwCode Team
 * @since 2.0.0
 * @see SyntaxTree
 * @see SyntaxQuery
 */
public interface SyntaxEngine {
    
    /**
     * 获取引擎名称
     */
    String getName();
    
    /**
     * 获取引擎版本
     */
    String getVersion();
    
    /**
     * 获取支持的语言列表
     */
    List<Language> getSupportedLanguages();
    
    /**
     * 检测文件语言
     * @param file 文件路径
     * @return 检测结果
     */
    Optional<Language> detectLanguage(Path file);
    
    /**
     * 检测源代码语言
     * @param sourceCode 源代码
     * @param hint 提示信息（如文件扩展名）
     * @return 检测结果
     */
    Optional<Language> detectLanguage(String sourceCode, String hint);
    
    // ========== 解析接口 ==========
    
    /**
     * 解析源代码
     * @param source 源代码字符串
     * @param language 目标语言
     * @return 语法树
     * @throws ParseException 解析失败时抛出
     */
    SyntaxTree parse(String source, Language language) throws ParseException;
    
    /**
     * 解析文件
     * @param file 文件路径
     * @return 语法树（包含文件路径信息）
     * @throws ParseException 解析失败时抛出
     */
    default SyntaxTree parseFile(Path file) throws ParseException {
        Language lang = detectLanguage(file)
            .orElseThrow(() -> new ParseException("无法检测文件语言: " + file));
        String source = readFile(file);
        return parse(source, lang);
    }
    
    /**
     * 增量解析
     * @param oldTree 旧语法树（可复用结构）
     * @param source 更新后的源代码
     * @param edits 编辑操作列表
     * @return 更新后的语法树
     */
    SyntaxTree parseIncremental(SyntaxTree oldTree, String source, List<TextEdit> edits);
    
    // ========== 查询接口 ==========
    
    /**
     * 创建查询对象
     * @param pattern 查询模式（类似 Tree-sitter Query 语法）
     * @return 编译后的查询
     * @throws QueryException 查询语法错误时抛出
     */
    SyntaxQuery createQuery(String pattern) throws QueryException;
    
    /**
     * 创建查询对象（带语言特定扩展）
     * @param pattern 查询模式
     * @param language 目标语言（用于语言特定的谓词）
     * @return 编译后的查询
     */
    SyntaxQuery createQuery(String pattern, Language language) throws QueryException;
    
    /**
     * 获取内置查询模板
     * @param language 目标语言
     * @return 该语言的常用查询模板
     */
    List<NamedQuery> getBuiltinQueries(Language language);
    
    // ========== 能力检查 ==========
    
    /**
     * 是否支持指定语言
     */
    boolean supports(Language language);
    
    /**
     * 是否支持增量解析
     */
    boolean supportsIncremental();
    
    /**
     * 是否支持错误恢复
     */
    boolean supportsErrorRecovery();
    
    /**
     * 是否支持查询语言
     */
    boolean supportsQuery();
    
    /**
     * 获取引擎能力报告
     */
    EngineCapabilities getCapabilities();
    
    // ========== 生命周期 ==========
    
    /**
     * 初始化引擎（加载语言库等）
     */
    void initialize();
    
    /**
     * 是否已初始化
     */
    boolean isInitialized();
    
    /**
     * 关闭引擎，释放资源
     */
    void shutdown();
    
    // ========== 工具方法 ==========
    
    private String readFile(Path file) throws ParseException {
        try {
            return java.nio.file.Files.readString(file);
        } catch (Exception e) {
            throw new ParseException("读取文件失败: " + file, e);
        }
    }
}

/**
 * 语言定义
 */
record Language(
    String id,              // "java", "rust"
    String name,            // "Java", "Rust"
    String version,         // 语法版本
    List<String> extensions, // [".java", ".jav"]
    List<String> filePatterns // ["pom.xml"] 用于检测
) {
    public Language(String id, String name, List<String> extensions) {
        this(id, name, "1.0", extensions, List.of());
    }
}

/**
 * 引擎能力报告
 */
record EngineCapabilities(
    String engineName,
    String version,
    List<Language> supportedLanguages,
    boolean incrementalParsing,
    boolean errorRecovery,
    boolean queryLanguage,
    boolean wasmSupport,
    int maxFileSizeMB,
    long typicalParseTimeMs
) {}

/**
 * 命名查询模板
 */
record NamedQuery(
    String name,
    String description,
    String pattern,
    Language language
) {}

/**
 * 解析异常
 */
class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }
    
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * 查询异常
 */
class QueryException extends Exception {
    private final int line;
    private final int column;
    
    public QueryException(String message, int line, int column) {
        super(String.format("%s at line %d, column %d", message, line, column));
        this.line = line;
        this.column = column;
    }
    
    public int getLine() { return line; }
    public int getColumn() { return column; }
}