package com.jwcode.core.aicl;

/**
 * AICL 块生命周期 — 6 状态自动流转。
 *
 * <pre>
 *     active → compressed → summarized → archived → deprecated → 删除
 *       ↑                                                    │
 *       └──── 用户主动查询（从 archived 恢复）─────────────────┘
 *     pinned: 不参与流转
 * </pre>
 *
 * <table>
 *   <tr><th>状态</th><th>含义</th><th>内容完整度</th><th>触发条件</th></tr>
 *   <tr><td>ACTIVE</td><td>活跃态</td><td>~100%</td><td>新建或从其他状态恢复</td></tr>
 *   <tr><td>COMPRESSED</td><td>压缩态</td><td>~80%</td><td>Token 达阈值，自动去空行/缩进/注释</td></tr>
 *   <tr><td>SUMMARIZED</td><td>摘要态</td><td>~20%</td><td>多轮未引用或 Assembler 主动压缩</td></tr>
 *   <tr><td>ARCHIVED</td><td>归档态</td><td>~5%</td><td>跨会话保留或长期未访问</td></tr>
 *   <tr><td>DEPRECATED</td><td>废弃态</td><td>0%</td><td>TTL 到期，等待清理</td></tr>
 *   <tr><td>PINNED</td><td>永固态</td><td>100%</td><td>人工标记，不参与流转</td></tr>
 * </table>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public enum BlockLifecycle {
    /** 活跃态 — 完整内容，正常解析 */
    ACTIVE("active", "活跃态", 1.0),
    /** 压缩态 — 已删除冗余格式，注意可能缺少换行/注释 */
    COMPRESSED("compressed", "压缩态", 0.8),
    /** 摘要态 — 仅保留核心结论，细节已压缩 */
    SUMMARIZED("summarized", "摘要态", 0.2),
    /** 归档态 — 仅保留元数据，内容已清空 */
    ARCHIVED("archived", "归档态", 0.05),
    /** 废弃态 — 即将删除，可忽略 */
    DEPRECATED("deprecated", "废弃态", 0.0),
    /** 永固态 — 不参与任何流转 */
    PINNED("pinned", "永固态", 1.0);

    private final String state;
    private final String description;
    private final double contentIntegrity;

    BlockLifecycle(String state, String description, double contentIntegrity) {
        this.state = state;
        this.description = description;
        this.contentIntegrity = contentIntegrity;
    }

    public String getState() { return state; }
    public String getDescription() { return description; }
    /** 内容完整度比例（0.0 ~ 1.0），用于估算有效 token 数 */
    public double getContentIntegrity() { return contentIntegrity; }

    /**
     * 生命周期衰减：返回下一级状态。
     * active → compressed → summarized → archived → deprecated（终态）
     * pinned 永远不衰减。
     */
    public BlockLifecycle decay() {
        return switch (this) {
            case ACTIVE -> COMPRESSED;
            case COMPRESSED -> SUMMARIZED;
            case SUMMARIZED -> ARCHIVED;
            case ARCHIVED -> DEPRECATED;
            case DEPRECATED, PINNED -> this;
        };
    }

    /**
     * 是否允许从该状态恢复（展开内容）。
     */
    public boolean canRestore() {
        return this == ARCHIVED || this == SUMMARIZED;
    }

    /**
     * 是否为终态（不可再衰减）。
     */
    public boolean isTerminal() {
        return this == DEPRECATED || this == PINNED;
    }

    /**
     * 内容是否仍有价值可读。
     */
    public boolean hasReadableContent() {
        return this != DEPRECATED && this != ARCHIVED;
    }
}
