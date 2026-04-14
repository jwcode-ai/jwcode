package com.jwcode.core.tool;

import com.jwcode.common.util.Preconditions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ToolRegistry - 工具注册表
 * 
 * 功能说明：
 * 管理所有可用工具的注册、查找和枚举。
 * 提供线程安全的工具注册和访问功能。
 * 
 * 上下文关系：
 * - 被 ToolContext 引用
 * - 被 ToolExecutor 用来查找要执行的工具
 * - 在应用启动时注册所有内置工具
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ToolRegistry {
    
    private static final Logger logger = Logger.getLogger(ToolRegistry.class.getName());
    
    /**
     * 工具映射表（名称 -> 工具实例）
     */
    private final Map<String, Tool<?, ?, ?>> toolsByName;
    
    /**
     * 工具列表（保持注册顺序）
     */
    private final List<Tool<?, ?, ?>> tools;
    
    /**
     * 构造函数
     */
    public ToolRegistry() {
        this.toolsByName = new ConcurrentHashMap<>();
        this.tools = new ArrayList<>();
    }
    
    /**
     * 注册工具
     * 
     * @param tool 要注册的工具
     * @return this（用于链式调用）
     * @throws IllegalArgumentException 如果工具名称已存在
     */
    @SuppressWarnings("unchecked")
    public ToolRegistry register(Tool<?, ?, ?> tool) {
        Preconditions.checkNotNull(tool, "tool cannot be null");
        String name = tool.getName();
        Preconditions.checkArgument(!toolsByName.containsKey(name), 
                "Tool with name '%s' is already registered", name);
        
        toolsByName.put(name, tool);
        tools.add(tool);
        
        // 注册别名
        if (tool instanceof ToolWrapper) {
            ToolWrapper<?, ?, ?> wrapper = (ToolWrapper<?, ?, ?>) tool;
            for (String alias : wrapper.getAliases()) {
                toolsByName.put(alias, tool);
            }
        }
        
        // 记录工具注册日志
        logger.info("[ToolRegistry] 注册工具: " + name);
        
        return this;
    }
    
    /**
     * 根据名称查找工具
     * 
     * @param name 工具名称
     * @return Optional 包含找到的工具，如果没有找到则为空
     */
    @SuppressWarnings("unchecked")
    public <T extends Tool<?, ?, ?>> Optional<T> findByName(String name) {
        Preconditions.checkNotNull(name, "name cannot be null");
        return Optional.ofNullable((T) toolsByName.get(name));
    }
    
    /**
     * 根据名称获取工具，如果不存在则抛出异常
     * 
     * @param name 工具名称
     * @return 找到的工具
     * @throws NoSuchElementException 如果工具不存在
     */
    public Tool<?, ?, ?> getByName(String name) {
        return findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Tool not found: " + name));
    }
    
    /**
     * 根据名称获取工具（兼容性方法）
     * 
     * @param name 工具名称
     * @return 找到的工具
     * @throws NoSuchElementException 如果工具不存在
     */
    public Tool<?, ?, ?> getTool(String name) {
        return getByName(name);
    }
    
    /**
     * 检查是否包含指定名称的工具
     * 
     * @param name 工具名称
     * @return true 如果包含该工具
     */
    public boolean contains(String name) {
        return toolsByName.containsKey(name);
    }
    
    /**
     * 获取所有已注册的工具
     * 
     * @return 工具列表（不可变）
     */
    public List<Tool<?, ?, ?>> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools));
    }
    
    /**
     * 获取所有工具名称
     * 
     * @return 工具名称集合（不可变）
     */
    public Set<String> getAllToolNames() {
        return Collections.unmodifiableSet(new HashSet<>(toolsByName.keySet()));
    }
    
    /**
     * 获取已注册工具的数量
     * 
     * @return 工具数量
     */
    public int size() {
        return tools.size();
    }
    
    /**
     * 清空所有注册的工具
     */
    public void clear() {
        toolsByName.clear();
        tools.clear();
    }
    
    /**
     * 移除指定名称的工具
     * 
     * @param name 工具名称
     * @return 被移除的工具，如果不存在则返回 null
     */
    public Tool<?, ?, ?> unregister(String name) {
        Tool<?, ?, ?> tool = toolsByName.remove(name);
        if (tool != null) {
            tools.remove(tool);
        }
        return tool;
    }
    
    /**
     * 创建默认的工具注册表（包含所有内置工具）
     * 
     * @return 包含所有内置工具的注册表
     */
    public static ToolRegistry createDefault() {
        ToolRegistry registry = new ToolRegistry();
        
        // 注册内置工具 - 与 JavaScript 项目对齐
        registry.register(new BashTool());
        registry.register(new FileReadTool());
        registry.register(new FileEditTool());
        registry.register(new FileWriteTool());
        registry.register(new GrepTool());
        registry.register(new GlobTool());
        registry.register(new WebFetchTool());
        registry.register(new WebSearchTool());
        registry.register(new TodoWriteTool());
        registry.register(new AgentTool());
        registry.register(new TaskOutputTool());
        registry.register(new TaskStopTool());
        registry.register(new SkillTool());
        registry.register(new AskUserQuestionTool());
        registry.register(new EnterPlanModeTool());
        registry.register(new ExitPlanModeTool());
        registry.register(new ExitPlanModeV2Tool());
        registry.register(new BriefTool());
        registry.register(new REPLTool());
        registry.register(new GitTool());
        registry.register(new LSPTool());
        registry.register(new MCPTool());
        registry.register(new ListMcpResourcesTool());
        registry.register(new ReadMcpResourceTool());
        registry.register(new ToolSearchTool());
        registry.register(new ConfigTool());
        registry.register(new PatternTool());
        registry.register(new NotebookEditTool());
        registry.register(new SleepTool());
        registry.register(new RemoteTriggerTool());
        registry.register(new ScheduleCronTool());
        registry.register(new TeamCreateTool());
        registry.register(new TeamDeleteTool());
        registry.register(new TeamListTool());
        registry.register(new SendMessageTool());
        registry.register(new TaskCreateTool());
        registry.register(new TaskGetTool());
        registry.register(new TaskUpdateTool());
        registry.register(new TaskListTool());
        registry.register(new PowerShellTool());
        registry.register(new SmartAnalyzeTool());
        registry.register(new MultiPlanTool());
        registry.register(new EnterWorktreeTool());
        registry.register(new ExitWorktreeTool());
        registry.register(new WorktreeListTool());
        registry.register(new SyntheticOutputTool());
        registry.register(new McpAuthTool());
        
        logger.info("[ToolRegistry] 已注册 " + registry.size() + " 个内置工具");
        
        return registry;
    }
    
    /**
     * 工具包装器（支持别名）
     */
    public interface ToolWrapper<Input, Output, Progress> extends Tool<Input, Output, Progress> {
        
        /**
         * 获取工具别名列表
         * 
         * @return 别名列表
         */
        List<String> getAliases();
    }
}
