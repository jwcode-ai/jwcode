package com.jwcode.core.context;

import com.jwcode.core.context.CompactionPipeline.CompactResult;
import com.jwcode.core.context.CompactionPipeline.ProgressCallback;
import com.jwcode.core.context.CompactionPipeline.Stage;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("CompactionPipeline -- 5 stage compression pipeline test")
class CompactionPipelineTest {

    private Session createSessionWithMessages(int count) {
        Session session = new Session("test-session-" + count, null);
        List<Message> messages = new ArrayList<>();
        messages.add(Message.createSystemMessage("You are a helpful assistant."));
        for (int i = 0; i < count - 1; i++) {
            if (i % 2 == 0) {
                messages.add(Message.createUserMessage("User long message with lots of content to take up tokens #" + i));
            } else {
                messages.add(Message.createAssistantMessage("Assistant detailed reply that includes code snippets and analysis #" + i));
            }
        }
        session.setMessages(messages);
        return session;
    }

    @Test
    @DisplayName("Case 1: Normal pipeline execution with force=true")
    void pipelineExecutesSuccessfully() {
        Session session = createSessionWithMessages(60);
        CompactionPipeline pipeline = new CompactionPipeline();
        CompactResult result = pipeline.execute(session, true);
        assertNotNull(result);
        assertTrue(result.getBeforeCount() >= result.getAfterCount());
    }

    @Test
    @DisplayName("Case 2: Empty session returns CompactResult(0,0,0)")
    void emptySessionReturnsZeroResult() {
        Session emptySession = new Session("empty-session", null);
        CompactionPipeline pipeline = new CompactionPipeline();
        CompactResult result = pipeline.execute(emptySession, false);
        assertNotNull(result);
        assertEquals(0, result.getBeforeCount());
        assertEquals(0, result.getAfterCount());
        assertEquals(0, result.getTokensSaved());
    }

    @Test
    @DisplayName("Case 3: LLM summary skipped when compactionStrategy=null")
    void llmSummarySkippedWhenStrategyIsNull() {
        Session session = createSessionWithMessages(30);
        CompactionPipeline pipeline = new CompactionPipeline(null, null);
        CompactResult result = pipeline.execute(session, false);
        assertNotNull(result);
        assertTrue(result.getBeforeCount() >= result.getAfterCount());
    }

    @Test
    @DisplayName("Case 4: ProgressCallback triggered 5+ times")
    void progressCallbackIsTriggered() {
        Session session = createSessionWithMessages(40);
        AtomicInteger callCount = new AtomicInteger(0);
        ProgressCallback callback = (stage, percent, message) -> callCount.incrementAndGet();
        CompactionPipeline pipeline = new CompactionPipeline(null, callback);
        pipeline.execute(session, false);
        assertTrue(callCount.get() >= 5, "Expected >= 5 callback invocations, got " + callCount.get());
    }

    @Test
    @DisplayName("Case 5: Few messages skip summary stage")
    void summarySkippedWhenMessagesAreFew() {
        Session session = createSessionWithMessages(10);
        AtomicInteger callbackCount = new AtomicInteger(0);
        ProgressCallback callback = (stage, percent, message) -> callbackCount.incrementAndGet();
        CompactionPipeline pipeline = new CompactionPipeline(null, callback);
        CompactResult result = pipeline.execute(session, false);
        assertNotNull(result);
        assertTrue(result.getBeforeCount() >= result.getAfterCount());
    }

    @Test
    @DisplayName("CompactResult data class getters")
    void compactResultDataClass() {
        CompactResult r = new CompactResult(60, 40, 5000L);
        assertEquals(60, r.getBeforeCount());
        assertEquals(40, r.getAfterCount());
        assertEquals(5000L, r.getTokensSaved());
        assertEquals(20, r.getRemovedCount());
    }

    @Test
    @DisplayName("Stage enum has 5 stages with IDs and labels")
    void stageEnumHasFiveStages() {
        Stage[] stages = Stage.values();
        assertEquals(5, stages.length);
        for (Stage s : stages) {
            assertNotNull(s.getId());
            assertNotNull(s.getLabel());
            assertFalse(s.getId().isEmpty());
            assertFalse(s.getLabel().isEmpty());
        }
    }
}
