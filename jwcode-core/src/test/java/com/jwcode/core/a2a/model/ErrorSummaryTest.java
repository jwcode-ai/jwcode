package com.jwcode.core.a2a.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ErrorSummaryTest {

    @Test void toolAgentFailure_createsMinimalSummary() {
        ErrorSummary es = ErrorSummary.toolAgentFailure("Permission denied", true, 2, 3);
        assertEquals("TOOL_AGENT_ERROR", es.getErrorType());
        assertTrue(es.isRetryable());
        assertEquals(2, es.getRetryCount());
        assertEquals("TOOL_AGENT", es.getSourceLayer());
        assertFalse(es.isCriticalPath());
        assertNull(es.getRecoveryHint());
    }

    @Test void domainAgentFailure_createsCorrectLayer() {
        ErrorSummary es = ErrorSummary.domainAgentFailure("Module not found", "Install dependency");
        assertEquals("DOMAIN_AGENT_ERROR", es.getErrorType());
        assertEquals("DOMAIN_AGENT", es.getSourceLayer());
        assertEquals("Install dependency", es.getRecoveryHint());
        assertEquals(0, es.getRetryCount());
    }

    @Test void criticalFailure_marksCritical() {
        ErrorSummary es = ErrorSummary.criticalFailure("Disk full", true);
        assertEquals("CRITICAL_ERROR", es.getErrorType());
        assertTrue(es.isCriticalPath());
        assertTrue(es.isRequiresHumanIntervention());
    }

    @Test void toBusinessSummary_formatsCorrectly() {
        ErrorSummary es = ErrorSummary.toolAgentFailure("File not found", false, 0, 0);
        assertEquals("[TOOL_AGENT_ERROR] File not found", es.toBusinessSummary());
    }

    @Test void builder_buildsCompleteSummary() {
        ErrorSummary es = ErrorSummary.builder()
            .errorType("VALIDATION_ERROR").message("Invalid input")
            .retryable(false).sourceLayer("DOMAIN_AGENT")
            .criticalPath(true).requiresHumanIntervention(true).build();
        assertEquals("VALIDATION_ERROR", es.getErrorType());
        assertTrue(es.isCriticalPath());
        assertTrue(es.isRequiresHumanIntervention());
    }

    @Test void constructor_throwsOnNullMandatoryFields() {
        assertThrows(NullPointerException.class, () ->
            ErrorSummary.builder().message("test").sourceLayer("TOOL").build());
    }
}