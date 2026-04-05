package com.jwcode.core.ui;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * KeyboardBindings - 键盘绑定系统
 * 
 * 功能说明：
 * 自定义快捷键绑定，支持组合键和功能键。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class KeyboardBindings {
    
    // 特殊键的转义序列
    public static final String ESC = "\u001B";
    public static final String ENTER = "\n";
    public static final String TAB = "\t";
    public static final String BACKSPACE = "\b";
    public static final String DELETE = "\u007F";
    
    // 方向键
    public static final String UP = "\u001B[A";
    public static final String DOWN = "\u001B[B";
    public static final String RIGHT = "\u001B[C";
    public static final String LEFT = "\u001B[D";
    
    // 功能键
    public static final String F1 = "\u001BOP";
    public static final String F2 = "\u001BOQ";
    public static final String F3 = "\u001BOR";
    public static final String F4 = "\u001BOS";
    public static final String F5 = "\u001B[15~";
    public static final String F6 = "\u001B[17~";
    public static final String F7 = "\u001B[18~";
    public static final String F8 = "\u001B[19~";
    public static final String F9 = "\u001B[20~";
    public static final String F10 = "\u001B[21~";
    public static final String F11 = "\u001B[23~";
    public static final String F12 = "\u001B[24~";
    
    // Home/End
    public static final String HOME = "\u001B[H";
    public static final String END = "\u001B[F";
    public static final String PAGE_UP = "\u001B[5~";
    public static final String PAGE_DOWN = "\u001B[6~";
    
    // 组合键前缀
    public static final String CTRL_PREFIX = "CTRL+";
    public static final String ALT_PREFIX = "ALT+";
    public static final String SHIFT_PREFIX = "SHIFT+";
    
    // 绑定注册表
    private final Map<String, Runnable> bindings;
    private final Map<String, String> keyDescriptions;
    private Consumer<String> statusCallback;
    
    // 当前状态
    private volatile boolean listening;
    private Thread listenerThread;
    
    public KeyboardBindings() {
        this.bindings = new ConcurrentHashMap<>();
        this.keyDescriptions = new HashMap<>();
        this.listening = false;
        registerDefaultBindings();
    }
    
    /**
     * 注册默认绑定
     */
    private void registerDefaultBindings() {
        // Ctrl+C - 中断
        bind(CTRL_PREFIX + "C", () -> {
            System.out.println("\n中断操作");
            System.exit(0);
        });
        setDescription(CTRL_PREFIX + "C", "中断当前操作");
        
        // Ctrl+D - 退出
        bind(CTRL_PREFIX + "D", () -> {
            System.out.println("\n退出程序");
            System.exit(0);
        });
        setDescription(CTRL_PREFIX + "D", "退出程序");
        
        // Ctrl+L - 清屏
        bind(CTRL_PREFIX + "L", () -> {
            ThemeSystem.clearScreen();
        });
        setDescription(CTRL_PREFIX + "L", "清除屏幕");
        
        // Ctrl+Z - 撤销
        bind(CTRL_PREFIX + "Z", () -> {
            System.out.println("\n撤销操作");
        });
        setDescription(CTRL_PREFIX + "Z", "撤销");
        
        // Ctrl+Y - 重做
        bind(CTRL_PREFIX + "Y", () -> {
            System.out.println("\n重做操作");
        });
        setDescription(CTRL_PREFIX + "Y", "重做");
        
        // Ctrl+K - 剪切到行尾
        bind(CTRL_PREFIX + "K", () -> {
            System.out.println("\n剪切到行尾");
        });
        setDescription(CTRL_PREFIX + "K", "剪切到行尾");
        
        // Ctrl+U - 删除到行首
        bind(CTRL_PREFIX + "U", () -> {
            System.out.println("\n删除到行首");
        });
        setDescription(CTRL_PREFIX + "U", "删除到行首");
        
        // 方向键
        bind(UP, () -> System.out.println("\r\u001B[A"));  // 上
        bind(DOWN, () -> System.out.println("\r\u001B[B"));  // 下
        bind(RIGHT, () -> System.out.println("\r\u001B[C"));  // 右
        bind(LEFT, () -> System.out.println("\r\u001B[D"));  // 左
        
        // Tab - 自动补全
        bind(TAB, () -> {
            System.out.println("\r[TAB 补全]");
        });
        setDescription(TAB, "自动补全");
        
        // Enter - 确认
        bind(ENTER, () -> {
            System.out.println();
        });
        setDescription(ENTER, "确认输入");
    }
    
    /**
     * 绑定按键到动作
     */
    public void bind(String key, Runnable action) {
        bindings.put(key.toUpperCase(), action);
    }
    
    /**
     * 设置按键描述
     */
    public void setDescription(String key, String description) {
        keyDescriptions.put(key.toUpperCase(), description);
    }
    
    /**
     * 移除绑定
     */
    public void unbind(String key) {
        bindings.remove(key.toUpperCase());
        keyDescriptions.remove(key.toUpperCase());
    }
    
    /**
     * 获取所有绑定
     */
    public Map<String, String> getAllBindings() {
        Map<String, String> result = new HashMap<>();
        for (String key : bindings.keySet()) {
            String desc = keyDescriptions.get(key);
            result.put(key, desc != null ? desc : "无描述");
        }
        return result;
    }
    
    /**
     * 显示所有绑定
     */
    public void showBindings() {
        System.out.println("=== 键盘绑定 ===");
        System.out.println();
        
        // 控制键
        System.out.println("控制键:");
        showKey(CTRL_PREFIX + "C");
        showKey(CTRL_PREFIX + "D");
        showKey(CTRL_PREFIX + "L");
        showKey(CTRL_PREFIX + "Z");
        showKey(CTRL_PREFIX + "Y");
        showKey(CTRL_PREFIX + "K");
        showKey(CTRL_PREFIX + "U");
        
        // 导航键
        System.out.println("\n导航键:");
        showKey(UP, "上");
        showKey(DOWN, "下");
        showKey(LEFT, "左");
        showKey(RIGHT, "右");
        showKey(HOME, "行首");
        showKey(END, "行尾");
        showKey(PAGE_UP, "上一页");
        showKey(PAGE_DOWN, "下一页");
        
        // 功能键
        System.out.println("\n其他:");
        showKey(TAB, "自动补全");
        showKey(ENTER, "确认");
        showKey(ESC, "取消");
    }
    
    private void showKey(String key) {
        String desc = keyDescriptions.get(key);
        if (desc != null) {
            System.out.println("  " + padRight(key, 10) + " - " + desc);
        }
    }
    
    private void showKey(String key, String defaultDesc) {
        String desc = keyDescriptions.get(key);
        System.out.println("  " + padRight(key, 10) + " - " + (desc != null ? desc : defaultDesc));
    }
    
    /**
     * 开始监听键盘输入
     */
    public void startListening() {
        if (listening) return;
        
        listening = true;
        listenerThread = new Thread(this::listenLoop);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    
    /**
     * 停止监听
     */
    public void stopListening() {
        listening = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }
    
    /**
     * 监听循环
     */
    private void listenLoop() {
        try {
            while (listening) {
                int c = System.in.read();
                if (c == -1) break;
                
                StringBuilder sequence = new StringBuilder();
                sequence.append((char) c);
                
                // 处理转义序列
                if (c == 27) { // ESC
                    if (System.in.available() > 0) {
                        int c2 = System.in.read();
                        sequence.append((char) c2);
                        
                        if (c2 == 91 || c2 == 79) { // [ or O
                            while (System.in.available() > 0) {
                                int c3 = System.in.read();
                                sequence.append((char) c3);
                                if (c3 >= 65 && c3 <= 126) break;
                            }
                        }
                    }
                }
                
                // 处理 Ctrl 键
                if (c >= 1 && c <= 26) {
                    String key = CTRL_PREFIX + (char)('A' + c - 1);
                    Runnable action = bindings.get(key);
                    if (action != null) {
                        action.run();
                        continue;
                    }
                }
                
                // 查找并执行绑定的动作
                String keySeq = sequence.toString();
                Runnable action = bindings.get(keySeq);
                if (action != null) {
                    action.run();
                }
            }
        } catch (IOException e) {
            // 监听结束
        }
    }
    
    /**
     * 设置状态回调
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }
    
    private String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
}