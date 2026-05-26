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
        if (toolsByName.containsKey(name)) {
            logger.warning("Tool with name '" + name + "' is already registered, skipping");
            return this;
        }
        
        toolsByName.put(name, tool);
        tools.add(tool);
        
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

    // ==================== AI-Native ToolRef 查询 ====================

    /**
     * 通过 {@link ToolRef} 查找工具
     */
    public Tool<?, ?, ?> get(ToolRef ref) {
        if (ref instanceof ToolRef.ByName byName) {
            return getByName(byName.name());
        } else if (ref instanceof ToolRef.ByType byType) {
            for (Tool<?, ?, ?> tool : tools) {
                if (byType.type().isInstance(tool)) {
                    return tool;
                }
            }
            throw new NoSuchElementException("Tool not found by type: " + byType.type().getName());
        } else if (ref instanceof ToolRef.Composite composite) {
            throw new IllegalArgumentException("Cannot resolve Composite ToolRef to single tool");
        }
        throw new IllegalArgumentException("Unknown ToolRef type: " + ref.getClass());
    }

    /**
     * 按类别获取工具
     */
    public List<Tool<?, ?, ?>> getByCategory(ToolCategory category) {
        return tools.stream()
            .filter(t -> t.getCategory() == category)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 按副作用类型获取工具
     */
    public List<Tool<?, ?, ?>> getBySideEffect(SideEffect sideEffect) {
        return tools.stream()
            .filter(t -> t.getSideEffects().contains(sideEffect))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取所有只读工具（无副作用或仅 READ_ONLY）
     */
    public List<Tool<?, ?, ?>> getReadOnlyTools() {
        return tools.stream()
            .filter(t -> t.getSideEffects().isEmpty() ||
                (t.getSideEffects().size() == 1 && t.getSideEffects().contains(SideEffect.READ_ONLY)))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取所有可并行执行的工具
     */
    public List<Tool<?, ?, ?>> getParallelSafeTools() {
        return tools.stream()
            .filter(t -> t.getConcurrencyLevel() == ConcurrencyLevel.PARALLEL_SAFE)
            .collect(java.util.stream.Collectors.toList());
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
     * <p>使用静态缓存，确保全局只有一个默认 ToolRegistry 实例。
     * 避免各 Agent 构造函数重复创建导致的性能浪费和日志刷屏。</p>
     * 
     * @return 包含所有内置工具的注册表（单例）
     */
    public static ToolRegistry createDefault() {
        return DefaultInstanceHolder.INSTANCE;
    }
    
    /**
     * 静态内部类持有单例，利用 JVM 类加载机制保证线程安全
     */
    private static final class DefaultInstanceHolder {
        static final ToolRegistry INSTANCE = createNewDefault();
    }
    
    /**
     * 实际创建默认 ToolRegistry 的方法（仅调用一次）
     */
    private static ToolRegistry createNewDefault() {
        ToolRegistry registry = new ToolRegistry();
        
        // 注册内置工具 - 与 JavaScript 项目对齐
        registry.register(new BashTool());
        registry.register(new FileReadTool());
        registry.register(new BatchReadTool());
        registry.register(new MergeFilesTool());
        registry.register(new FileEditTool());
        registry.register(new FileWriteTool());
        registry.register(new DownloadTool());
        registry.register(new GrepTool());
        registry.register(new GlobTool());
        registry.register(new WebFetchTool());
        registry.register(new WebSearchTool());
        registry.register(new TodoWriteTool());
        registry.register(new AgentTool());
        registry.register(new TaskOutputTool());
        registry.register(new TaskStopTool());
        registry.register(new AskUserQuestionTool());
        registry.register(new EnterPlanModeTool());
        registry.register(new ExitPlanModeV2Tool());
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
        registry.register(new SmartAnalyzeTool(new com.jwcode.core.code.analysis.TreeSitterCodeSemanticAnalyzer()));
        registry.register(new MultiPlanTool());
        registry.register(new EnterWorktreeTool());
        registry.register(new ExitWorktreeTool());
        registry.register(new WorktreeListTool());
        registry.register(new SyntheticOutputTool());
        registry.register(new McpAuthTool());
        
        // 注意：SemanticSearchTool 需要 CodebaseIndexer 实例，
        // 无法在此处注册。请使用 registerSemanticSearch() 方法。
        
        logger.info("[ToolRegistry] 已注册 " + registry.size() + " 个内置工具");
        
        return registry;
    }
    
    /**
     * 注册语义搜索工具（需要 CodebaseIndexer 实例）
     *
     * @param indexer 代码库索引器
     * @return 注册后的工具实例
     */
    public SemanticSearchTool registerSemanticSearch(
            com.jwcode.core.index.CodebaseIndexer indexer) {
        String toolName = "SemanticSearchTool";
        // 幂等性检查：如果已注册则直接返回现有实例
        if (toolsByName.containsKey(toolName)) {
            logger.info("[ToolRegistry] SemanticSearchTool 已存在，跳过重复注册");
            return (SemanticSearchTool) toolsByName.get(toolName);
        }
        SemanticSearchTool tool = new SemanticSearchTool(indexer);
        register(tool);
        logger.info("[ToolRegistry] SemanticSearchTool 已注册");
        return tool;
    }
    
}
