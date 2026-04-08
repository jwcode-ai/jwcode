package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

/**
 * Dialog - 对话框组件
 * 
 * 功能说明：
 * 显示模态对话框，用于确认操作或显示消息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Dialog implements Component {
    
    private String title;
    private String message;
    private String[] buttons;
    private int selectedButton;
    private int width;
    private int height;
    private TextColor titleColor;
    private TextColor borderColor;
    private boolean centered;
    
    public Dialog() {
        this("提示", "");
    }
    
    public Dialog(String title, String message) {
        this.title = title;
        this.message = message;
        this.buttons = new String[]{"确定"};
        this.selectedButton = 0;
        this.width = 40;
        this.height = 10;
        this.titleColor = TextColor.ANSI.CYAN;
        this.borderColor = TextColor.ANSI.DEFAULT;
        this.centered = true;
    }
    
    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        
        // 上边框
        sb.append(renderLine('┌', '─', '┐', width)).append("\n");
        
        // 标题行
        sb.append(renderTitleLine()).append("\n");
        
        // 消息区域
        sb.append(renderMessageArea()).append("\n");
        
        // 按钮行
        sb.append(renderButtonLine()).append("\n");
        
        // 下边框
        sb.append(renderLine('└', '─', '┘', width));
        
        return sb.toString();
    }
    
    /**
     * 渲染边框线
     */
    private String renderLine(char left, char middle, char right, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < width - 2; i++) {
            sb.append(middle);
        }
        sb.append(right);
        return sb.toString();
    }
    
    /**
     * 渲染标题行
     */
    private String renderTitleLine() {
        StringBuilder sb = new StringBuilder();
        sb.append('│');
        
        // 标题
        String titleText = " " + title + " ";
        int padding = width - 2 - titleText.length();
        
        sb.append(titleText);
        for (int i = 0; i < padding; i++) {
            sb.append(' ');
        }
        sb.append('│');
        
        return sb.toString();
    }
    
    /**
     * 渲染消息区域
     */
    private String renderMessageArea() {
        StringBuilder sb = new StringBuilder();
        
        // 分割消息为多行
        String[] lines = message.split("\n");
        int linesRendered = 0;
        
        for (String line : lines) {
            sb.append('│');
            String paddedLine = padOrTruncate(line, width - 2);
            sb.append(paddedLine);
            sb.append('│');
            sb.append("\n");
            linesRendered++;
        }
        
        // 填充空行
        while (linesRendered < height - 4) {
            sb.append('│');
            for (int i = 0; i < width - 2; i++) {
                sb.append(' ');
            }
            sb.append('│');
            sb.append("\n");
            linesRendered++;
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 渲染按钮行
     */
    private String renderButtonLine() {
        StringBuilder sb = new StringBuilder();
        sb.append('│');
        
        // 计算按钮总宽度
        int totalButtonWidth = 0;
        for (String button : buttons) {
            totalButtonWidth += button.length() + 4; // [ 按钮 ]
        }
        
        // 计算起始位置（居中）
        int startPos = (width - 2 - totalButtonWidth) / 2;
        
        // 填充左侧空格
        for (int i = 0; i < startPos; i++) {
            sb.append(' ');
        }
        
        // 渲染按钮
        for (int i = 0; i < buttons.length; i++) {
            if (i == selectedButton) {
                sb.append("[");
                sb.append(buttons[i]);
                sb.append("]");
            } else {
                sb.append(" ");
                sb.append(buttons[i]);
                sb.append(" ");
            }
            
            if (i < buttons.length - 1) {
                sb.append("  ");
            }
        }
        
        // 填充右侧空格
        int remainingSpace = width - 2 - startPos - totalButtonWidth;
        for (int i = 0; i < remainingSpace; i++) {
            sb.append(' ');
        }
        
        sb.append('│');
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
     * 设置标题
     */
    public void setTitle(String title) {
        this.title = title;
    }
    
    /**
     * 设置消息
     */
    public void setMessage(String message) {
        this.message = message;
    }
    
    /**
     * 设置按钮
     */
    public void setButtons(String[] buttons) {
        this.buttons = buttons;
        this.selectedButton = 0;
    }
    
    /**
     * 选择下一个按钮
     */
    public void selectNextButton() {
        selectedButton = (selectedButton + 1) % buttons.length;
    }
    
    /**
     * 选择上一个按钮
     */
    public void selectPreviousButton() {
        selectedButton = (selectedButton - 1 + buttons.length) % buttons.length;
    }
    
    /**
     * 获取选中的按钮
     */
    public String getSelectedButton() {
        return buttons[selectedButton];
    }
    
    /**
     * 设置宽度
     */
    public void setWidth(int width) {
        this.width = Math.max(20, width);
    }
    
    /**
     * 设置高度
     */
    public void setHeight(int height) {
        this.height = Math.max(5, height);
    }
    
    /**
     * 设置是否居中
     */
    public void setCentered(boolean centered) {
        this.centered = centered;
    }
    
    /**
     * 设置标题颜色
     */
    public void setTitleColor(TextColor color) {
        this.titleColor = color;
    }
    
    /**
     * 设置边框颜色
     */
    public void setBorderColor(TextColor color) {
        this.borderColor = color;
    }
    
    /**
     * 创建确认对话框
     */
    public static Dialog createConfirm(String message) {
        Dialog dialog = new Dialog("确认", message);
        dialog.setButtons(new String[]{"是", "否"});
        return dialog;
    }
    
    /**
     * 创建消息对话框
     */
    public static Dialog createMessage(String title, String message) {
        return new Dialog(title, message);
    }
    
    /**
     * 创建错误对话框
     */
    public static Dialog createError(String message) {
        Dialog dialog = new Dialog("错误", message);
        dialog.setButtons(new String[]{"确定"});
        dialog.setTitleColor(TextColor.ANSI.RED);
        return dialog;
    }
}