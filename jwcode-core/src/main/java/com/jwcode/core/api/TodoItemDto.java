package com.jwcode.core.api;

/**
 * TodoItemDto — 待办事项数据传输对象。
 *
 * <p>用于 WebSocket 广播到前端的数据结构。</p>
 *
 * @param content    待办事项内容（pending/completed 时显示）
 * @param activeForm 进行中形式（in_progress 时显示）
 * @param status     状态：pending / in_progress / completed
 * @param index      在列表中的索引
 */
public record TodoItemDto(
        String content,
        String activeForm,
        String status,
        int index
) {
}
