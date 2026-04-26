package com.jwcode.core.code.api;

import com.jwcode.core.code.engine.SyntaxEngine;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 语法查询接口 - 声明式代码查询
 * 
 * <p>查询语法参考 Tree-sitter Query，但更加简洁：</p>
 * <pre>
 * ;; 查找所有方法声明
 * (method_declaration
 *   name: (identifier) @methodName
 *   parameters: (formal_parameters) @params
 * )
 * 
 * ;; 查找所有 public 方法
 * (method_declaration
 *   (modifiers (modifier "public"))
 *   name: (identifier) @name
 * )
 * 
 * ;; 查找特定注解的方法
 * (method_declaration
 *   (modifiers
 *     (annotation
 *       name: (identifier) @anno (#eq? @anno "Test")
 *     )
 *   )
 *   @testMethod
 * )
 * </pre>
 * 
 * <p>支持的操作符：</p>
 * <ul>
 *   <li>{@code @} - 捕获节点</li>
 *   <li>{@code (#eq? @capture "value")} - 字符串相等</li>
 *   <li>{@code (#match? @capture "regex")} - 正则匹配</li>
 *   <li>{@code (#gt? @capture 10)} - 大于</li>
 *   <li>{@code (#child-count? @capture > 2)} - 子节点数量</li>
 * </ul>
 * 
 * @author JwCode Team
 * @since 2.0.0
 */
public interface SyntaxQuery {
    
    /**
     * 获取原始查询模式
     */
    String getPattern();
    
    /**
     * 获取查询针对的语言
     */
    String getTargetLanguage();
    
    // ========== 单文件查询 ==========
    
    /**
     * 在语法树上执行查询
     * @param tree 目标语法树
     * @return 所有匹配结果
     */
    List<QueryMatch> execute(SyntaxTree tree);
    
    /**
     * 在指定节点上执行查询
     * @param node 起始节点
     * @return 所有匹配结果
     */
    List<QueryMatch> execute(SyntaxNode node);
    
    /**
     * 执行查询并返回第一个匹配
     */
    default QueryMatch executeFirst(SyntaxTree tree) {
        List<QueryMatch> matches = execute(tree);
        return matches.isEmpty() ? null : matches.get(0);
    }
    
    // ========== 批量查询 ==========
    
    /**
     * 在多个文件上执行查询
     * @param files 源文件路径列表
     * @param engine 解析引擎
     * @return 带文件路径的匹配结果
     */
    List<QueryMatch> executeBatch(List<Path> files, SyntaxEngine engine);
    
    /**
     * 在项目上执行查询（并行处理）
     * @param projectRoot 项目根目录
     * @param engine 解析引擎
     * @return 项目范围的匹配结果
     */
    List<QueryMatch> executeOnProject(Path projectRoot, SyntaxEngine engine);
    
    // ========== 过滤查询 ==========
    
    /**
     * 带过滤条件的查询
     * @param tree 目标语法树
     * @param filter 匹配结果过滤器
     * @return 过滤后的匹配结果
     */
    List<QueryMatch> execute(SyntaxTree tree, Predicate<QueryMatch> filter);
    
    /**
     * 限制返回数量的查询
     * @param tree 目标语法树
     * @param limit 最大返回数量
     * @return 匹配结果（不超过限制）
     */
    default List<QueryMatch> execute(SyntaxTree tree, int limit) {
        List<QueryMatch> matches = execute(tree);
        return matches.size() <= limit ? matches : matches.subList(0, limit);
    }
    
    // ========== 游标迭代（大结果集） ==========
    
    /**
     * 创建查询游标，用于逐条处理匹配结果
     * 优势：内存友好，适合大结果集
     */
    QueryCursor createCursor(SyntaxTree tree);
    
    /**
     * 查询游标接口
     */
    interface QueryCursor {
        /**
         * 是否有下一个匹配
         */
        boolean hasNext();
        
        /**
         * 获取下一个匹配
         */
        QueryMatch next();
        
        /**
         * 获取已处理数量
         */
        int getProcessedCount();
        
        /**
         * 关闭游标释放资源
         */
        void close();
    }
}
