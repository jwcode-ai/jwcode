package com.jwcode.core.code.semantic;

import java.util.Map;

/**
 * 图谱统计
 */
public record GraphStats(
    int nodeCount,
    int edgeCount,
    Map<SymbolKind, Long> nodeDistribution,
    Map<RelationType, Long> edgeDistribution
) {}