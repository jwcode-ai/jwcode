package com.jwcode.core.observability;

import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMService;

/**
 * StepCallback 到 ObservationPipeline 的适配器 — 保证向后兼容。
 *
 * <p>现有的终端 UI、Web UI 等代码使用 {@link LLMQueryEngine#setStepCallback}，
 * 内部自动包装为此适配器并订阅到管道。</p>
 */
import java.util.HashSet;
import java.util.Set;

public class StepCallbackAdapter implements ObservationPipeline.Observer {

    private final LLMQueryEngine.StepCallback callback;
    private final Set<String> seenToolCallIds = new HashSet<>();

    public StepCallbackAdapter(LLMQueryEngine.StepCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onEvent(ObservationEvent event) {
        if (callback == null) {
            return;
        }
        if (event instanceof ObservationEvent.StepStart e) {
            callback.onStepStart(e.stepName(), e.description());
        } else if (event instanceof ObservationEvent.Thinking e) {
            callback.onStepThinking(e.stepName(), e.content());
        } else if (event instanceof ObservationEvent.StepComplete e) {
            callback.onStepComplete(e.stepName(), e.result());
        } else if (event instanceof ObservationEvent.ToolResult e) {
            callback.onToolResult(e.toolName(), e.result());
        } else if (event instanceof ObservationEvent.ContentChunk e) {
            callback.onContentChunk(e.chunk());
        } else if (event instanceof ObservationEvent.ThinkingChunk e) {
            callback.onThinkingChunk(e.chunk());
        } else if (event instanceof ObservationEvent.ToolCall e) {
            // 去重：同一个 toolCallId 的流式片段只触发一次 onStepAction
            String toolCallId = e.toolCallId();
            if (toolCallId != null && !toolCallId.isEmpty()) {
                if (!seenToolCallIds.add(toolCallId)) {
                    return; // 已处理过，跳过
                }
            }
            callback.onStepAction(e.toolName(), "执行 " + e.toolName());
        }
        // 其他事件类型 StepCallback 不支持，静默丢弃
    }

    @Override
    public String getObserverName() {
        return "StepCallbackAdapter(" + callback.getClass().getSimpleName() + ")";
    }
}
