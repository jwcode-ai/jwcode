package com.jwcode.ui.layout;

/**
 * FlexItem - Flexbox 布局项模型
 *
 * <p>表示参与 Flexbox 布局的单个项目，包含输入属性（flexGrow、padding、margin 等）
 * 和输出结果（计算后的 x、y、width、height）。</p>
 */
public class FlexItem {

    // ========== 输入属性 ==========

    /** 伸缩增长比例（0 = 不增长） */
    private float flexGrow = 0f;

    /** 伸缩收缩比例（0 = 不收缩） */
    private float flexShrink = 1f;

    /** 主轴基础尺寸（-1 = AUTO） */
    private int flexBasis = -1;

    /** 固定宽度（-1 = AUTO） */
    private int width = -1;

    /** 固定高度（-1 = AUTO） */
    private int height = -1;

    /** 最小宽度 */
    private int minWidth = 0;

    /** 最大宽度 */
    private int maxWidth = Integer.MAX_VALUE;

    /** 最小高度 */
    private int minHeight = 0;

    /** 最大高度 */
    private int maxHeight = Integer.MAX_VALUE;

    /** 左内边距 */
    private int paddingLeft = 0;
    /** 右内边距 */
    private int paddingRight = 0;
    /** 上内边距 */
    private int paddingTop = 0;
    /** 下内边距 */
    private int paddingBottom = 0;

    /** 左外边距 */
    private int marginLeft = 0;
    /** 右外边距 */
    private int marginRight = 0;
    /** 上外边距 */
    private int marginTop = 0;
    /** 下外边距 */
    private int marginBottom = 0;

    /** 交叉轴对齐方式（null = 继承容器） */
    private Align alignSelf = null;

    // ========== 输出结果（由 FlexLayout.layout() 填充） ==========

    /** 计算后的 X 坐标 */
    private int x = 0;
    /** 计算后的 Y 坐标 */
    private int y = 0;
    /** 计算后的宽度 */
    private int computedWidth = 0;
    /** 计算后的高度 */
    private int computedHeight = 0;

    // ========== 内部辅助 ==========

    /** 用户数据引用（可选，用于关联回组件） */
    private Object userData;

    public FlexItem() {
    }

    public FlexItem(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // ========== 链式设置 ==========

    public FlexItem flexGrow(float flexGrow) {
        this.flexGrow = flexGrow;
        return this;
    }

    public FlexItem flexShrink(float flexShrink) {
        this.flexShrink = flexShrink;
        return this;
    }

    public FlexItem flexBasis(int flexBasis) {
        this.flexBasis = flexBasis;
        return this;
    }

    public FlexItem width(int width) {
        this.width = width;
        return this;
    }

    public FlexItem height(int height) {
        this.height = height;
        return this;
    }

    public FlexItem minWidth(int minWidth) {
        this.minWidth = minWidth;
        return this;
    }

    public FlexItem maxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    public FlexItem minHeight(int minHeight) {
        this.minHeight = minHeight;
        return this;
    }

    public FlexItem maxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        return this;
    }

    public FlexItem padding(int all) {
        return padding(all, all, all, all);
    }

    public FlexItem padding(int left, int right, int top, int bottom) {
        this.paddingLeft = left;
        this.paddingRight = right;
        this.paddingTop = top;
        this.paddingBottom = bottom;
        return this;
    }

    public FlexItem margin(int all) {
        return margin(all, all, all, all);
    }

    public FlexItem margin(int left, int right, int top, int bottom) {
        this.marginLeft = left;
        this.marginRight = right;
        this.marginTop = top;
        this.marginBottom = bottom;
        return this;
    }

    public FlexItem alignSelf(Align alignSelf) {
        this.alignSelf = alignSelf;
        return this;
    }

    public FlexItem userData(Object userData) {
        this.userData = userData;
        return this;
    }

    // ========== Getters ==========

    public float getFlexGrow() { return flexGrow; }
    public float getFlexShrink() { return flexShrink; }
    public int getFlexBasis() { return flexBasis; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getMinWidth() { return minWidth; }
    public int getMaxWidth() { return maxWidth; }
    public int getMinHeight() { return minHeight; }
    public int getMaxHeight() { return maxHeight; }
    public int getPaddingLeft() { return paddingLeft; }
    public int getPaddingRight() { return paddingRight; }
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingBottom() { return paddingBottom; }
    public int getMarginLeft() { return marginLeft; }
    public int getMarginRight() { return marginRight; }
    public int getMarginTop() { return marginTop; }
    public int getMarginBottom() { return marginBottom; }
    public Align getAlignSelf() { return alignSelf; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getComputedWidth() { return computedWidth; }
    public int getComputedHeight() { return computedHeight; }
    public Object getUserData() { return userData; }

    // ========== Setters（由 FlexLayout 调用） ==========

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setComputedWidth(int computedWidth) {
        this.computedWidth = Math.max(minWidth, Math.min(maxWidth, computedWidth));
    }
    public void setComputedHeight(int computedHeight) {
        this.computedHeight = Math.max(minHeight, Math.min(maxHeight, computedHeight));
    }

    /** 获取主轴上的外边距总和 */
    public int getMarginMain(boolean isRow) {
        return isRow ? (marginLeft + marginRight) : (marginTop + marginBottom);
    }

    /** 获取交叉轴上的外边距总和 */
    public int getMarginCross(boolean isRow) {
        return isRow ? (marginTop + marginBottom) : (marginLeft + marginRight);
    }

    /** 获取主轴上的内边距总和 */
    public int getPaddingMain(boolean isRow) {
        return isRow ? (paddingLeft + paddingRight) : (paddingTop + paddingBottom);
    }

    /** 获取交叉轴上的内边距总和 */
    public int getPaddingCross(boolean isRow) {
        return isRow ? (paddingTop + paddingBottom) : (paddingLeft + paddingRight);
    }

    @Override
    public String toString() {
        return "FlexItem{" +
                "x=" + x + ", y=" + y +
                ", w=" + computedWidth + ", h=" + computedHeight +
                ", flexGrow=" + flexGrow +
                '}';
    }
}
