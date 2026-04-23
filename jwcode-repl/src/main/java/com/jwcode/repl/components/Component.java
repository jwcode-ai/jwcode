package com.jwcode.repl.components;

/**
 * Component - UI 组件接口
 * 
 * 所有 TUI 组件都实现此接口
 */
public interface Component {
    
    /**
     * 渲染组件为字符串
     * @return 渲染后的字符串
     */
    String render();
}
