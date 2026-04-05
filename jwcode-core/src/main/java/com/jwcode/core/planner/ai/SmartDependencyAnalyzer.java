package com.jwcode.core.planner.ai;

import com.jwcode.core.planner.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SmartDependencyAnalyzer - 智能依赖分析器
 * 
 * 使用 AI 分析子任务间的依赖关系：
 * 1. 数据依赖 - 任务间的数据输入输出关系
 * 2. 逻辑依赖 - 业务逻辑上的先后关系
 * 3. 资源依赖 - 共享资源的竞争关系
 * 4. 优化依赖图 - 减少关键路径长度
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SmartDependencyAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(SmartDependencyAnalyzer.class);
    
    /**
     * 分析步骤间的依赖关系
     */
    public DependencyAnalysis analyze(List<PlanStep> steps) {
        log.info("[SmartDependencyAnalyzer] 分析 " + steps.size() + " 个步骤的依赖关系");
        
        // 1. 构建依赖图
        DependencyGraph graph = buildDependencyGraph(steps);
        
        // 2. 检测循环依赖
        List<List<String>> cycles = detectCycles(graph);
        if (!cycles.isEmpty()) {
            log.warn("[SmartDependencyAnalyzer] 检测到 " + cycles.size() + " 个循环依赖");
        }
        
        // 3. 计算关键路径
        List<String> criticalPath = calculateCriticalPath(graph, steps);
        
        // 4. 识别可并行组
        List<List<PlanStep>> parallelGroups = identifyParallelGroups(steps, graph);
        
        // 5. 优化建议
        List<OptimizationSuggestion> suggestions = generateOptimizationSuggestions(steps, graph, criticalPath);
        
        return DependencyAnalysis.builder()
            .graph(graph)
            .cycles(cycles)
            .criticalPath(criticalPath)
            .parallelGroups(parallelGroups)
            .suggestions(suggestions)
            .build();
    }
    
    /**
     * 构建依赖图
     */
    private DependencyGraph buildDependencyGraph(List<PlanStep> steps) {
        DependencyGraph graph = new DependencyGraph();
        
        for (PlanStep step : steps) {
            String nodeId = String.valueOf(step.getStepNumber());
            graph.addNode(nodeId, step);
            
            // 添加依赖边
            for (String dep : step.getDependencies()) {
                graph.addEdge(dep, nodeId, DependencyType.LOGICAL);
            }
        }
        
        return graph;
    }
    
    /**
     * 检测循环依赖
     */
    private List<List<String>> detectCycles(DependencyGraph graph) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        Map<String, String> path = new HashMap<>();
        
        for (String node : graph.getNodes()) {
            if (!visited.contains(node)) {
                detectCyclesDFS(node, graph, visited, stack, path, cycles);
            }
        }
        
        return cycles;
    }
    
    private void detectCyclesDFS(String node, DependencyGraph graph, Set<String> visited, 
                                 Set<String> stack, Map<String, String> path, List<List<String>> cycles) {
        visited.add(node);
        stack.add(node);
        
        for (String neighbor : graph.getNeighbors(node)) {
            if (!visited.contains(neighbor)) {
                path.put(neighbor, node);
                detectCyclesDFS(neighbor, graph, visited, stack, path, cycles);
            } else if (stack.contains(neighbor)) {
                // 发现循环
                List<String> cycle = new ArrayList<>();
                String current = node;
                while (current != null && !current.equals(neighbor)) {
                    cycle.add(current);
                    current = path.get(current);
                }
                if (current != null) {
                    cycle.add(neighbor);
                }
                Collections.reverse(cycle);
                cycles.add(cycle);
            }
        }
        
        stack.remove(node);
    }
    
    /**
     * 计算关键路径
     */
    private List<String> calculateCriticalPath(DependencyGraph graph, List<PlanStep> steps) {
        Map<String, Long> earliestStart = new HashMap<>();
        Map<String, Long> latestStart = new HashMap<>();
        
        // 拓扑排序
        List<String> topoOrder = topologicalSort(graph);
        
        // 计算最早开始时间（正向）
        for (String node : topoOrder) {
            long maxPrevEnd = 0;
            for (String prev : graph.getPredecessors(node)) {
                long prevEnd = earliestStart.getOrDefault(prev, 0L) + getNodeDuration(graph.getNodeData(prev));
                maxPrevEnd = Math.max(maxPrevEnd, prevEnd);
            }
            earliestStart.put(node, maxPrevEnd);
        }
        
        // 计算最晚开始时间（反向）
        long totalDuration = topoOrder.stream()
            .mapToLong(n -> earliestStart.getOrDefault(n, 0L) + getNodeDuration(graph.getNodeData(n)))
            .max().orElse(0);
        
        Collections.reverse(topoOrder);
        for (String node : topoOrder) {
            long minNextStart = totalDuration;
            for (String next : graph.getNeighbors(node)) {
                long nextStart = latestStart.getOrDefault(next, totalDuration);
                minNextStart = Math.min(minNextStart, nextStart - getNodeDuration(graph.getNodeData(node)));
            }
            latestStart.put(node, minNextStart);
        }
        
        // 关键路径：最早开始 == 最晚开始
        List<String> criticalPath = new ArrayList<>();
        for (String node : graph.getNodes()) {
            long es = earliestStart.getOrDefault(node, 0L);
            long ls = latestStart.getOrDefault(node, 0L);
            if (Math.abs(es - ls) < 1000) { // 1秒容差
                criticalPath.add(node);
            }
        }
        
        // 按拓扑排序
        Collections.sort(criticalPath, Comparator.comparingInt(topoOrder::indexOf));
        
        return criticalPath;
    }
    
    /**
     * 拓扑排序
     */
    private List<String> topologicalSort(DependencyGraph graph) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> tempMark = new HashSet<>();
        
        for (String node : graph.getNodes()) {
            if (!visited.contains(node)) {
                topologicalSortDFS(node, graph, visited, tempMark, result);
            }
        }
        
        return result;
    }
    
    private void topologicalSortDFS(String node, DependencyGraph graph, Set<String> visited, 
                                     Set<String> tempMark, List<String> result) {
        if (tempMark.contains(node)) {
            throw new IllegalStateException("图中存在循环依赖");
        }
        if (visited.contains(node)) {
            return;
        }
        
        tempMark.add(node);
        for (String neighbor : graph.getNeighbors(node)) {
            topologicalSortDFS(neighbor, graph, visited, tempMark, result);
        }
        tempMark.remove(node);
        visited.add(node);
        result.add(0, node);
    }
    
    /**
     * 识别可并行组
     */
    private List<List<PlanStep>> identifyParallelGroups(List<PlanStep> steps, DependencyGraph graph) {
        List<List<PlanStep>> groups = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        
        while (assigned.size() < steps.size()) {
            List<PlanStep> ready = steps.stream()
                .filter(s -> !assigned.contains(String.valueOf(s.getStepNumber())))
                .filter(s -> s.getDependencies().stream()
                    .allMatch(dep -> assigned.contains(dep) || !graph.hasNode(dep)))
                .collect(Collectors.toList());
            
            if (ready.isEmpty()) {
                ready = steps.stream()
                    .filter(s -> !assigned.contains(String.valueOf(s.getStepNumber())))
                    .limit(1)
                    .collect(Collectors.toList());
            }
            
            groups.add(ready);
            ready.forEach(s -> assigned.add(String.valueOf(s.getStepNumber())));
        }
        
        return groups;
    }
    
    /**
     * 生成优化建议
     */
    private List<OptimizationSuggestion> generateOptimizationSuggestions(List<PlanStep> steps, 
                                                                          DependencyGraph graph, 
                                                                          List<String> criticalPath) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        
        // 建议 1: 关键路径优化
        if (criticalPath.size() > steps.size() / 2) {
            suggestions.add(OptimizationSuggestion.builder()
                .type(SuggestionType.CRITICAL_PATH_OPTIMIZATION)
                .description("关键路径过长，建议重新设计任务分解策略")
                .priority(9)
                .build());
        }
        
        // 建议 2: 并行度优化
        long serialSteps = steps.stream()
            .filter(s -> !s.getDependencies().isEmpty())
            .count();
        long parallelSteps = steps.size() - serialSteps;
        
        if (serialSteps > parallelSteps * 2) {
            suggestions.add(OptimizationSuggestion.builder()
                .type(SuggestionType.INCREASE_PARALLELISM)
                .description("串行步骤过多，建议增加并行任务")
                .priority(8)
                .build());
        }
        
        return suggestions;
    }
    
    /**
     * 打破循环依赖
     */
    private void breakCycle(List<PlanStep> steps, List<String> cycle) {
        if (cycle.size() >= 2) {
            String weakestLink = cycle.get(cycle.size() - 1);
            String target = cycle.get(0);
            
            steps.stream()
                .filter(s -> String.valueOf(s.getStepNumber()).equals(target))
                .forEach(s -> s.getDependencies().remove(weakestLink));
        }
    }
    
    /**
     * 提取关键词
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        String[] words = text.toLowerCase().split("\\s+");
        for (String word : words) {
            if (word.length() > 3) {
                keywords.add(word.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", ""));
            }
        }
        return keywords;
    }
    
    /**
     * 获取节点预估耗时
     */
    private long getNodeDuration(PlanStep step) {
        return step != null ? step.getEstimatedTimeMs() : 60000;
    }
    
    // ==================== 数据类 ====================
    
    public static class DependencyAnalysis {
        private DependencyGraph graph;
        private List<List<String>> cycles;
        private List<String> criticalPath;
        private List<List<PlanStep>> parallelGroups;
        private List<OptimizationSuggestion> suggestions;
        
        public DependencyGraph getGraph() { return graph; }
        public void setGraph(DependencyGraph graph) { this.graph = graph; }
        public List<List<String>> getCycles() { return cycles; }
        public void setCycles(List<List<String>> cycles) { this.cycles = cycles; }
        public List<String> getCriticalPath() { return criticalPath; }
        public void setCriticalPath(List<String> criticalPath) { this.criticalPath = criticalPath; }
        public List<List<PlanStep>> getParallelGroups() { return parallelGroups; }
        public void setParallelGroups(List<List<PlanStep>> parallelGroups) { this.parallelGroups = parallelGroups; }
        public List<OptimizationSuggestion> getSuggestions() { return suggestions; }
        public void setSuggestions(List<OptimizationSuggestion> suggestions) { this.suggestions = suggestions; }
        
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("依赖分析结果:\n");
            sb.append("关键路径 (").append(criticalPath.size()).append(" 步):\n");
            sb.append("   ").append(String.join(" -> ", criticalPath)).append("\n");
            return sb.toString();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private DependencyGraph graph;
            private List<List<String>> cycles;
            private List<String> criticalPath;
            private List<List<PlanStep>> parallelGroups;
            private List<OptimizationSuggestion> suggestions;
            
            public Builder graph(DependencyGraph v) { this.graph = v; return this; }
            public Builder cycles(List<List<String>> v) { this.cycles = v; return this; }
            public Builder criticalPath(List<String> v) { this.criticalPath = v; return this; }
            public Builder parallelGroups(List<List<PlanStep>> v) { this.parallelGroups = v; return this; }
            public Builder suggestions(List<OptimizationSuggestion> v) { this.suggestions = v; return this; }
            
            public DependencyAnalysis build() {
                DependencyAnalysis a = new DependencyAnalysis();
                a.graph = graph;
                a.cycles = cycles;
                a.criticalPath = criticalPath;
                a.parallelGroups = parallelGroups;
                a.suggestions = suggestions;
                return a;
            }
        }
    }
    
    public static class OptimizationSuggestion {
        private SuggestionType type;
        private String description;
        private int priority;
        
        public SuggestionType getType() { return type; }
        public void setType(SuggestionType type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private SuggestionType type;
            private String description;
            private int priority;
            
            public Builder type(SuggestionType v) { this.type = v; return this; }
            public Builder description(String v) { this.description = v; return this; }
            public Builder priority(int v) { this.priority = v; return this; }
            
            public OptimizationSuggestion build() {
                OptimizationSuggestion s = new OptimizationSuggestion();
                s.type = type;
                s.description = description;
                s.priority = priority;
                return s;
            }
        }
    }
    
    public enum SuggestionType {
        CRITICAL_PATH_OPTIMIZATION,
        INCREASE_PARALLELISM,
        SIMPLIFY_DEPENDENCIES,
        REMOVE_REDUNDANT_DEPS
    }
    
    public enum DependencyType {
        DATA, LOGICAL, RESOURCE, IMPLICIT_DATA
    }
    
    /**
     * 依赖图
     */
    public static class DependencyGraph {
        private final Map<String, Node> nodes = new HashMap<>();
        private final Map<String, List<Edge>> edges = new HashMap<>();
        
        public void addNode(String id, PlanStep data) {
            nodes.put(id, new Node(id, data));
            edges.computeIfAbsent(id, k -> new ArrayList<>());
        }
        
        public void addEdge(String from, String to, DependencyType type) {
            edges.computeIfAbsent(from, k -> new ArrayList<>())
                .add(new Edge(from, to, type));
        }
        
        public boolean hasNode(String id) {
            return nodes.containsKey(id);
        }
        
        public boolean hasEdge(String from, String to) {
            return edges.getOrDefault(from, new ArrayList<>()).stream()
                .anyMatch(e -> e.to.equals(to));
        }
        
        public Set<String> getNodes() {
            return nodes.keySet();
        }
        
        public List<String> getNeighbors(String nodeId) {
            return edges.getOrDefault(nodeId, new ArrayList<>()).stream()
                .map(e -> e.to)
                .collect(Collectors.toList());
        }
        
        public List<String> getPredecessors(String nodeId) {
            List<String> preds = new ArrayList<>();
            for (Map.Entry<String, List<Edge>> entry : edges.entrySet()) {
                for (Edge edge : entry.getValue()) {
                    if (edge.to.equals(nodeId)) {
                        preds.add(entry.getKey());
                    }
                }
            }
            return preds;
        }
        
        public PlanStep getNodeData(String nodeId) {
            Node node = nodes.get(nodeId);
            return node != null ? node.data : null;
        }
        
        private static class Node {
            String id;
            PlanStep data;
            
            Node(String id, PlanStep data) {
                this.id = id;
                this.data = data;
            }
        }
        
        private static class Edge {
            String from;
            String to;
            DependencyType type;
            
            Edge(String from, String to, DependencyType type) {
                this.from = from;
                this.to = to;
                this.type = type;
            }
        }
    }
}
