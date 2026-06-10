package com.jwcode.core.llm.fragment;

/**
 * 片段构建结果。
 */
public record FragmentResult(
    String fragmentId,
    FragmentCategory category,
    int tokenCount,
    String content
) {
    public boolean isEmpty() {
        return content == null || content.isBlank();
    }
}
