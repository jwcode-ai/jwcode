package com.jwcode.core.tool;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 能力地图 — 从"平铺工具列表"到"能力空间"。
 *
 * <p>AI 不是"选择一个工具"，而是"导航到一项能力"。
 * 每个能力是一个解决目标问题的途径，包含多个候选工具。</p>
 *
 * <p>关键设计：能力按 {@link ToolCategory} 自动分组，AI 先定策略再选工具，
 * 降低"选错工具"的概率（例如用 FileEdit 去创建新文件）。</p>
 */
public class CapabilitySpace {

    private static final Logger logger = Logger.getLogger(CapabilitySpace.class.getName());

    private final Map<String, Capability> capabilities;
    private final ToolRegistry registry;

    public CapabilitySpace(ToolRegistry registry) {
        this.registry = registry;
        this.capabilities = buildCapabilities(registry);
    }

    /**
     * 从 ToolRegistry 自动构建能力地图
     */
    private static Map<String, Capability> buildCapabilities(ToolRegistry registry) {
        Map<String, Capability> map = new LinkedHashMap<>();

        for (ToolCategory category : ToolCategory.values()) {
            List<Tool<?, ?, ?>> tools = registry.getByCategory(category);
            if (tools.isEmpty()) {
                continue;
            }
            List<ToolRef> refs = tools.stream()
                .map(t -> new ToolRef.ByName(t.getName()))
                .collect(Collectors.toList());

            ToolRef recommended = refs.isEmpty() ? null : refs.get(0);

            map.put(category.name(), new Capability(
                category.name().toLowerCase().replace("_", "_"),
                category.getDescription(),
                refs,
                recommended,
                "When you need " + category.getDescription().toLowerCase()
            ));
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * 根据上下文推荐最佳工具
     *
     * <p>当前实现为启发式匹配：按关键词匹配能力描述，返回首选工具。
     * 未来可接入轻量级 LLM 做语义匹配。</p>
     */
    public ToolRef recommend(String taskDescription) {
        if (taskDescription == null || taskDescription.isBlank()) {
            return null;
        }
        String lower = taskDescription.toLowerCase();

        // 启发式关键词匹配
        for (Capability cap : capabilities.values()) {
            if (matches(cap, lower)) {
                logger.fine("CapabilitySpace matched '" + cap.name() + "' for task: " + taskDescription);
                return cap.recommended();
            }
        }

        // 退回到第一个可用工具
        List<Tool<?, ?, ?>> all = registry.getAllTools();
        if (!all.isEmpty()) {
            return new ToolRef.ByName(all.get(0).getName());
        }
        return null;
    }

    private boolean matches(Capability cap, String taskLower) {
        String desc = (cap.description() + " " + cap.whenToUse()).toLowerCase();
        String[] keywords = desc.split("[^a-z]+");
        for (String kw : keywords) {
            if (kw.length() > 2 && taskLower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Capability> getCapabilities() {
        return capabilities;
    }

    public List<Capability> getCapabilitiesByCategory(ToolCategory category) {
        return capabilities.values().stream()
            .filter(c -> c.name().equalsIgnoreCase(category.name()))
            .collect(Collectors.toList());
    }

    /**
     * 能力定义
     */
    public record Capability(
        String name,
        String description,
        List<ToolRef> tools,
        ToolRef recommended,
        String whenToUse
    ) {}
}
