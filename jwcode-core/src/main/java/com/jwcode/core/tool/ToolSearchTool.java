package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 工具搜索工具
 * 
 * 用于搜索可用工具，支持：
 * - 按名称关键词搜索
 * - 按描述关键词搜索
 * - 返回匹配的工具列表及其描述
 */
public class ToolSearchTool implements Tool<ToolSearchTool.Input, ToolSearchTool.Output, ToolSearchTool.Progress> {
    
    private static final Logger logger = Logger.getLogger(ToolSearchTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override
    public String getName() { 
        return "ToolSearch"; 
    }
    
    @Override
    public String getDescription() { 
        return "搜索可用工具。当你不确定使用哪个工具时使用。"; 
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<Input> getInputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 ToolSearch 工具搜索可用的工具。
               
               参数:
               - query: 搜索关键词（必需）
               
               示例:
               - {"query": "文件操作"} - 搜索文件相关工具
               - {"query": "git"} - 搜索 Git 相关工具
               - {"query": "搜索"} - 搜索搜索类工具
               
               注意:
               - 你不确定某个功能应该使用哪个工具时，可以使用此工具搜索
               - 返回匹配的工具列表及其描述
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { 
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "搜索关键词（支持工具名、描述、功能的模糊匹配）"
                        }
                    },
                    "required": ["query"]
                }
                """); 
        } catch (Exception e) { 
            return null; 
        }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                if (input == null || input.query == null || input.query.trim().isEmpty()) {
                    return ToolResult.error("query 参数是必需的");
                }
                
                String query = input.query.toLowerCase().trim();
                logger.info("[ToolSearch] Searching for tools with query: " + query);
                
                // 从 ToolExecutionContext 获取工具注册表
                List<ToolInfo> matchedTools = new ArrayList<>();
                
                if (context != null) {
                    // 尝试从 state 中获取 ToolRegistry（如果已注册）
                    Object registryObj = context.getState().get("toolRegistry");
                    if (registryObj instanceof ToolRegistry) {
                        ToolRegistry registry = (ToolRegistry) registryObj;
                        List<Tool<?, ?, ?>> enabledTools = registry.getAllTools();
                        
                        for (Tool<?, ?, ?> tool : enabledTools) {
                            // 匹配工具名称
                            boolean nameMatch = tool.getName() != null && 
                                tool.getName().toLowerCase().contains(query);
                            
                            // 匹配工具描述
                            boolean descMatch = tool.getDescription() != null && 
                                tool.getDescription().toLowerCase().contains(query);
                            
                            // 匹配 Prompt 中的关键词
                            boolean promptMatch = tool.getPrompt() != null && 
                                tool.getPrompt().toLowerCase().contains(query);
                            
                            if (nameMatch || descMatch || promptMatch) {
                                matchedTools.add(new ToolInfo(
                                    tool.getName(),
                                    tool.getDescription(),
                                    getCategories(tool)
                                ));
                            }
                        }
                    } else {
                        // 回退：提供常见工具的内置列表（不依赖注册表）
                        matchedTools = searchBuiltInTools(query);
                    }
                } else {
                    // 无 context 时使用内置列表
                    matchedTools = searchBuiltInTools(query);
                }
                
                // 构建输出
                Output output = new Output();
                output.success = true;
                output.results = matchedTools.stream()
                    .map(t -> "- **" + t.name + "**: " + t.description + 
                             (t.categories != null && !t.categories.isEmpty() 
                                ? " [" + String.join(", ", t.categories) + "]" 
                                : ""))
                    .collect(Collectors.toList());
                
                // 构建内容
                StringBuilder content = new StringBuilder();
                if (matchedTools.isEmpty()) {
                    content.append("没有找到匹配的工具。\n\n");
                    content.append("尝试使用更通用的关键词，如：\n");
                    content.append("- \"文件\" - 搜索文件操作工具\n");
                    content.append("- \"搜索\" - 搜索搜索类工具\n");
                    content.append("- \"执行\" - 搜索执行类工具\n");
                } else {
                    content.append("找到 ").append(matchedTools.size()).append(" 个匹配的工具：\n\n");
                    for (ToolInfo tool : matchedTools) {
                        content.append("### ").append(tool.name).append("\n");
                        content.append(tool.description).append("\n");
                        if (tool.categories != null && !tool.categories.isEmpty()) {
                            content.append("分类: ").append(String.join(", ", tool.categories)).append("\n");
                        }
                        content.append("\n");
                    }
                }
                
                logger.info("[ToolSearch] Found " + matchedTools.size() + " matching tools");
                
                ToolResult<Output> result = ToolResult.success(output);
                result.setContent(content.toString());
                return result;
                
            } catch (Exception e) {
                logger.severe("[ToolSearch] Search failed: " + e.getMessage());
                return ToolResult.error("工具搜索失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 根据工具名称推断分类
     */
    private List<String> getCategories(Tool<?, ?, ?> tool) {
        List<String> categories = new ArrayList<>();
        String name = tool.getName().toLowerCase();
        
        if (name.contains("file") || name.contains("read") || name.contains("write") || name.contains("edit")) {
            categories.add("文件操作");
        }
        if (name.contains("search") || name.contains("grep") || name.contains("glob")) {
            categories.add("搜索");
        }
        if (name.contains("git")) {
            categories.add("版本控制");
        }
        if (name.contains("agent")) {
            categories.add("多Agent协作");
        }
        if (name.contains("bash") || name.contains("shell") || name.contains("command")) {
            categories.add("命令执行");
        }
        if (name.contains("test")) {
            categories.add("测试");
        }
        if (name.contains("doc") || name.contains("readme")) {
            categories.add("文档");
        }
        if (name.contains("lsp")) {
            categories.add("代码分析");
        }
        
        return categories;
    }
    
    /**
     * 【新增】搜索内置工具列表（当无法从 ToolRegistry 获取时的回退方案）
     */
    private List<ToolInfo> searchBuiltInTools(String query) {
        List<ToolInfo> matchedTools = new ArrayList<>();
        
        // 内置工具注册信息（基于常见工具名称和描述）
        String[][] builtInTools = {
            {"FileReadTool", "读取文件内容", "文件操作"},
            {"FileWriteTool", "写入或创建文件", "文件操作"},
            {"FileEditTool", "编辑文件内容", "文件操作"},
            {"GlobTool", "使用 glob 模式搜索文件", "搜索"},
            {"GrepTool", "在文件中搜索文本内容", "搜索"},
            {"BashTool", "执行 Bash/Shell 命令", "命令执行"},
            {"PowerShellTool", "执行 PowerShell 命令", "命令执行"},
            {"GitTool", "执行 Git 版本控制操作", "版本控制"},
            {"AgentTool", "创建和管理多个 Agent 进行协作", "多Agent协作"},
            {"SmartAnalyzeTool", "智能分析代码或项目结构", "代码分析"},
            {"TaskCreateTool", "创建任务", "任务管理"},
            {"TaskListTool", "列出所有任务", "任务管理"},
            {"TodoWriteTool", "写入待办事项", "任务管理"},
            {"WebSearchTool", "搜索网络信息", "搜索"},
            {"WebFetchTool", "获取网页内容", "搜索"},
            {"LSPTool", "代码语言服务协议工具", "代码分析"},
            {"ToolSearchTool", "搜索可用工具", "搜索"}
        };
        
        for (String[] toolInfo : builtInTools) {
            String name = toolInfo[0].toLowerCase();
            String desc = toolInfo[1].toLowerCase();
            String category = toolInfo[2];
            
            if (name.contains(query) || desc.contains(query)) {
                List<String> categories = new ArrayList<>();
                categories.add(category);
                matchedTools.add(new ToolInfo(toolInfo[0], toolInfo[1], categories));
            }
        }
        
        return matchedTools;
    }
    
    // ==================== 内部类 ====================
    
    public static class Input { 
        public String query; 
    }
    
    public static class Output { 
        public boolean success; 
        public List<String> results; 
    }
    
    public static class Progress {
        private final String query;
        private final int toolsFound;
        
        public Progress(String query, int toolsFound) {
            this.query = query;
            this.toolsFound = toolsFound;
        }
        
        public String getQuery() { return query; }
        public int getToolsFound() { return toolsFound; }
    }
    
    private static class ToolInfo {
        final String name;
        final String description;
        final List<String> categories;
        
        ToolInfo(String name, String description, List<String> categories) {
            this.name = name;
            this.description = description;
            this.categories = categories;
        }
    }
}
