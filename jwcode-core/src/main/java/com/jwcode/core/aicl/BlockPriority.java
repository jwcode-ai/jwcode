package com.jwcode.core.aicl;

/**
 * AICL 块优先级 — 6 级 + 1 特殊（pinned 永固）。
 *
 * <p>控制淘汰引擎的处理顺序和策略：
 * 值越大越优先保留，淘汰时从低优先级开始处理。</p>
 *
 * <table>
 *   <tr><th>优先级</th><th>级别</th><th>淘汰动作</th><th>适用场景</th></tr>
 *   <tr><td>OPTIONAL</td><td>0</td><td>直接删除整个块</td><td>调试信息、详细推理过程</td></tr>
 *   <tr><td>LOW</td><td>1</td><td>归档（仅保留元数据+摘要句）</td><td>用户画像、过期记忆</td></tr>
 *   <tr><td>MEDIUM</td><td>2</td><td>摘要替换（保留骨架，内容变概述）</td><td>远轮历史、非核心工具清单</td></tr>
 *   <tr><td>HIGH</td><td>3</td><td>同义压缩（精简措辞，不丢语义）</td><td>近轮对话历史、关键推理链</td></tr>
 *   <tr><td>CRITICAL</td><td>4</td><td>仅删注释/空行</td><td>当前任务目标、关键约束</td></tr>
 *   <tr><td>PINNED</td><td>MAX</td><td>跳过，完整保留</td><td>系统指令、用户当前输入</td></tr>
 * </table>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public enum BlockPriority {
    /** 可选 — 允许直接删除整个块（调试信息、推理过程） */
    OPTIONAL(0, "可选", "允许直接删除整个块"),
    /** 低 — 允许归档（仅保留标签+摘要句，内容清空） */
    LOW(1, "低", "允许归档（仅保留标签+摘要句，内容清空）"),
    /** 中 — 允许摘要替换（保留骨架，内容变概述） */
    MEDIUM(2, "中", "允许摘要替换（保留骨架，内容变概述）"),
    /** 高 — 允许同义压缩（精简措辞，不丢语义） */
    HIGH(3, "高", "允许同义压缩（精简措辞，不丢语义）"),
    /** 关键 — 不可裁剪，仅允许删除注释/空行等冗余 */
    CRITICAL(4, "关键", "不可裁剪，仅允许删除注释/空行等冗余"),
    /** 永固 — 不参与任何淘汰，始终完整保留 */
    PINNED(Integer.MAX_VALUE, "永固", "不参与任何淘汰，始终完整保留");

    private final int level;
    private final String name;
    private final String description;

    BlockPriority(int level, String name, String description) {
        this.level = level;
        this.name = name;
        this.description = description;
    }

    public int getLevel() { return level; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    /**
     * 获取该优先级对应的淘汰动作。
     */
    public EvictionAction getEvictionAction() {
        return switch (this) {
            case OPTIONAL -> EvictionAction.REMOVE;
            case LOW -> EvictionAction.ARCHIVE;
            case MEDIUM -> EvictionAction.SUMMARIZE;
            case HIGH -> EvictionAction.COMPRESS;
            case CRITICAL -> EvictionAction.TRIM_COMMENTS;
            case PINNED -> EvictionAction.SKIP;
        };
    }

    /**
     * 是否为受保护优先级（不可删除全部内容）。
     */
    public boolean isProtected() {
        return this == PINNED || this == CRITICAL;
    }

    /**
     * 淘汰动作枚举。
     */
    public enum EvictionAction {
        /** 直接删除整个块 */
        REMOVE,
        /** 归档：保留元数据+摘要句，清空内容 */
        ARCHIVE,
        /** 摘要替换：content 替换为 summary */
        SUMMARIZE,
        /** 同义压缩：删除空行/缩进/注释 */
        COMPRESS,
        /** 仅删除注释：trim 但保留内容 */
        TRIM_COMMENTS,
        /** 跳过不处理 */
        SKIP
    }
}
