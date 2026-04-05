package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * VimCommand - /vim 命令
 * 
 * 功能说明：
 * Vim 模式，启用 Vim 风格的键盘绑定。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/vim", description = "Vim 模式")
public class VimCommand implements Runnable {
    
    @Option(names = {"-e", "--enable"}, description = "启用 Vim 模式")
    private boolean enable;
    
    @Option(names = {"-d", "--disable"}, description = "禁用 Vim 模式")
    private boolean disable;
    
    @Option(names = {"-s", "--status"}, description = "查看当前状态")
    private boolean status;
    
    private static boolean vimModeEnabled = false;
    
    @Override
    public void run() {
        if (status || (!enable && !disable)) {
            showStatus();
            return;
        }
        
        if (enable) {
            vimModeEnabled = true;
            System.out.println("Vim 模式已启用");
            System.out.println();
            showVimHelp();
        } else if (disable) {
            vimModeEnabled = false;
            System.out.println("Vim 模式已禁用");
        }
    }
    
    private void showStatus() {
        System.out.println("=== Vim 模式状态 ===");
        System.out.println();
        System.out.println("Vim 模式：" + (vimModeEnabled ? "已启用" : "已禁用"));
        System.out.println();
        if (vimModeEnabled) {
            showVimHelp();
        }
    }
    
    private void showVimHelp() {
        System.out.println("Vim 模式键盘绑定:");
        System.out.println();
        System.out.println("  模式切换:");
        System.out.println("    ESC     - 切换到普通模式");
        System.out.println("    i       - 切换到插入模式");
        System.out.println("    v       - 切换到可视模式");
        System.out.println();
        System.out.println("  普通模式:");
        System.out.println("    h/j/k/l - 左/下/上/右移动");
        System.out.println("    w/b     - 前进/后退一个单词");
        System.out.println("    0/$     - 行首/行尾");
        System.out.println("    gg/G    - 文件首/文件尾");
        System.out.println("    dd      - 删除行");
        System.out.println("    yy      - 复制行");
        System.out.println("    p       - 粘贴");
        System.out.println("    u       - 撤销");
        System.out.println("    Ctrl+r  - 重做");
        System.out.println("    /       - 搜索");
        System.out.println("    n/N     - 下一个/上一个匹配");
        System.out.println();
        System.out.println("  插入模式:");
        System.out.println("    正常输入文本");
        System.out.println("    ESC 返回普通模式");
    }
    
    public static boolean isVimModeEnabled() {
        return vimModeEnabled;
    }
}