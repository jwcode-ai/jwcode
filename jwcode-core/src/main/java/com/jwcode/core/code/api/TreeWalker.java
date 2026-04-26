package com.jwcode.core.code.api;

import java.util.function.Consumer;

/**
 * 语法树遍历器 - 用于遍历语法树的游标
 * 
 * <p>对应 Tree-sitter 的 TreeCursor</p>
 * 
 * @author JwCode Team
 * @since 2.0.0
 */
public interface TreeWalker {
    
    /**
     * 是否还有下一个节点
     */
    boolean hasNext();
    
    /**
     * 获取下一个节点
     */
    SyntaxNode next();
    
    /**
     * 进入子节点
     */
    void enter();
    
    /**
     * 退出当前节点
     */
    void exit();
    
    /**
     * 获取当前节点
     */
    SyntaxNode current();
    
    /**
     * 遍历所有节点（深度优先）
     */
    default void walk(Consumer<SyntaxNode> consumer) {
        while (hasNext()) {
            SyntaxNode node = next();
            consumer.accept(node);
            
            if (!node.getChildren().isEmpty()) {
                enter();
            }
        }
    }
    
    /**
     * 重置遍历器到根节点
     */
    void reset();
    
    /**
     * 关闭遍历器释放资源
     */
    default void close() {
        // 默认空实现
    }
}
