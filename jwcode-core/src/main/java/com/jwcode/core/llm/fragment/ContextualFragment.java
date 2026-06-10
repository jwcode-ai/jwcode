package com.jwcode.core.llm.fragment;

/**
 * 有界可审计的模型上下文片段。
 *
 * <p>对标 Codex 的 ContextualUserFragment trait，每个片段：
 * <ul>
 *   <li>有唯一 ID 用于去重和审计追踪</li>
 *   <li>有类别用于排序</li>
 *   <li>有 token 估算用于预算控制</li>
 *   <li>可通过配置启用/禁用</li>
 * </ul>
 */
public interface ContextualFragment {

    /** 最大单片段 token 数 */
    int MAX_TOKENS_PER_FRAGMENT = 10_000;

    /** 片段唯一标识 */
    String getId();

    /** 片段类别（决定注入顺序） */
    FragmentCategory getCategory();

    /** 构建片段内容 */
    String build(FragmentContext ctx);

    /** 估算片段 token 数（4 字符 ≈ 1 token） */
    default int getTokenEstimate(FragmentContext ctx) {
        String content = build(ctx);
        return content == null ? 0 : (int) Math.ceil(content.length() / 4.0);
    }

    /** 是否默认启用 */
    default boolean isEnabledByDefault() {
        return true;
    }

    /** 去重标记（用于 hasRecentSystemPrompt 检查），null 表示不去重 */
    default String getDedupMarker() {
        return null;
    }
}
