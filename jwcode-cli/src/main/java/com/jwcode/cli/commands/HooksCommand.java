package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HooksCommand - /hooks 命令
 * 
 * 功能说明：
 * Hook 管理，添加、删除、列出事件钩子。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/hooks", description = "Hook 管理")
public class HooksCommand implements Runnable {
    
    @Parameters(index = "0", description = "操作类型 (list, add, remove)", arity = "0..1")
    private String action;
    
    @Parameters(index = "1", description = "Hook 名称", arity = "0..1")
    private String hookName;
    
    @Parameters(index = "2", description = "Hook 脚本/命令", arity = "0..1")
    private String hookScript;
    
    @Option(names = {"-e", "--event"}, description = "触发事件类型")
    private String eventType;
    
    @Option(names = {"-l", "--list"}, description = "列出所有 hooks")
    private boolean listOnly;
    
    private static final Map<String, List<HookEntry>> hooks = new ConcurrentHashMap<>();
    
    static {
        // 初始化默认 hooks
        List<HookEntry> preRequestHooks = new ArrayList<>();
        preRequestHooks.add(new HookEntry("auth-check", "pre-request", "验证用户认证"));
        hooks.put("pre-request", preRequestHooks);
    }
    
    @Override
    public void run() {
        if (listOnly || action == null) {
            listHooks();
            return;
        }
        
        switch (action.toLowerCase()) {
            case "list":
                listHooks();
                break;
            case "add":
                addHook();
                break;
            case "remove":
                removeHook();
                break;
            default:
                showHelp();
        }
    }
    
    private void listHooks() {
        System.out.println("=== 已注册的 Hooks ===");
        System.out.println();
        
        if (hooks.isEmpty()) {
            System.out.println("(无已注册的 hooks)");
            return;
        }
        
        for (Map.Entry<String, List<HookEntry>> entry : hooks.entrySet()) {
            System.out.println("事件类型：" + entry.getKey());
            for (HookEntry hook : entry.getValue()) {
                System.out.println("  - " + hook.name + ": " + hook.description);
            }
            System.out.println();
        }
    }
    
    private void addHook() {
        if (hookName == null || hookScript == null) {
            System.out.println("错误：需要指定 hook 名称和脚本");
            System.out.println("用法：/hooks add <name> <script> [-e <event>]");
            return;
        }
        
        String event = eventType != null ? eventType : "default";
        HookEntry hook = new HookEntry(hookName, event, hookScript);
        
        hooks.computeIfAbsent(event, k -> new ArrayList<>()).add(hook);
        System.out.println("已添加 hook: " + hookName + " (事件：" + event + ")");
    }
    
    private void removeHook() {
        if (hookName == null) {
            System.out.println("错误：需要指定 hook 名称");
            System.out.println("用法：/hooks remove <name>");
            return;
        }
        
        boolean removed = false;
        for (List<HookEntry> hookList : hooks.values()) {
            hookList.removeIf(h -> h.name.equals(hookName));
            removed = true;
        }
        
        if (removed) {
            System.out.println("已删除 hook: " + hookName);
        } else {
            System.out.println("未找到 hook: " + hookName);
        }
    }
    
    private void showHelp() {
        System.out.println("Hook 管理命令");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /hooks list              - 列出所有 hooks");
        System.out.println("  /hooks add <name> <script> [-e <event>] - 添加 hook");
        System.out.println("  /hooks remove <name>     - 删除 hook");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  -e, --event <type>  触发事件类型 (pre-request, post-response, etc.)");
    }
    
    /**
     * Hook 条目
     */
    public static class HookEntry {
        public final String name;
        public final String eventType;
        public final String description;
        
        public HookEntry(String name, String eventType, String description) {
            this.name = name;
            this.eventType = eventType;
            this.description = description;
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "name", name,
                    "eventType", eventType,
                    "description", description
            );
        }
    }
}