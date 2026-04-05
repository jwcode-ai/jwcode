package com.jwcode.core.ui;

import java.io.IOException;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * DialogSystem - 对话框系统
 * 
 * 功能说明：
 * 提供确认对话框、输入对话框等交互功能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class DialogSystem {
    
    private static final Scanner scanner = new Scanner(System.in);
    
    // ANSI 颜色代码
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    
    /**
     * 确认对话框
     * @param message 确认消息
     * @return 用户选择结果
     */
    public static boolean confirm(String message) {
        return confirm(message, "确认", "取消");
    }
    
    /**
     * 确认对话框（自定义选项）
     * @param message 确认消息
     * @param confirmText 确认按钮文本
     * @param cancelText 取消按钮文本
     * @return 用户选择结果
     */
    public static boolean confirm(String message, String confirmText, String cancelText) {
        System.out.println();
        System.out.println("┌" + repeatChar("─", 50) + "┐");
        System.out.println("│ " + BOLD + "确认" + RESET + " " + repeatChar(" ", 46 - 6) + "│");
        System.out.println("├" + repeatChar("─", 50) + "┤");
        
        // 格式化消息，自动换行
        List<String> lines = wrapText(message, 48);
        for (String line : lines) {
            System.out.println("│ " + padRight(line, 48) + "│");
        }
        
        System.out.println("├" + repeatChar("─", 50) + "┤");
        System.out.println("│ " + GREEN + "[" + confirmText + "]" + RESET + " 确认  |  " + 
                          RED + "[取消]" + RESET + " 取消" + repeatChar(" ", 15) + "│");
        System.out.println("└" + repeatChar("─", 50) + "┘");
        System.out.print("> ");
        
        String input = scanner.nextLine().trim().toLowerCase();
        return input.equals("y") || input.equals("yes") || input.equals(confirmText.toLowerCase()) || input.isEmpty();
    }
    
    /**
     * 输入对话框
     * @param prompt 提示消息
     * @return 用户输入
     */
    public static String input(String prompt) {
        return input(prompt, null);
    }
    
    /**
     * 输入对话框（带默认值）
     * @param prompt 提示消息
     * @param defaultValue 默认值
     * @return 用户输入
     */
    public static String input(String prompt, String defaultValue) {
        System.out.println();
        System.out.println("┌" + repeatChar("─", 50) + "┐");
        System.out.println("│ " + BOLD + "输入" + RESET + " " + repeatChar(" ", 46 - 6) + "│");
        System.out.println("├" + repeatChar("─", 50) + "┤");
        
        List<String> lines = wrapText(prompt, 48);
        for (String line : lines) {
            System.out.println("│ " + padRight(line, 48) + "│");
        }
        
        if (defaultValue != null) {
            String defaultText = "默认值：" + defaultValue;
            System.out.println("│ " + CYAN + padRight(defaultText, 48) + "│");
        }
        
        System.out.println("├" + repeatChar("─", 50) + "┤");
        System.out.print("│ > ");
        
        String inputValue = scanner.nextLine().trim();
        
        if (inputValue.isEmpty() && defaultValue != null) {
            inputValue = defaultValue;
        }
        
        System.out.println("└" + repeatChar("─", 50) + "┘");
        System.out.println();
        
        return inputValue;
    }
    
    /**
     * 密码输入对话框
     * @param prompt 提示消息
     * @return 用户输入的密码
     */
    public static String password(String prompt) {
        System.out.println();
        System.out.println("┌" + repeatChar("─", 50) + "┐");
        System.out.println("│ " + BOLD + "密码输入" + RESET + " " + repeatChar(" ", 42 - 10) + "│");
        System.out.println("├" + repeatChar("─", 50) + "┤");
        
        List<String> lines = wrapText(prompt, 48);
        for (String line : lines) {
            System.out.println("│ " + padRight(line, 48) + "│");
        }
        
        System.out.println("├" + repeatChar("─", 50) + "┤");
        System.out.print("│ > ");
        
        // 读取密码（不显示）
        StringBuilder password = new StringBuilder();
        try {
            while (true) {
                int c = System.in.read();
                if (c == '\r' || c == '\n') {
                    break;
                }
                if (c == 127) { // Backspace
                    if (password.length() > 0) {
                        password.deleteCharAt(password.length() - 1);
                        System.out.print("\b \b");
                    }
                } else {
                    password.append((char) c);
                    System.out.print("*");
                }
            }
        } catch (IOException e) {
            // 忽略读取错误，返回已输入的内容
        }
        
        System.out.println();
        System.out.println("└" + repeatChar("─", 50) + "┘");
        System.out.println();
        
        return password.toString();
    }
    
    /**
     * 选择对话框（单选）
     * @param prompt 提示消息
     * @param options 选项列表
     * @return 选中的索引
     */
    public static int select(String prompt, List<String> options) {
        System.out.println();
        System.out.println("┌" + repeatChar("─", 50) + "┐");
        System.out.println("│ " + BOLD + "选择" + RESET + " " + repeatChar(" ", 46 - 6) + "│");
        System.out.println("├" + repeatChar("─", 50) + "┤");
        
        List<String> lines = wrapText(prompt, 48);
        for (String line : lines) {
            System.out.println("│ " + padRight(line, 48) + "│");
        }
        
        System.out.println("├" + repeatChar("─", 50) + "┤");
        
        for (int i = 0; i < options.size(); i++) {
            String option = (i + 1) + ". " + options.get(i);
            System.out.println("│ " + YELLOW + option + RESET + repeatChar(" ", 48 - option.length()) + "│");
        }
        
        System.out.println("└" + repeatChar("─", 50) + "┘");
        System.out.print("> ");
        
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim());
            if (choice >= 1 && choice <= options.size()) {
                return choice - 1;
            }
        } catch (NumberFormatException e) {
            // 无效输入
        }
        
        System.out.println("无效选择");
        return -1;
    }
    
    /**
     * 消息对话框
     * @param title 标题
     * @param message 消息内容
     * @param type 类型：info, warning, error, success
     */
    public static void message(String title, String message, String type) {
        String color;
        String icon;
        
        switch (type.toLowerCase()) {
            case "warning":
                color = YELLOW;
                icon = "⚠";
                break;
            case "error":
                color = RED;
                icon = "✗";
                break;
            case "success":
                color = GREEN;
                icon = "✓";
                break;
            default:
                color = BLUE;
                icon = "ℹ";
        }
        
        System.out.println();
        System.out.println("┌" + repeatChar("─", 50) + "┐");
        System.out.println("│ " + color + BOLD + icon + " " + title + RESET + repeatChar(" ", 46 - title.length() - 2) + "│");
        System.out.println("├" + repeatChar("─", 50) + "┤");
        
        List<String> lines = wrapText(message, 48);
        for (String line : lines) {
            System.out.println("│ " + padRight(line, 48) + "│");
        }
        
        System.out.println("└" + repeatChar("─", 50) + "┘");
        System.out.println();
    }
    
    /**
     * 异步确认对话框
     */
    public static CompletableFuture<Boolean> confirmAsync(String message) {
        return CompletableFuture.supplyAsync(() -> confirm(message));
    }
    
    /**
     * 异步输入对话框
     */
    public static CompletableFuture<String> inputAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> input(prompt));
    }
    
    // 工具方法
    
    private static String repeatChar(String ch, int count) {
        return ch.repeat(Math.max(0, count));
    }
    
    private static String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + repeatChar(" ", n - s.length());
    }
    
    private static List<String> wrapText(String text, int width) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > width) {
                lines.add(currentLine.toString().trim());
                currentLine = new StringBuilder();
            }
            currentLine.append(word).append(" ");
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString().trim());
        }
        
        return lines;
    }
}