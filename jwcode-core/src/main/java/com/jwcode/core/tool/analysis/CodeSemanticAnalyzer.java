package com.jwcode.core.tool.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 代码语义分析器接口
 *
 * <p>为 {@link com.jwcode.core.tool.SmartAnalyzeTool} 提供可选的代码分析能力扩展。
 * 实现类负责语法树解析、符号图谱构建、语法查询等深度代码理解任务。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>接口位于 tool 包内，不反向依赖 code 子系统的具体实现</li>
 *   <li>查询结果使用 {@code List<Map<String, Object>>} 保持松散耦合</li>
 *   <li>所有方法允许返回 null 或空集合表示不可用</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 2.0.0
 */
public interface CodeSemanticAnalyzer {

    /**
     * 对指定项目进行代码语义分析
     *
     * @param projectRoot 项目根目录
     * @return 分析结果；若分析器未初始化或不可用则返回 null
     */
    CodeAnalysisResult analyze(Path projectRoot);

    /**
     * 执行语法查询
     *
     * @param projectRoot   项目根目录
     * @param queryPattern  查询模式（如 Tree-sitter Query 语法）
     * @return 匹配结果列表；无结果或失败时返回空列表
     */
    List<Map<String, Object>> query(Path projectRoot, String queryPattern);

    /**
     * 使用内置模板执行查询
     *
     * @param projectRoot    项目根目录
     * @param language       语言标识（如 "java", "rust"）
     * @param templateName   模板名称（如 "java-public-methods"）
     * @return 匹配结果列表；无结果或失败时返回空列表
     */
    List<Map<String, Object>> queryByTemplate(Path projectRoot, String language, String templateName);

    /**
     * 代码分析结果数据对象
     */
    record CodeAnalysisResult(
        int parsedFiles,
        int cachedFiles,
        int symbolNodes,
        int symbolEdges,
        List<Map<String, Object>> queryMatches
    ) {}
}
