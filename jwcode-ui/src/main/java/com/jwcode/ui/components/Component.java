package com.jwcode.ui.components;

/**
 * Component - UI 组件接口
 * 
 * 功能说明：
 * 所有 UI 组件的基础接口。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface Component {
    
    /**
     * 渲染组件
     * @return 渲染后的字符串
     */
    String render();
    
    /**
     * 获取组件宽度
     * @return 宽度
     */
    default int getWidth() {
        return render().length();
    }
    
    /**
     * 获取组件高度
     * @return 高度（默认为 1）
     */
    default int getHeight() {
        return 1;
    }
}