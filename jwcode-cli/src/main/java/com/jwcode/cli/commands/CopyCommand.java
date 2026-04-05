package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * /copy 命令 - 复制内容到剪贴板
 * 
 * 将指定的内容或上一个命令的输出复制到系统剪贴板。
 */
public class CopyCommand implements Command {
    
    @Override
    public String getName() {
        return "copy";
    }
    
    @Override
    public String getDescription() {
        return "复制内容到系统剪贴板";
    }
    
    @Override
    public String getUsage() {
        return "/copy [内容] 或 /copy --last 复制上一条消息";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        String contentToCopy = null;
        boolean copyLast = false;
        
        // 解析参数
        if (args == null || args.trim().isEmpty()) {
            // 没有参数，尝试复制最后一条消息
            copyLast = true;
        } else if ("--last".equals(args.trim()) || "-l".equals(args.trim())) {
            copyLast = true;
        } else {
            contentToCopy = args;
        }
        
        if (copyLast) {
            // 获取最后一条消息
            String lastMessage = context.getSession().getLastMessage();
            if (lastMessage == null || lastMessage.isEmpty()) {
                return CommandResult.error("没有可复制的消息");
            }
            contentToCopy = lastMessage;
        }
        
        if (contentToCopy == null) {
            return CommandResult.error("没有指定要复制的内容");
        }
        
        try {
            // 尝试使用 Java AWT 复制到剪贴板
            copyToClipboard(contentToCopy);
            
            return CommandResult.success("已复制到剪贴板 (" + contentToCopy.length() + " 字符)");
            
        } catch (Exception e) {
            // 如果 AWT 不可用，尝试使用系统命令
            try {
                copyToClipboardSystemCommand(contentToCopy);
                return CommandResult.success("已复制到剪贴板 (" + contentToCopy.length() + " 字符)");
            } catch (Exception ex) {
                return CommandResult.error("复制到剪贴板失败：" + ex.getMessage());
            }
        }
    }
    
    /**
     * 使用 AWT 复制到剪贴板
     */
    private void copyToClipboard(String content) throws Exception {
        if (!Desktop.isDesktopSupported()) {
            throw new Exception("当前系统不支持 Clipboard");
        }
        
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(content);
        clipboard.setContents(selection, selection);
    }
    
    /**
     * 使用系统命令复制到剪贴板
     */
    private void copyToClipboardSystemCommand(String content) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        
        if (os.contains("win")) {
            // Windows: 使用 clip
            pb = new ProcessBuilder("cmd.exe", "/c", "clip");
        } else if (os.contains("mac")) {
            // macOS: 使用 pbcopy
            pb = new ProcessBuilder("pbcopy");
        } else if (os.contains("linux")) {
            // Linux: 尝试使用 xclip 或 xsel
            if (isCommandAvailable("xclip")) {
                pb = new ProcessBuilder("xclip", "-selection", "clipboard");
            } else if (isCommandAvailable("xsel")) {
                pb = new ProcessBuilder("xsel", "--clipboard", "--input");
            } else {
                throw new Exception("Linux 系统需要安装 xclip 或 xsel");
            }
        } else {
            throw new Exception("不支持的操作系统：" + os);
        }
        
        Process process = pb.start();
        
        // 写入内容到进程
        try (var outputStream = process.getOutputStream()) {
            outputStream.write(content.getBytes());
            outputStream.flush();
        }
        
        // 等待进程完成
        process.waitFor();
    }
    
    /**
     * 检查命令是否可用
     */
    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("which", command).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line != null && !line.isEmpty();
            }
        } catch (Exception e) {
            return false;
        }
    }
}