package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MemoryCommand - /memory 命令
 * 
 * 功能说明：
 * 内存管理，查看和管理 AI 的长期记忆。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/memory", description = "内存管理")
public class MemoryCommand implements Runnable {
    
    @Parameters(index = "0", description = "操作类型 (list, add, remove, clear)", arity = "0..1")
    private String action;
    
    @Parameters(index = "1", description = "记忆内容或 ID", arity = "0..1")
    private String content;
    
    @Option(names = {"-l", "--list"}, description = "列出所有记忆")
    private boolean listOnly;
    
    @Option(names = {"-c", "--clear"}, description = "清除所有记忆")
    private boolean clearAll;
    
    @Option(names = {"-s", "--status"}, description = "显示记忆状态")
    private boolean showStatus;
    
    private static final List<MemoryEntry> memories = new ArrayList<>();
    private static int memoryIdCounter = 0;
    
    static {
        // 添加示例记忆
        addMemory("用户偏好使用 Java 进行开发");
        addMemory("项目使用 Maven 构建");
    }
    
    @Override
    public void run() {
        if (showStatus || (action == null && !listOnly && !clearAll)) {
            showMemoryStatus();
            return;
        }
        
        if (listOnly) {
            listMemories();
            return;
        }
        
        if (clearAll) {
            clearMemories();
            return;
        }
        
        if (action == null) {
            return;
        }
        
        switch (action.toLowerCase()) {
            case "list":
                listMemories();
                break;
            case "add":
                addMemoryCmd();
                break;
            case "remove":
                removeMemory();
                break;
            case "clear":
                clearMemories();
                break;
            default:
                showHelp();
        }
    }
    
    private void showMemoryStatus() {
        System.out.println("=== 记忆状态 ===");
        System.out.println();
        System.out.println("记忆数量：" + memories.size());
        System.out.println();
        if (!memories.isEmpty()) {
            System.out.println("最近记忆:");
            int start = Math.max(0, memories.size() - 3);
            for (int i = start; i < memories.size(); i++) {
                MemoryEntry mem = memories.get(i);
                System.out.println("  [" + mem.id + "] " + mem.content);
            }
        }
    }
    
    private void listMemories() {
        System.out.println("=== 所有记忆 ===");
        System.out.println();
        
        if (memories.isEmpty()) {
            System.out.println("(无记忆)");
            return;
        }
        
        for (MemoryEntry mem : memories) {
            System.out.println("[" + mem.id + "] " + mem.content);
            System.out.println("    创建时间：" + mem.createdAt);
            System.out.println();
        }
    }
    
    private void addMemoryCmd() {
        if (content == null) {
            System.out.println("错误：需要提供记忆内容");
            System.out.println("用法：/memory add <content>");
            return;
        }
        
        int id = addMemory(content);
        System.out.println("已添加记忆 [" + id + "]: " + content);
    }
    
    private void removeMemory() {
        if (content == null) {
            System.out.println("错误：需要提供记忆 ID");
            System.out.println("用法：/memory remove <id>");
            return;
        }
        
        try {
            int id = Integer.parseInt(content);
            boolean removed = memories.removeIf(m -> m.id == id);
            if (removed) {
                System.out.println("已删除记忆 [" + id + "]");
            } else {
                System.out.println("未找到记忆 [" + id + "]");
            }
        } catch (NumberFormatException e) {
            System.out.println("错误：无效的 ID");
        }
    }
    
    private void clearMemories() {
        memories.clear();
        System.out.println("已清除所有记忆");
    }
    
    private static int addMemory(String content) {
        MemoryEntry entry = new MemoryEntry(++memoryIdCounter, content);
        memories.add(entry);
        return entry.id;
    }
    
    private void showHelp() {
        System.out.println("内存管理命令");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /memory list              - 列出所有记忆");
        System.out.println("  /memory add <content>     - 添加记忆");
        System.out.println("  /memory remove <id>       - 删除记忆");
        System.out.println("  /memory clear             - 清除所有记忆");
        System.out.println("  /memory status            - 显示记忆状态");
    }
    
    /**
     * 记忆条目
     */
    public static class MemoryEntry {
        public final int id;
        public final String content;
        public final String createdAt;
        
        public MemoryEntry(int id, String content) {
            this.id = id;
            this.content = content;
            this.createdAt = java.time.Instant.now().toString();
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "content", content,
                    "createdAt", createdAt
            );
        }
    }
}