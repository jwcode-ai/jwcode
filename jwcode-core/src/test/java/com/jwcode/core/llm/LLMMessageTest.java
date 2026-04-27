package com.jwcode.core.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LLMMessageTest {

    @Test
    void toOpenAIFormat_alwaysIncludesReasoningContentForAssistant() {
        LLMMessage msg = LLMMessage.assistant("hello", null);
        Map<String, Object> map = msg.toOpenAIFormat();

        assertTrue(map.containsKey("reasoning_content"), "assistant message must contain reasoning_content key");
        assertEquals("", map.get("reasoning_content"), "null reasoningContent should be serialized as empty string");
    }

    @Test
    void toOpenAIFormat_preservesNonNullReasoningContent() {
        LLMMessage msg = LLMMessage.assistant("hello", "thinking...");
        Map<String, Object> map = msg.toOpenAIFormat();

        assertEquals("thinking...", map.get("reasoning_content"));
    }

    @Test
    void toOpenAIFormat_doesNotIncludeReasoningContentForUser() {
        LLMMessage msg = LLMMessage.user("hello");
        Map<String, Object> map = msg.toOpenAIFormat();

        assertFalse(map.containsKey("reasoning_content"), "user message should not contain reasoning_content");
    }
}
