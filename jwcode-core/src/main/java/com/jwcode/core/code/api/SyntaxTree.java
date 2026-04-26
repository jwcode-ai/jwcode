package com.jwcode.core.code.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 统一语法树接口 - 与具体解析器无关的语法树抽象
 * 
 * <p>设计目标：</p>
 * <ul>
 *   <li>屏蔽底层解析器差异（Tree-sitter/ANTLR/LSP）</li>
 *   <li>支持增量更新，高效处理大文件</li>
 *   <li>提供多种序列化格式</li>
 * </ul>
 * 
 * <p>与 Tree-sitter 对比：</p>
 * <pre>
 * Tree-sitter (Rust/C)          SmartAnalyzeTool (Java)
 * Tree                          SyntaxTree
 * Node                          SyntaxNode
 * TreeCursor                    TreeWalker
 * Query                         SyntaxQuery
 * </pre>
 * 
 * @author JwCode Team
 * @since 2.0.0
 * @see SyntaxNode
 * @see SyntaxQuery
 */
public interface SyntaxTree {
    
    /**
     * 获取语言标识符
     * @return 语言ID，如 "java", "rust", "typescript"
     */
    String getLanguage();
    
    /**
     * 获取语言版本
     * @return 语法版本，用于兼容性检查
     */
    default String getLanguageVersion() {
        return "1.0";
    }
    
    /**
     * 获取根节点
     */
    SyntaxNode getRootNode();
    
    /**
     * 获取原始源代码
     */
    String getSource();
    
    /**
     * 获取文件路径（如果有）
     */
    default String getFilePath() {
        return null;
    }
    
    /**
     * 增量更新语法树
     * 
     * <p>核心优化：复用未变更部分的节点，只重新解析变化区域</p>
     * <p>时间复杂度：O(log n) 而非 O(n)</p>
     * 
     * @param edits 文本编辑列表
     * @return 更新后的语法树（可能是新实例或自身修改）
     */
    SyntaxTree edit(List<TextEdit> edits);
    
    /**
     * 获取指定范围的子树
     */
    SyntaxNode getNodeAt(int line, int column);
    
    /**
     * 获取指定字节偏移的节点
     */
    SyntaxNode getNodeAtByte(int byteOffset);
    
    /**
     * 遍历语法树
     */
    TreeWalker getWalker();
    
    /**
     * 检查语法树是否包含错误
     */
    boolean hasErrors();
    
    /**
     * 获取所有错误节点
     */
    List<SyntaxNode> getErrorNodes();
    
    /**
     * 获取语言特定的元数据
     */
    JsonNode getMetadata();
    
    // ========== 序列化方法 ==========
    
    /**
     * 输出为 S-expression 格式
     * 示例：(source_file (class_declaration (identifier "Main") ...))
     */
    String toSexp();
    
    /**
     * 输出为 XML 格式
     */
    String toXml();
    
    /**
     * 输出为 JSON 格式
     */
    JsonNode toJson();
    
    /**
     * 输出为 Graphviz DOT 格式（用于可视化）
     */
    String toDot();
    
    /**
     * 获取树的统计信息
     */
    default TreeStats getStats() {
        return TreeStats.calculate(this);
    }
    
    /**
     * 释放底层资源（如果使用 Native 解析器）
     */
    default void close() {
        // 默认空实现，具体实现可覆盖
    }
}
