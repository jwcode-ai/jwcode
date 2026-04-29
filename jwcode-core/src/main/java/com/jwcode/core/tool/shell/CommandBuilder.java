package com.jwcode.core.tool.shell;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * 跨平台 Shell 命令构建器
 * 
 * 实现「意图-翻译-沙箱」三层模型：
 * - Layer 3 (LLM)：只生成结构化意图 JSON
 * - Layer 2 (Builder)：识别 OS，翻译为平台命令
 * - Layer 1 (Sandbox)：执行沙箱控制
 * 
 * LLM 可见的「意图清单」：
 * | 意图 | 参数 | Windows 翻译 | Linux 翻译 |
 * | read_file | path | Get-Content | cat |
 * | list_files | dir, recursive | Get-ChildItem | find |
 * | search_text | dir, pattern | Select-String | grep -r |
 * | count_lines | path | (Get-Content).Count | wc -l |
 */
public class CommandBuilder {
    
    private static final Logger logger = Logger.getLogger(CommandBuilder.class.getName());
    
    /** 操作系统类型 */
    private static final String OS = System.getProperty("os.name", "unknown").toLowerCase();
    
    /** 是否 Windows */
    private static final boolean IS_WINDOWS = OS.contains("windows");
    
    /** 禁止的危险操作 */
    private static final List<String> FORBIDDEN_COMMANDS = List.of(
        "rm -rf", "format", "del /f /s /q", "$RECYCLE.BIN", 
        "mkfs", "dd if=", "> /dev/sd"
    );
    
    /**
     * 意图类型
     */
    public enum Intent {
        READ_FILE("read_file"),
        LIST_FILES("list_files"),
        SEARCH_TEXT("search_text"),
        COUNT_LINES("count_lines"),
        WRITE_FILE("write_file"),
        COPY_FILE("copy_file"),
        MOVE_FILE("move_file"),
        DELETE_FILE("delete_file"),
        CREATE_DIR("create_dir"),
        EXECUTE_COMMAND("execute_command");
        
        private final String name;
        
        Intent(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public static Intent fromString(String s) {
            for (Intent i : values()) {
                if (i.name.equalsIgnoreCase(s)) {
                    return i;
                }
            }
            return null;
        }
    }
    
    /**
     * 意图请求
     */
    public static class IntentRequest {
        public final Intent intent;
        public final Map<String, Object> params;
        
        public IntentRequest(Intent intent, Map<String, Object> params) {
            this.intent = intent;
            this.params = params != null ? params : new HashMap<>();
        }
        
        public static IntentRequest fromJson(String json) {
            // 简单解析 JSON 意图（实际使用时可用 JSON 解析器）
            try {
                // 尝试解析 {"action": "read_file", "path": "..."}
                if (json.contains("read_file")) {
                    return parseSimple(json, Intent.READ_FILE, "path");
                } else if (json.contains("list_files")) {
                    return parseSimple(json, Intent.LIST_FILES, "dir");
                } else if (json.contains("search_text")) {
                    return parseSimple(json, Intent.SEARCH_TEXT, "dir");
                } else if (json.contains("count_lines")) {
                    return parseSimple(json, Intent.COUNT_LINES, "path");
                }
            } catch (Exception e) {
                logger.warning("解析意图失败: " + e.getMessage());
            }
            return null;
        }
        
        private static IntentRequest parseSimple(String json, Intent intent, String paramKey) {
            Map<String, Object> params = new HashMap<>();
            // 简单提取（实际使用 JSON 解析器）
            int start = json.indexOf("\"" + paramKey + "\"");
            if (start > 0) {
                int colon = json.indexOf(":", start);
                int quote1 = json.indexOf("\"", colon);
                int quote2 = json.indexOf("\"", quote1 + 1);
                if (colon > 0 && quote2 > quote1) {
                    params.put(paramKey, json.substring(quote1 + 1, quote2));
                }
            }
            return new IntentRequest(intent, params);
        }
    }
    
    /**
     * 构建命令
     * 
     * @param intent 意图
     * @param params 参数
     * @return 命令字符串
     */
    public static String build(Intent intent, Map<String, Object> params) {
        if (intent == null) {
            return null;
        }
        
        // 检查危险操作
        if (isForbidden(params)) {
            throw new SecurityException("检测到危险操作被禁止: " + params);
        }
        
        return switch (intent) {
            case READ_FILE -> buildReadFile(params);
            case LIST_FILES -> buildListFiles(params);
            case SEARCH_TEXT -> buildSearchText(params);
            case COUNT_LINES -> buildCountLines(params);
            case WRITE_FILE -> buildWriteFile(params);
            case COPY_FILE -> buildCopyFile(params);
            case MOVE_FILE -> buildMoveFile(params);
            case DELETE_FILE -> buildDeleteFile(params);
            case CREATE_DIR -> buildCreateDir(params);
            case EXECUTE_COMMAND -> buildExecute(params);
        };
    }
    
    /**
     * 构建意图请求的命令
     */
    public static String build(IntentRequest request) {
        return build(request.intent, request.params);
    }
    
    /**
     * 从 JSON 构建命令
     */
    public static String buildFromJson(String jsonIntent) {
        IntentRequest request = IntentRequest.fromJson(jsonIntent);
        if (request == null) {
            throw new IllegalArgumentException("无法解析意图: " + jsonIntent);
        }
        return build(request);
    }
    
    // ==================== Windows 实现 ====================
    
    private static String buildReadFile(Map<String, Object> params) {
        String path = getString(params, "path");
        if (path == null) {
            throw new IllegalArgumentException("read_file 需要 path 参数");
        }
        
        if (IS_WINDOWS) {
            return "Get-Content -Path \"" + escapePath(path) + "\" -Raw";
        } else {
            return "cat '" + escapePath(path) + "'";
        }
    }
    
    private static String buildListFiles(Map<String, Object> params) {
        String dir = getString(params, "dir", ".");
        boolean recursive = getBoolean(params, "recursive", false);
        
        if (IS_WINDOWS) {
            String cmd = "Get-ChildItem -Path \"" + escapePath(dir) + "\"";
            if (recursive) {
                cmd += " -Recurse";
            }
            cmd += " -File | Select-Object FullName";
            return cmd;
        } else {
            String cmd = "find '" + escapePath(dir) + "' -type f";
            return cmd;
        }
    }
    
    private static String buildSearchText(Map<String, Object> params) {
        String dir = getString(params, "dir", ".");
        String pattern = getString(params, "pattern");
        
        if (pattern == null) {
            throw new IllegalArgumentException("search_text 需要 pattern 参数");
        }
        
        if (IS_WINDOWS) {
            return "Select-String -Path \"" + escapePath(dir) + "\\*\" -Pattern \"" + pattern + "\"";
        } else {
            return "grep -r \"" + pattern + "\" \"" + escapePath(dir) + "\"";
        }
    }
    
    private static String buildCountLines(Map<String, Object> params) {
        String path = getString(params, "path");
        if (path == null) {
            throw new IllegalArgumentException("count_lines 需要 path 参数");
        }
        
        if (IS_WINDOWS) {
            return "(Get-Content \"" + escapePath(path) + "\").Count";
        } else {
            return "wc -l \"" + escapePath(path) + "\"";
        }
    }
    
    private static String buildWriteFile(Map<String, Object> params) {
        String path = getString(params, "path");
        String content = getString(params, "content", "");
        
        if (path == null) {
            throw new IllegalArgumentException("write_file 需要 path 参数");
        }
        
        // 写入文件（避免使用 echo）
        if (IS_WINDOWS) {
            return "Set-Content -Path \"" + escapePath(path) + "\" -Value @\"\n" + content + "\n\"@";
        } else {
            return "cat > '" + escapePath(path) + "' << 'EOF'\n" + content + "\nEOF";
        }
    }
    
    private static String buildCopyFile(Map<String, Object> params) {
        String src = getString(params, "src");
        String dest = getString(params, "dest");
        
        if (IS_WINDOWS) {
            return "Copy-Item -Path \"" + escapePath(src) + "\" -Destination \"" + escapePath(dest) + "\"";
        } else {
            return "cp '" + escapePath(src) + "' '" + escapePath(dest) + "'";
        }
    }
    
    private static String buildMoveFile(Map<String, Object> params) {
        String src = getString(params, "src");
        String dest = getString(params, "dest");
        
        if (IS_WINDOWS) {
            return "Move-Item -Path \"" + escapePath(src) + "\" -Destination \"" + escapePath(dest) + "\"";
        } else {
            return "mv '" + escapePath(src) + "' '" + escapePath(dest) + "'";
        }
    }
    
    private static String buildDeleteFile(Map<String, Object> params) {
        String path = getString(params, "path");
        
        if (IS_WINDOWS) {
            return "Remove-Item -Path \"" + escapePath(path) + "\"";
        } else {
            return "rm '" + escapePath(path) + "'";
        }
    }
    
    private static String buildCreateDir(Map<String, Object> params) {
        String dir = getString(params, "dir");
        
        if (dir == null) {
            throw new IllegalArgumentException("create_dir 需要 dir 参数");
        }
        
        if (IS_WINDOWS) {
            return "New-Item -Path \"" + escapePath(dir) + "\" -ItemType Directory -Force";
        } else {
            return "mkdir -p '" + escapePath(dir) + "'";
        }
    }
    
    private static String buildExecute(Map<String, Object> params) {
        String command = getString(params, "command");
        if (command == null) {
            throw new IllegalArgumentException("execute_command 需要 command 参数");
        }
        // 仍然检查危险操作
        if (isForbidden(command)) {
            throw new SecurityException("检测到危险命令: " + command);
        }
        return command;
    }
    
    // ==================== 辅助方法 ====================
    
    private static String getString(Map<String, Object> params, String key) {
        return getString(params, key, null);
    }
    
    private static String getString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private static boolean getBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    private static String escapePath(String path) {
        if (path == null) {
            return "";
        }
        // 转义反斜杠和引号
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    private static boolean isForbidden(Map<String, Object> params) {
        for (Object value : params.values()) {
            if (value instanceof String && isForbidden((String) value)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isForbidden(String command) {
        String lower = command.toLowerCase();
        for (String forbidden : FORBIDDEN_COMMANDS) {
            if (lower.contains(forbidden.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取 LLM 可见的意图清单（用于 Prompt）
     */
    public static String getIntentPrompt() {
        return """
            # 可用意图（使用这些格式，不要直接写 Shell 命令）
            
            {"action": "read_file", "path": "文件路径"}
            {"action": "list_files", "dir": "目录", "recursive": false}
            {"action": "search_text", "dir": "目录", "pattern": "正则"}
            {"action": "count_lines", "path": "文件路径"}
            {"action": "write_file", "path": "文件路径", "content": "内容"}
            {"action": "copy_file", "src": "源路径", "dest": "目标路径"}
            {"action": "delete_file", "path": "文件路径"}
            {"action": "create_dir", "dir": "目录路径"}
            
            # 禁止的操作
            ❌ rm -rf （删除文件夹请用 delete_file）
            ❌ format （磁盘格式化被禁止）
            ❌ &&, ||, |, ; （需要拆分为多个意图）
            """;
    }
}