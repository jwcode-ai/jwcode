package com.jwcode.core.code.api;

/**
 * 语法树统计
 */
public record TreeStats(
    int nodeCount,
    int namedNodeCount,
    int maxDepth,
    int errorNodeCount
) {
    public static TreeStats calculate(SyntaxTree tree) {
        StatsCollector collector = new StatsCollector();
        collector.visit(tree.getRootNode(), 0);
        return new TreeStats(
            collector.nodeCount,
            collector.namedNodeCount,
            collector.maxDepth,
            collector.errorNodeCount
        );
    }
    
    private static class StatsCollector {
        int nodeCount = 0;
        int namedNodeCount = 0;
        int maxDepth = 0;
        int errorNodeCount = 0;
        
        void visit(SyntaxNode node, int depth) {
            nodeCount++;
            maxDepth = Math.max(maxDepth, depth);
            
            if (node.isNamed()) {
                namedNodeCount++;
            }
            if (node.isError() || node.hasError()) {
                errorNodeCount++;
            }
            
            for (SyntaxNode child : node.getChildren()) {
                visit(child, depth + 1);
            }
        }
    }
}