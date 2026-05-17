package com.jwcode.core.tool;

import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ToolOrchestrator - 工具编排器
 *
 * <p>负责按照依赖关系编排和执行已注册的工具。
 * 支持拓扑排序的依赖执行、并行执行和结果收集。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class ToolOrchestrator {

    private static final Logger logger = Logger.getLogger(ToolOrchestrator.class.getName());

    private final ToolRegistry registry;
    private final Map<String, ToolResult<?>> results;

    /**
     * 构造函数
     *
     * @param registry 工具注册表
     */
    public ToolOrchestrator(ToolRegistry registry) {
        this.registry = registry;
        this.results = new ConcurrentHashMap<>();
    }

    /**
     * 执行所有已注册的工具（按依赖顺序）
     *
     * @return 包含所有工具执行结果的 CompletableFuture
     */
    public CompletableFuture<Map<String, ToolResult<?>>> executeAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<Tool<?, ?, ?>> tools = registry.getAllTools();
            if (tools.isEmpty()) {
                return Collections.unmodifiableMap(new HashMap<>(results));
            }

            // 按依赖关系拓扑排序
            List<Tool<?, ?, ?>> sorted = topologicalSort(tools);

            // 依次执行
            for (Tool<?, ?, ?> tool : sorted) {
                try {
                    ToolResult<?> result = executeTool(tool);
                    results.put(tool.getName(), result);
                } catch (Exception e) {
                    ToolResult<?> errorResult = new ToolResult<>();
                    errorResult.setSuccess(false);
                    errorResult.setContent("Execution failed: " + e.getMessage());
                    results.put(tool.getName(), errorResult);
                }
            }

            return Collections.unmodifiableMap(new HashMap<>(results));
        });
    }

    /**
     * 执行单个工具
     *
     * @param tool 要执行的工具
     * @return 执行结果
     */
    @SuppressWarnings("unchecked")
    private <I, O, P> ToolResult<O> executeTool(Tool<I, O, P> tool) {
        try {
            // 通过 ToolExecutor 执行
            ToolExecutor executor = new ToolExecutor(registry);
            ToolExecutionContext context = ToolExecutionContext.builder().build();
            I input = null; // 使用 null 输入，由具体工具处理
            CompletableFuture<ToolResult<O>> future = executor.execute(tool, input, context);
            return future.get();
        } catch (Exception e) {
            logger.warning("Tool execution failed: " + tool.getName() + " - " + e.getMessage());
            ToolResult<O> errorResult = new ToolResult<>();
            errorResult.setSuccess(false);
            errorResult.setContent(e.getMessage());
            return errorResult;
        }
    }

    /**
     * 拓扑排序（按依赖关系）
     */
    private List<Tool<?, ?, ?>> topologicalSort(List<Tool<?, ?, ?>> tools) {
        Map<String, Tool<?, ?, ?>> toolMap = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();

        for (Tool<?, ?, ?> tool : tools) {
            String name = tool.getName();
            toolMap.put(name, tool);
            inDegree.putIfAbsent(name, 0);
            adjacency.putIfAbsent(name, new ArrayList<>());
        }

        // 构建依赖图
        for (Tool<?, ?, ?> tool : tools) {
            String name = tool.getName();
            List<String> deps = tool.getDependencies();
            if (deps != null) {
                for (String dep : deps) {
                    if (toolMap.containsKey(dep)) {
                        adjacency.computeIfAbsent(dep, k -> new ArrayList<>()).add(name);
                        inDegree.merge(name, 1, Integer::sum);
                    }
                }
            }
        }

        // Kahn 算法
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<Tool<?, ?, ?>> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            sorted.add(toolMap.get(name));

            for (String neighbor : adjacency.getOrDefault(name, Collections.emptyList())) {
                inDegree.merge(neighbor, -1, Integer::sum);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // 如果有环，将剩余工具追加到末尾
        for (Tool<?, ?, ?> tool : tools) {
            if (!sorted.contains(tool)) {
                sorted.add(tool);
            }
        }

        return sorted;
    }

    /**
     * 获取执行结果
     *
     * @param toolName 工具名称
     * @return 执行结果，如果不存在则返回空
     */
    public Optional<ToolResult<?>> getResult(String toolName) {
        return Optional.ofNullable(results.get(toolName));
    }

    /**
     * 清除所有执行结果
     */
    public void clearResults() {
        results.clear();
    }
}
