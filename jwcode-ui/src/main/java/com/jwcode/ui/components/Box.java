package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;
import com.jwcode.ui.layout.Align;
import com.jwcode.ui.layout.FlexDirection;
import com.jwcode.ui.layout.FlexItem;
import com.jwcode.ui.layout.FlexLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Box - 容器组件
 * 
 * <p>v2.0 新增 Flexbox 布局支持。Box 可以作为 Flex 容器，
 * 通过 {@link #setFlexDirection}、{@link #setJustifyContent}、
 * {@link #setAlignItems} 控制子组件的布局行为。</p>
 * 
 * <p>向后兼容：默认 flexDirection=COLUMN，不设置 flex 属性时行为与原来一致。</p>
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Box implements Component {
    
    private Component content;
    private int width;
    private int height;
    private BorderStyle borderStyle;
    private TextColor borderColor;
    private String title;
    private boolean showBorder;
    private int paddingLeft;
    private int paddingRight;
    private int paddingTop;
    private int paddingBottom;
    
    // ========== Flexbox 布局属性 ==========
    
    private FlexDirection flexDirection = FlexDirection.COLUMN;
    private Align justifyContent = Align.FLEX_START;
    private Align alignItems = Align.FLEX_START;
    private float flexGrow = 0f;
    private int minWidth = 0;
    private int maxWidth = Integer.MAX_VALUE;
    private int minHeight = 0;
    private int maxHeight = Integer.MAX_VALUE;
    
    /** 子组件列表（用于 Flexbox 布局） */
    private final List<Component> children = new ArrayList<>();
    
    /** 布局计算后的坐标 */
    private int layoutX = 0;
    private int layoutY = 0;
    
    public Box() {
        this(null, 40, 10);
    }
    
    public Box(Component content, int width, int height) {
        this.content = content;
        this.width = width;
        this.height = height;
        this.borderStyle = BorderStyle.SINGLE;
        this.borderColor = TextColor.ANSI.DEFAULT;
        this.title = null;
        this.showBorder = true;
        this.paddingLeft = 1;
        this.paddingRight = 1;
        this.paddingTop = 0;
        this.paddingBottom = 0;
    }
    
    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        
        if (showBorder) {
            sb.append(renderTopBorder()).append("\n");
        }
        
        // 内容区域
        int contentWidth = width - (showBorder ? 2 : 0);
        int contentHeight = height - (showBorder ? 2 : 0) - paddingTop - paddingBottom;
        
        for (int i = 0; i < paddingTop; i++) {
            if (showBorder) sb.append('│');
            sb.append(repeat(' ', contentWidth));
            if (showBorder) sb.append('│');
            sb.append("\n");
        }
        
        // Flexbox 布局模式：如果有子组件，使用 FlexLayout 计算位置
        if (!children.isEmpty()) {
            renderFlexChildren(sb, contentWidth, contentHeight);
        } else if (content != null) {
            String contentStr = content.render();
            String[] lines = contentStr.split("\n", -1);
            for (String line : lines) {
                if (showBorder) sb.append('│');
                sb.append(padOrTruncate(line, contentWidth));
                if (showBorder) sb.append('│');
                sb.append("\n");
            }
        }
        
        for (int i = 0; i < paddingBottom; i++) {
            if (showBorder) sb.append('│');
            sb.append(repeat(' ', contentWidth));
            if (showBorder) sb.append('│');
            sb.append("\n");
        }
        
        if (showBorder) {
            sb.append(renderBottomBorder());
        }
        
        return sb.toString();
    }
    
    /**
     * 使用 Flexbox 布局渲染子组件。
     */
    private void renderFlexChildren(StringBuilder sb, int contentWidth, int contentHeight) {
        // 构建 FlexItem 列表
        List<FlexItem> flexItems = new ArrayList<>();
        for (Component child : children) {
            FlexItem fi = child.getFlexItem();
            if (fi == null) {
                // 没有显式 FlexItem 的组件，根据其宽高创建默认项
                fi = new FlexItem(child.getWidth(), child.getHeight());
            }
            fi.userData(child);
            flexItems.add(fi);
        }
        
        // 执行 Flexbox 布局计算
        FlexLayout.layout(flexItems, contentWidth, contentHeight,
                flexDirection, justifyContent, alignItems);
        
        // 按计算后的位置渲染每个子组件
        // 先收集所有行的渲染结果
        String[][] grid = new String[contentHeight][contentWidth];
        for (int y = 0; y < contentHeight; y++) {
            for (int x = 0; x < contentWidth; x++) {
                grid[y][x] = " ";
            }
        }
        
        for (FlexItem fi : flexItems) {
            Component child = (Component) fi.getUserData();
            if (child == null) continue;
            
            int cx = fi.getX();
            int cy = fi.getY();
            int cw = fi.getComputedWidth();
            int ch = fi.getComputedHeight();
            
            child.setLayoutBounds(cx, cy, cw, ch);
            
            String childRender = child.render();
            String[] childLines = childRender.split("\n", -1);
            
            for (int ly = 0; ly < Math.min(childLines.length, ch) && cy + ly < contentHeight; ly++) {
                String line = childLines[ly];
                for (int lx = 0; lx < Math.min(line.length(), cw) && cx + lx < contentWidth; lx++) {
                    grid[cy + ly][cx + lx] = String.valueOf(line.charAt(lx));
                }
            }
        }
        
        // 将 grid 写入 sb
        for (int y = 0; y < contentHeight; y++) {
            if (showBorder) sb.append('│');
            for (int x = 0; x < contentWidth; x++) {
                sb.append(grid[y][x]);
            }
            if (showBorder) sb.append('│');
            sb.append("\n");
        }
    }
    
    /**
     * 渲染上边框
     */
    private String renderTopBorder() {
        BorderChars chars = borderStyle.chars;
        StringBuilder sb = new StringBuilder();
        sb.append(chars.topLeft);
        
        if (title != null && !title.isEmpty()) {
            String titleText = " " + title + " ";
            int titleLen = titleText.length();
            int remainingWidth = width - 2 - titleLen;
            int leftLen = remainingWidth / 2;
            int rightLen = remainingWidth - leftLen;
            
            for (int i = 0; i < leftLen; i++) sb.append(chars.horizontal);
            sb.append(titleText);
            for (int i = 0; i < rightLen; i++) sb.append(chars.horizontal);
        } else {
            for (int i = 0; i < width - 2; i++) sb.append(chars.horizontal);
        }
        
        sb.append(chars.topRight);
        return sb.toString();
    }
    
    /**
     * 渲染下边框
     */
    private String renderBottomBorder() {
        BorderChars chars = borderStyle.chars;
        StringBuilder sb = new StringBuilder();
        sb.append(chars.bottomLeft);
        for (int i = 0; i < width - 2; i++) sb.append(chars.horizontal);
        sb.append(chars.bottomRight);
        return sb.toString();
    }
    
    /**
     * 填充或截断字符串
     */
    private String padOrTruncate(String str, int length) {
        if (str == null) str = "";
        if (str.length() >= length) {
            return str.substring(0, length - 3) + "...";
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.toString();
    }
    
    /**
     * 重复字符
     */
    private String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }
    
    /**
     * 设置内容组件
     */
    public void setContent(Component content) {
        this.content = content;
    }
    
    /**
     * 获取内容组件
     */
    public Component getContent() {
        return content;
    }
    
    /**
     * 设置宽度
     */
    public void setWidth(int width) {
        this.width = Math.max(4, width);
    }
    
    /**
     * 设置高度
     */
    public void setHeight(int height) {
        this.height = Math.max(2, height);
    }
    
    /**
     * 设置边框样式
     */
    public void setBorderStyle(BorderStyle style) {
        this.borderStyle = style;
    }
    
    /**
     * 设置边框颜色
     */
    public void setBorderColor(TextColor color) {
        this.borderColor = color;
    }
    
    /**
     * 设置标题
     */
    public void setTitle(String title) {
        this.title = title;
    }
    
    /**
     * 设置是否显示边框
     */
    public void setShowBorder(boolean show) {
        this.showBorder = show;
    }
    
    /**
     * 设置内边距
     */
    public void setPadding(int left, int right, int top, int bottom) {
        this.paddingLeft = left;
        this.paddingRight = right;
        this.paddingTop = top;
        this.paddingBottom = bottom;
    }
    
    // ========== Flexbox 布局方法 ==========
    
    /**
     * 添加子组件（参与 Flexbox 布局）
     */
    public void addChild(Component child) {
        children.add(child);
    }
    
    /**
     * 移除子组件
     */
    public void removeChild(Component child) {
        children.remove(child);
    }
    
    /**
     * 获取所有子组件
     */
    public List<Component> getChildren() {
        return children;
    }
    
    /**
     * 清空子组件
     */
    public void clearChildren() {
        children.clear();
    }
    
    /**
     * 设置 Flexbox 主轴方向
     */
    public void setFlexDirection(FlexDirection direction) {
        this.flexDirection = direction;
    }
    
    /**
     * 获取 Flexbox 主轴方向
     */
    public FlexDirection getFlexDirection() {
        return flexDirection;
    }
    
    /**
     * 设置主轴对齐方式
     */
    public void setJustifyContent(Align align) {
        this.justifyContent = align;
    }
    
    /**
     * 获取主轴对齐方式
     */
    public Align getJustifyContent() {
        return justifyContent;
    }
    
    /**
     * 设置交叉轴对齐方式
     */
    public void setAlignItems(Align align) {
        this.alignItems = align;
    }
    
    /**
     * 获取交叉轴对齐方式
     */
    public Align getAlignItems() {
        return alignItems;
    }
    
    /**
     * 设置 flexGrow（Box 自身作为 FlexItem 时的伸缩比例）
     */
    public void setFlexGrow(float flexGrow) {
        this.flexGrow = flexGrow;
    }
    
    /**
     * 获取 flexGrow
     */
    public float getFlexGrow() {
        return flexGrow;
    }
    
    /**
     * 设置最小宽度
     */
    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
    }
    
    /**
     * 设置最大宽度
     */
    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }
    
    /**
     * 设置最小高度
     */
    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
    }
    
    /**
     * 设置最大高度
     */
    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }
    
    @Override
    public FlexItem getFlexItem() {
        FlexItem fi = new FlexItem(width, height);
        fi.flexGrow(flexGrow)
           .minWidth(minWidth).maxWidth(maxWidth)
           .minHeight(minHeight).maxHeight(maxHeight)
           .padding(paddingLeft, paddingRight, paddingTop, paddingBottom);
        return fi;
    }
    
    @Override
    public void setLayoutBounds(int x, int y, int width, int height) {
        this.layoutX = x;
        this.layoutY = y;
        this.width = width;
        this.height = height;
    }
    
    @Override
    public int getLayoutX() {
        return layoutX;
    }
    
    @Override
    public int getLayoutY() {
        return layoutY;
    }
    
    /**
     * 边框样式枚举
     */
    public enum BorderStyle {
        SINGLE(new BorderChars('┌', '┐', '└', '┘', '─', '│')),
        DOUBLE(new BorderChars('╔', '╗', '╚', '╝', '═', '║')),
        ROUNDED(new BorderChars('╭', '╮', '╰', '╯', '─', '│')),
        NONE(new BorderChars(' ', ' ', ' ', ' ', ' ', ' '));
        
        public final BorderChars chars;
        
        BorderStyle(BorderChars chars) {
            this.chars = chars;
        }
    }
    
    /**
     * 边框字符类
     */
    public static class BorderChars {
        public final char topLeft;
        public final char topRight;
        public final char bottomLeft;
        public final char bottomRight;
        public final char horizontal;
        public final char vertical;
        
        public BorderChars(char topLeft, char topRight, char bottomLeft, 
                          char bottomRight, char horizontal, char vertical) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomLeft = bottomLeft;
            this.bottomRight = bottomRight;
            this.horizontal = horizontal;
            this.vertical = vertical;
        }
    }
}