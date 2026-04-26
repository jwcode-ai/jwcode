package com.jwcode.core.code.semantic;

import com.jwcode.core.code.api.Range;
import com.jwcode.core.code.api.SyntaxNode;
import java.util.*;

/**
 * 符号图谱 - 代码符号及关系的图表示
 * 
 * <p>支持的关系类型：</p>
 * <ul>
 *   <li>{@link RelationType#DEFINES} - 定义关系（类定义了方法）</li>
 *   <li>{@link RelationType#REFERENCES} - 引用关系（方法调用了另一个方法）</li>
 *   <li>{@link RelationType#CALLS} - 调用关系</li>
 *   <li>{@link RelationType#INHERITS} - 继承关系</li>
 *   <li>{@link RelationType#IMPLEMENTS} - 实现关系</li>
 *   <li>{@link RelationType#IMPORTS} - 导入关系</li>
 *   <li>{@link RelationType#CONTAINS} - 包含关系（文件包含类）</li>
 * </ul>
 * 
 * <p>应用场景：</p>
 * <ul>
 *   <li>查找定义（Go to Definition）</li>
 *   <li>查找引用（Find References）</li>
 *   <li>调用图分析（Call Graph）</li>
 *   <li>依赖分析（Dependency Analysis）</li>
 *   <li>影响分析（Impact Analysis）</li>
 * </ul>
 * 
 * @author JwCode Team
 * @since 2.0.0
 */
public class SymbolGraph {
    
    private final Map<String, SymbolNode> nodes = new HashMap<>();
    private final Map<String, Set<SymbolEdge>> outgoingEdges = new HashMap<>();
    private final Map<String, Set<SymbolEdge>> incomingEdges = new HashMap<>();
    
    // ========== 节点管理 ==========
    
    /**
     * 添加符号节点
     */
    public SymbolNode addNode(SymbolNode node) {
        nodes.put(node.getId(), node);
        outgoingEdges.putIfAbsent(node.getId(), new HashSet<>());
        incomingEdges.putIfAbsent(node.getId(), new HashSet<>());
        return node;
    }
    
    /**
     * 获取或创建节点
     */
    public SymbolNode getOrCreateNode(String id, String name, SymbolKind kind, Location location) {
        return nodes.computeIfAbsent(id, k -> {
            SymbolNode node = new SymbolNode(k, name, kind, location);
            outgoingEdges.putIfAbsent(k, new HashSet<>());
            incomingEdges.putIfAbsent(k, new HashSet<>());
            return node;
        });
    }
    
    /**
     * 获取节点
     */
    public Optional<SymbolNode> getNode(String id) {
        return Optional.ofNullable(nodes.get(id));
    }
    
    /**
     * 通过名称查找节点（可能有多个）
     */
    public List<SymbolNode> findNodesByName(String name) {
        return nodes.values().stream()
            .filter(n -> n.getName().equals(name))
            .toList();
    }
    
    /**
     * 通过完全限定名查找节点
     */
    public Optional<SymbolNode> findNodeByQualifiedName(String qualifiedName) {
        return nodes.values().stream()
            .filter(n -> n.getQualifiedName().equals(qualifiedName))
            .findFirst();
    }
    
    // ========== 边管理 ==========
    
    /**
     * 添加关系边
     */
    public void addEdge(SymbolEdge edge) {
        outgoingEdges.get(edge.getFrom()).add(edge);
        incomingEdges.get(edge.getTo()).add(edge);
    }
    
    /**
     * 添加关系（便捷方法）
     */
    public void addRelation(String fromId, String toId, RelationType type) {
        SymbolEdge edge = new SymbolEdge(fromId, toId, type);
        addEdge(edge);
    }
    
    /**
     * 添加关系（带位置信息）
     */
    public void addRelation(String fromId, String toId, RelationType type, SyntaxNode sourceNode) {
        SymbolEdge edge = new SymbolEdge(fromId, toId, type, 
            Location.from(sourceNode.getRange()), sourceNode);
        addEdge(edge);
    }
    
    // ========== 查询接口 ==========
    
    /**
     * 查找定义（Go to Definition）
     * @param referenceId 引用节点的 ID
     * @return 定义该符号的节点
     */
    public List<SymbolNode> findDefinitions(String referenceId) {
        return getNode(referenceId)
            .map(ref -> incomingEdges.getOrDefault(referenceId, Set.of()).stream()
                .filter(e -> e.getType() == RelationType.DEFINES)
                .map(e -> nodes.get(e.getFrom()))
                .filter(Objects::nonNull)
                .toList())
            .orElse(List.of());
    }
    
    /**
     * 查找引用（Find References）
     * @param definitionId 定义节点的 ID
     * @return 所有引用该符号的节点
     */
    public List<SymbolNode> findReferences(String definitionId) {
        return outgoingEdges.getOrDefault(definitionId, Set.of()).stream()
            .filter(e -> e.getType() == RelationType.REFERENCES || e.getType() == RelationType.CALLS)
            .map(e -> nodes.get(e.getTo()))
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * 查找调用者（谁调用了这个方法）
     * @param methodId 方法节点的 ID
     * @return 调用该方法的节点
     */
    public List<SymbolNode> findCallers(String methodId) {
        return incomingEdges.getOrDefault(methodId, Set.of()).stream()
            .filter(e -> e.getType() == RelationType.CALLS)
            .map(e -> nodes.get(e.getFrom()))
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * 查找被调用的方法（这个方法调用了谁）
     * @param methodId 方法节点的 ID
     * @return 被该方法调用的节点
     */
    public List<SymbolNode> findCallees(String methodId) {
        return outgoingEdges.getOrDefault(methodId, Set.of()).stream()
            .filter(e -> e.getType() == RelationType.CALLS)
            .map(e -> nodes.get(e.getTo()))
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * 获取完整的调用图（递归获取所有调用关系）
     * @param methodId 起始方法
     * @param maxDepth 最大递归深度（防止循环）
     * @return 调用子图
     */
    public SymbolGraph getCallGraph(String methodId, int maxDepth) {
        SymbolGraph subgraph = new SymbolGraph();
        Set<String> visited = new HashSet<>();
        buildCallGraphRecursive(methodId, maxDepth, 0, visited, subgraph);
        return subgraph;
    }
    
    private void buildCallGraphRecursive(String nodeId, int maxDepth, int currentDepth, 
                                        Set<String> visited, SymbolGraph subgraph) {
        if (currentDepth > maxDepth || visited.contains(nodeId)) {
            return;
        }
        visited.add(nodeId);
        
        SymbolNode node = nodes.get(nodeId);
        if (node != null) {
            subgraph.addNode(node);
            
            for (SymbolEdge edge : outgoingEdges.getOrDefault(nodeId, Set.of())) {
                if (edge.getType() == RelationType.CALLS) {
                    subgraph.addEdge(edge);
                    buildCallGraphRecursive(edge.getTo(), maxDepth, currentDepth + 1, visited, subgraph);
                }
            }
        }
    }
    
    /**
     * 查找依赖路径（从 A 到 B 的依赖链）
     */
    public List<List<SymbolNode>> findDependencyPath(String fromId, String toId) {
        List<List<SymbolNode>> paths = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<SymbolNode> currentPath = new ArrayList<>();
        
        findPathDFS(fromId, toId, visited, currentPath, paths);
        return paths;
    }
    
    private void findPathDFS(String current, String target, Set<String> visited,
                            List<SymbolNode> currentPath, List<List<SymbolNode>> paths) {
        if (visited.contains(current)) return;
        
        SymbolNode node = nodes.get(current);
        if (node == null) return;
        
        visited.add(current);
        currentPath.add(node);
        
        if (current.equals(target)) {
            paths.add(new ArrayList<>(currentPath));
        } else {
            for (SymbolEdge edge : outgoingEdges.getOrDefault(current, Set.of())) {
                findPathDFS(edge.getTo(), target, visited, currentPath, paths);
            }
        }
        
        currentPath.remove(currentPath.size() - 1);
        visited.remove(current);
    }
    
    /**
     * 影响分析：找出修改某个符号会影响哪些其他符号
     * @param changedSymbolId 被修改的符号
     * @return 受影响的符号集合
     */
    public Set<SymbolNode> findImpactedSymbols(String changedSymbolId) {
        Set<SymbolNode> impacted = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(changedSymbolId);
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);
            
            // 查找所有反向依赖（谁依赖了当前符号）
            for (SymbolEdge edge : incomingEdges.getOrDefault(current, Set.of())) {
                SymbolNode dependent = nodes.get(edge.getFrom());
                if (dependent != null) {
                    impacted.add(dependent);
                    queue.add(edge.getFrom());
                }
            }
        }
        
        return impacted;
    }
    
    // ========== 图统计 ==========
    
    /**
     * 获取所有节点
     */
    public Collection<SymbolNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }
    
    /**
     * 获取所有边
     */
    public Collection<SymbolEdge> getAllEdges() {
        Set<SymbolEdge> allEdges = new HashSet<>();
        outgoingEdges.values().forEach(allEdges::addAll);
        return Collections.unmodifiableCollection(allEdges);
    }
    
    /**
     * 获取节点数量
     */
    public int getNodeCount() {
        return nodes.size();
    }
    
    /**
     * 获取边数量
     */
    public int getEdgeCount() {
        return outgoingEdges.values().stream().mapToInt(Set::size).sum();
    }
    
    /**
     * 获取统计信息
     */
    public com.jwcode.core.code.semantic.GraphStats getStats() {
        Map<SymbolKind, Long> nodeDistribution = nodes.values().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                SymbolNode::getKind, java.util.stream.Collectors.counting()));
        
        Map<RelationType, Long> edgeDistribution = getAllEdges().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                SymbolEdge::getType, java.util.stream.Collectors.counting()));
        
        return new com.jwcode.core.code.semantic.GraphStats(nodes.size(), getEdgeCount(), nodeDistribution, edgeDistribution);
    }
    
    // ========== 合并操作 ==========
    
    /**
     * 合并另一个图谱
     */
    public void merge(SymbolGraph other) {
        other.nodes.values().forEach(this::addNode);
        other.getAllEdges().forEach(this::addEdge);
    }
}

/**
 * 符号边（关系）
 */
class SymbolEdge {
    private final String from;
    private final String to;
    private final RelationType type;
    private final Location location;
    private final SyntaxNode sourceNode;
    
    public SymbolEdge(String from, String to, RelationType type) {
        this(from, to, type, null, null);
    }
    
    public SymbolEdge(String from, String to, RelationType type, Location location, SyntaxNode sourceNode) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.location = location;
        this.sourceNode = sourceNode;
    }
    
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public RelationType getType() { return type; }
    public Location getLocation() { return location; }
    public SyntaxNode getSourceNode() { return sourceNode; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolEdge)) return false;
        SymbolEdge that = (SymbolEdge) o;
        return from.equals(that.from) && to.equals(that.to) && type == that.type;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(from, to, type);
    }
}


