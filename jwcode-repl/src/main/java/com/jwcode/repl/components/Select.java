package com.jwcode.repl.components;

import org.jline.utils.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Select - 列表选择器组件
 * 
 * 支持键盘导航（方向键、j/k、数字键）、多选模式
 * 参照 Claude Code/Kimi Code 的 Select 组件设计
 */
public class Select implements Component {
    
    private String title;
    private List<Option> options;
    private int selectedIndex;
    private Set<Integer> selectedIndices;
    private boolean multiSelect;
    private int width;
    private int visibleStart;
    private int visibleCount;
    private boolean showNumbers;
    private Consumer<SelectionResult> onSelect;
    
    // ANSI 颜色
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    
    public Select() {
        this.options = new ArrayList<>();
        this.selectedIndex = 0;
        this.selectedIndices = new HashSet<>();
        this.multiSelect = false;
        this.title = "请选择";
        this.width = 60;
        this.visibleStart = 0;
        this.visibleCount = 10;
        this.showNumbers = true;
    }
    
    public Select title(String title) {
        this.title = title;
        return this;
    }
    
    public Select options(List<String> items) {
        this.options = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            this.options.add(new Option(items.get(i), String.valueOf(i + 1)));
        }
        return this;
    }
    
    public Select multiSelect(boolean multiSelect) {
        this.multiSelect = multiSelect;
        if (multiSelect) {
            this.selectedIndices.add(selectedIndex);
        }
        return this;
    }
    
    public Select onSelect(Consumer<SelectionResult> callback) {
        this.onSelect = callback;
        return this;
    }
    
    public Select width(int width) {
        this.width = Math.max(40, width);
        return this;
    }
    
    public Select visibleCount(int count) {
        this.visibleCount = count;
        return this;
    }
    
    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        
        // 标题
        sb.append(CYAN).append("┌─ ").append(RESET).append(BOLD).append(title).append(RESET);
        sb.append(" ").append(CYAN);
        for (int i = 0; i < width - title.length() - 4; i++) {
            sb.append("─");
        }
        sb.append("┐").append(RESET).append("\n");
        
        // 计算可见范围
        int endIndex = Math.min(visibleStart + visibleCount, options.size());
        
        // 选项列表
        for (int i = visibleStart; i < endIndex; i++) {
            Option opt = options.get(i);
            boolean isSelected = (i == selectedIndex) || (multiSelect && selectedIndices.contains(i));
            
            sb.append("│ ");
            
            if (isSelected) {
                sb.append(GREEN).append("▶").append(RESET).append(" ");
            } else {
                sb.append("  ");
            }
            
            if (showNumbers) {
                sb.append(DIM).append(opt.getShortcut()).append(". ").append(RESET);
            }
            
            if (isSelected) {
                sb.append(BOLD).append(opt.getLabel()).append(RESET);
            } else {
                sb.append(opt.getLabel());
            }
            
            // 填充空格
            int padding = width - opt.getLabel().length() - (showNumbers ? 6 : 3) - (isSelected ? 2 : 0);
            for (int j = 0; j < padding; j++) {
                sb.append(' ');
            }
            
            sb.append(" │\n");
        }
        
        // 底部边框
        sb.append(CYAN).append("└");
        for (int i = 0; i < width; i++) {
            sb.append("─");
        }
        sb.append("┘").append(RESET);
        
        // 提示信息
        sb.append("\n").append(DIM);
        if (multiSelect) {
            sb.append("使用 ↑↓ 导航，空格选择，Enter 确认");
        } else {
            sb.append("使用 ↑↓ 或 j/k 导航，Enter 选择，Esc 取消");
        }
        sb.append(RESET);
        
        return sb.toString();
    }
    
    /**
     * 处理按键事件
     * @param key 按键字符
     * @return true 如果事件被处理
     */
    public boolean handleKey(char key) {
        switch (key) {
            case 'j': // Vim 风格下移
            case '\u2193': // 方向键下
                moveDown();
                return true;
                
            case 'k': // Vim 风格上移
            case '\u2191': // 方向键上
                moveUp();
                return true;
                
            case ' ':
                if (multiSelect) {
                    toggleSelection();
                    return true;
                }
                return false;
                
            case '\n':
            case '\r':
                confirmSelection();
                return true;
                
            case '\u001B': // Escape
                cancelSelection();
                return true;
                
            default:
                // 数字键快速选择
                if (Character.isDigit(key)) {
                    int num = Character.getNumericValue(key);
                    if (num > 0 && num <= options.size()) {
                        selectIndex(num - 1);
                        confirmSelection();
                        return true;
                    }
                }
                return false;
        }
    }
    
    private void moveDown() {
        if (selectedIndex < options.size() - 1) {
            selectedIndex++;
            if (selectedIndex >= visibleStart + visibleCount) {
                visibleStart++;
            }
            if (multiSelect) {
                selectedIndices.add(selectedIndex);
            }
        }
    }
    
    private void moveUp() {
        if (selectedIndex > 0) {
            selectedIndex--;
            if (selectedIndex < visibleStart) {
                visibleStart--;
            }
            if (multiSelect) {
                selectedIndices.add(selectedIndex);
            }
        }
    }
    
    private void toggleSelection() {
        if (selectedIndices.contains(selectedIndex)) {
            selectedIndices.remove(selectedIndex);
        } else {
            selectedIndices.add(selectedIndex);
        }
    }
    
    private void selectIndex(int index) {
        if (index >= 0 && index < options.size()) {
            selectedIndex = index;
            // 确保选中项可见
            if (selectedIndex < visibleStart) {
                visibleStart = selectedIndex;
            } else if (selectedIndex >= visibleStart + visibleCount) {
                visibleStart = selectedIndex - visibleCount + 1;
            }
        }
    }
    
    private void confirmSelection() {
        if (onSelect != null) {
            if (multiSelect) {
                List<Option> selected = new ArrayList<>();
                for (int idx : selectedIndices) {
                    selected.add(options.get(idx));
                }
                onSelect.accept(new SelectionResult(selected, false));
            } else {
                List<Option> selected = Collections.singletonList(options.get(selectedIndex));
                onSelect.accept(new SelectionResult(selected, false));
            }
        }
    }
    
    private void cancelSelection() {
        if (onSelect != null) {
            onSelect.accept(new SelectionResult(Collections.emptyList(), true));
        }
    }
    
    /**
     * 获取选中的选项
     */
    public List<Option> getSelectedOptions() {
        if (multiSelect) {
            List<Option> selected = new ArrayList<>();
            for (int idx : selectedIndices) {
                selected.add(options.get(idx));
            }
            return selected;
        } else {
            return Collections.singletonList(options.get(selectedIndex));
        }
    }
    
    /**
     * 选项类
     */
    public static class Option {
        private final String label;
        private final String shortcut;
        private Object data;
        
        public Option(String label, String shortcut) {
            this.label = label;
            this.shortcut = shortcut;
        }
        
        public Option(String label, String shortcut, Object data) {
            this.label = label;
            this.shortcut = shortcut;
            this.data = data;
        }
        
        public String getLabel() {
            return label;
        }
        
        public String getShortcut() {
            return shortcut;
        }
        
        public Object getData() {
            return data;
        }
    }
    
    /**
     * 选择结果类
     */
    public static class SelectionResult {
        private final List<Option> selectedOptions;
        private final boolean cancelled;
        
        public SelectionResult(List<Option> selectedOptions, boolean cancelled) {
            this.selectedOptions = selectedOptions;
            this.cancelled = cancelled;
        }
        
        public List<Option> getSelectedOptions() {
            return selectedOptions;
        }
        
        public boolean isCancelled() {
            return cancelled;
        }
        
        public boolean hasSelection() {
            return !cancelled && !selectedOptions.isEmpty();
        }
    }
}
