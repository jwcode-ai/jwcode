package com.jwcode.core.code.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 语法树节点接口 - 统一节点抽象
 * 
 * <p>设计原则：</p>
 * <ul>
 *   <li>与具体 AST/CST 实现无关</li>
 *   <li>支持错误恢复（missing/error 节点）</li>
 *   <li>提供便捷的树遍历 API</li>
 * </ul>
 * 
 * @author JwCode Team
 * @since 2.0.0
 */
public interface SyntaxNode {
    
    /**
     * 获取节点类型
     * 示例："method_declaration", "identifier", "block"
     */
    String getType();
    
    /**
     * 获取节点的具名类型（如果有）
     * 用于区分匿名节点和具名节点
     */
    default Optional<String> getNamedType() {
        return isNamed() ? Optional.of(getType()) : Optional.empty();
    }
    
    /**
     * 获取节点在源代码中的文本
     */
    String getText();
    
    /**
     * 获取源代码位置范围
     */
    Range getRange();
    
    /**
     * 获取字节偏移范围（UTF-8）
     */
    ByteRange getByteRange();
    
    // ========== 树结构导航 ==========
    
    /**
     * 获取父节点
     */
    Optional<SyntaxNode> getParent();
    
    /**
     * 获取所有子节点
     */
    List<SyntaxNode> getChildren();
    
    /**
     * 获取指定类型的子节点
     */
    default List<SyntaxNode> getChildren(String type) {
        return getChildren().stream()
            .filter(c -> c.getType().equals(type))
            .toList();
    }
    
    /**
     * 获取命名字段子节点
     * 示例：methodNode.getField("name") 获取方法名节点
     */
    Optional<SyntaxNode> getField(String fieldName);
    
    /**
     * 获取第 N 个子节点
     */
    default Optional<SyntaxNode> getChild(int index) {
        List<SyntaxNode> children = getChildren();
        return index >= 0 && index < children.size() 
            ? Optional.of(children.get(index)) 
            : Optional.empty();
    }
    
    /**
     * 获取第一个子节点
     */
    default Optional<SyntaxNode> getFirstChild() {
        return getChild(0);
    }
    
    /**
     * 获取最后一个子节点
     */
    default Optional<SyntaxNode> getLastChild() {
        List<SyntaxNode> children = getChildren();
        return children.isEmpty() 
            ? Optional.empty() 
            : Optional.of(children.get(children.size() - 1));
    }
    
    /**
     * 获取下一个兄弟节点
     */
    Optional<SyntaxNode> getNextSibling();
    
    /**
     * 获取上一个兄弟节点
     */
    Optional<SyntaxNode> getPrevSibling();
    
    // ========== 节点属性 ==========
    
    /**
     * 是否是具名节点（而非匿名 token）
     */
    boolean isNamed();
    
    /**
     * 是否是叶子节点（无子节点）
     */
    default boolean isLeaf() {
        return getChildren().isEmpty();
    }
    
    /**
     * 是否是错误恢复产生的缺失节点
     */
    boolean isMissing();
    
    /**
     * 是否是错误节点
     */
    boolean isError();
    
    /**
     * 是否包含错误（自身或子树中）
     */
    boolean hasError();
    
    /**
     * 是否是注释节点
     */
    default boolean isComment() {
        String type = getType();
        return type != null && (type.contains("comment") || type.contains("Comment"));
    }
    
    /**
     * 是否是空白字符节点
     */
    default boolean isWhitespace() {
        String type = getType();
        return type != null && (type.equals(" ") || type.contains("whitespace"));
    }
    
    // ========== 树遍历查询 ==========
    
    /**
     * 查找第一个匹配的子节点（深度优先）
     */
    Optional<SyntaxNode> findFirst(Predicate<SyntaxNode> predicate);
    
    /**
     * 查找所有匹配的子节点（深度优先）
     */
    List<SyntaxNode> findAll(Predicate<SyntaxNode> predicate);
    
    /**
     * 查找指定类型的所有后代节点
     */
    default List<SyntaxNode> findAll(String type) {
        return findAll(n -> n.getType().equals(type));
    }
    
    /**
     * 使用 CSS 选择器风格的查询
     * 示例：
     * - "method_declaration" 查找所有方法声明
     * - "class_declaration > identifier" 查找类声明的直接子标识符
     * - "[name]" 查找有 name 字段的节点
     */
    List<SyntaxNode> find(String selector);
    
    /**
     * 获取从根节点到当前节点的路径
     */
    List<SyntaxNode> getPath();
    
    /**
     * 检查当前节点是否包含指定节点
     */
    boolean contains(SyntaxNode node);
    
    // ========== 语义信息 ==========
    
    /**
     * 获取节点的语义角色
     * 示例："definition", "reference", "call"
     */
    default Optional<String> getSemanticRole() {
        return Optional.empty();
    }
    
    /**
     * 获取符号名称（如果是标识符或定义节点）
     */
    default Optional<String> getSymbolName() {
        if (getType().contains("identifier") || getType().contains("name")) {
            return Optional.of(getText());
        }
        return getField("name").map(SyntaxNode::getText);
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 转换为 S-expression 字符串
     */
    String toSexp();
    
    /**
     * 获取节点的描述信息（用于调试）
     */
    default String describe() {
        return String.format("%s [%s] '%s'", 
            getType(), 
            getRange(), 
            getText().length() > 30 
                ? getText().substring(0, 30) + "..." 
                : getText()
        );
    }
}
