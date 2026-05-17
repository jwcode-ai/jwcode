package com.jwcode.ui.components;

import com.jwcode.ui.layout.FlexItem;

/**
 * Component - UI 组件接口
 * 
 * 功能说明：
 * 所有 UI 组件的基础接口。v2.0 扩展了布局支持，组件可关联 FlexItem
 * 参与 Flexbox 布局计算。
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
    
    /**
     * 获取与此组件关联的 FlexItem（用于 Flexbox 布局）。
     * 如果组件不参与 Flexbox 布局，返回 null。
     */
    default FlexItem getFlexItem() {
        return null;
    }
    
    /**
     * 设置组件在布局中计算出的位置和尺寸。
     * 在 FlexLayout.layout() 完成后由容器调用。
     *
     * @param x 计算后的 X 坐标
     * @param y 计算后的 Y 坐标
     * @param width 计算后的宽度
     * @param height 计算后的高度
     */
    default void setLayoutBounds(int x, int y, int width, int height) {
    }
    
    /**
     * 获取计算后的 X 坐标
     */
    default int getLayoutX() {
        return 0;
    }
    
    /**
     * 获取计算后的 Y 坐标
     */
    default int getLayoutY() {
        return 0;
    }
}