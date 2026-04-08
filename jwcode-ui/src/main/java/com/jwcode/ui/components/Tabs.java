package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabs - 标签页组件
 * 
 * 功能说明：
 * 显示多标签切换界面，支持标签滚动、关闭标签、快捷键等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Tabs implements Component {
    
    private final List<Tab> tabs;
    private int selectedIndex;
    private int scrollOffset;
    private boolean closable;
    private boolean scrollable;
    private TextColor activeColor;
    private TextColor inactiveColor;
    private int maxTabWidth;
    private TabChangeListener listener;
    
    public Tabs() {
        this.tabs = new ArrayList<>();
        this.selectedIndex = 0;
        this.scrollOffset = 0;
        this.closable = true;
        this.scrollable = true;
        this.activeColor = TextColor.ANSI.DEFAULT;
        this.inactiveColor = TextColor.ANSI.DEFAULT;
        this.maxTabWidth = 20;
    }
    
    @Override
    public String render() {
        if (tabs.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 渲染标签栏
        sb.append(renderTabBar()).append("\n");
        
        // 渲染分隔线
        sb.append(renderSeparator()).append("\n");
        
        // 渲染内容区域
        Tab selectedTab = getSelectedTab();
        if (selectedTab != null && selectedTab.content != null) {
            sb.append(selectedTab.content.render());
        }
        
        return sb.toString();
    }
    
    /**
     * 渲染标签栏
     */
    private String renderTabBar() {
        StringBuilder sb = new StringBuilder();
        sb.append("┌");
        
        int visibleCount = 0;
        int maxVisible = getMaxVisibleTabs();
        
        for (int i = scrollOffset; i < tabs.size() && visibleCount < maxVisible; i++) {
            Tab tab = tabs.get(i);
            String tabStr = renderTab(tab, i == selectedIndex);
            sb.append(tabStr);
            visibleCount++;
        }
        
        // 填充剩余空间
        sb.append("─".repeat(Math.max(0, 60 - sb.length())));
        sb.append("┐");
        
        return sb.toString();
    }
    
    /**
     * 渲染单个标签
     */
    private String renderTab(Tab tab, boolean active) {
        StringBuilder sb = new StringBuilder();
        
        if (active) {
            sb.append("┌─");
        } else {
            sb.append("┌─");
        }
        
        String title = truncateTitle(tab.title);
        sb.append(title);
        
        // 填充空格
        int padding = maxTabWidth - title.length() - 2;
        for (int i = 0; i < padding; i++) {
            sb.append(" ");
        }
        
        if (closable && active) {
            sb.append("×");
        }
        
        if (active) {
            sb.append("─┐");
        } else {
            sb.append("─┘");
        }
        
        return sb.toString();
    }
    
    /**
     * 渲染分隔线
     */
    private String renderSeparator() {
        StringBuilder sb = new StringBuilder();
        sb.append("├");
        
        for (int i = 0; i < Math.max(60, maxTabWidth * tabs.size()); i++) {
            sb.append("─");
        }
        
        sb.append("┤");
        return sb.toString();
    }
    
    /**
     * 截断标题
     */
    private String truncateTitle(String title) {
        if (title.length() <= maxTabWidth - 4) {
            return title;
        }
        return title.substring(0, maxTabWidth - 7) + "...";
    }
    
    /**
     * 获取最大可见标签数
     */
    private int getMaxVisibleTabs() {
        return Math.max(1, 60 / maxTabWidth);
    }
    
    /**
     * 添加标签
     */
    public void addTab(String title, Component content) {
        tabs.add(new Tab(title, content));
    }
    
    /**
     * 添加标签（带图标）
     */
    public void addTab(String icon, String title, Component content) {
        tabs.add(new Tab(icon, title, content));
    }
    
    /**
     * 移除标签
     */
    public void removeTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            tabs.remove(index);
            
            // 调整选中索引
            if (selectedIndex >= tabs.size()) {
                selectedIndex = Math.max(0, tabs.size() - 1);
            }
            
            if (listener != null) {
                listener.onTabRemoved(index);
            }
        }
    }
    
    /**
     * 移除标签（按标题）
     */
    public void removeTab(String title) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).title.equals(title)) {
                removeTab(i);
                return;
            }
        }
    }
    
    /**
     * 选择标签
     */
    public void selectTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            int oldIndex = selectedIndex;
            selectedIndex = index;
            
            // 调整滚动
            if (scrollable) {
                adjustScroll();
            }
            
            if (listener != null) {
                listener.onTabChanged(oldIndex, index);
            }
        }
    }
    
    /**
     * 选择下一个标签
     */
    public void selectNext() {
        selectTab((selectedIndex + 1) % tabs.size());
    }
    
    /**
     * 选择上一个标签
     */
    public void selectPrevious() {
        selectTab((selectedIndex - 1 + tabs.size()) % tabs.size());
    }
    
    /**
     * 调整滚动偏移
     */
    private void adjustScroll() {
        int maxVisible = getMaxVisibleTabs();
        
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }
    }
    
    /**
     * 获取选中的标签
     */
    public Tab getSelectedTab() {
        if (tabs.isEmpty()) {
            return null;
        }
        return tabs.get(selectedIndex);
    }
    
    /**
     * 获取选中索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }
    
    /**
     * 获取标签数量
     */
    public int getTabCount() {
        return tabs.size();
    }
    
    /**
     * 设置是否可关闭
     */
    public void setClosable(boolean closable) {
        this.closable = closable;
    }
    
    /**
     * 设置是否可滚动
     */
    public void setScrollable(boolean scrollable) {
        this.scrollable = scrollable;
    }
    
    /**
     * 设置活动颜色
     */
    public void setActiveColor(TextColor color) {
        this.activeColor = color;
    }
    
    /**
     * 设置非活动颜色
     */
    public void setInactiveColor(TextColor color) {
        this.inactiveColor = color;
    }
    
    /**
     * 设置最大标签宽度
     */
    public void setMaxTabWidth(int width) {
        this.maxTabWidth = Math.max(10, width);
    }
    
    /**
     * 设置标签变更监听器
     */
    public void setTabChangeListener(TabChangeListener listener) {
        this.listener = listener;
    }
    
    /**
     * 清空所有标签
     */
    public void clear() {
        tabs.clear();
        selectedIndex = 0;
        scrollOffset = 0;
    }
    
    /**
     * 标签变更监听器接口
     */
    public interface TabChangeListener {
        void onTabChanged(int oldIndex, int newIndex);
        void onTabRemoved(int index);
    }
    
    /**
     * 标签类
     */
    public static class Tab {
        public final String icon;
        public final String title;
        public final Component content;
        public boolean modified;
        
        public Tab(String title, Component content) {
            this(null, title, content);
        }
        
        public Tab(String icon, String title, Component content) {
            this.icon = icon;
            this.title = title;
            this.content = content;
            this.modified = false;
        }
        
        /**
         * 设置修改状态
         */
        public void setModified(boolean modified) {
            this.modified = modified;
        }
        
        /**
         * 获取显示标题
         */
        public String getDisplayTitle() {
            StringBuilder sb = new StringBuilder();
            
            if (icon != null && !icon.isEmpty()) {
                sb.append(icon).append(" ");
            }
            
            if (modified) {
                sb.append("*");
            }
            
            sb.append(title);
            
            return sb.toString();
        }
    }
    
    /**
     * 创建文件编辑器标签
     */
    public static Tab createFileTab(String filename, Component content) {
        Tab tab = new Tab("📄", filename, content);
        return tab;
    }
    
    /**
     * 创建终端标签
     */
    public static Tab createTerminalTab(String name, Component content) {
        Tab tab = new Tab("💻", name, content);
        return tab;
    }
    
    /**
     * 创建预览标签
     */
    public static Tab createPreviewTab(String name, Component content) {
        Tab tab = new Tab("👁", name, content);
        return tab;
    }
}