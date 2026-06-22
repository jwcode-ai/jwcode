package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class WorkflowCompiler {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public WorkflowIR fromJson(String json) {
        try {
            WorkflowIR ir = mapper.readValue(json, WorkflowIR.class);
            WorkflowValidator.validate(ir);
            return ir;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow IR JSON", e);
        }
    }

    public String toJson(WorkflowIR ir) {
        try {
            return mapper.writeValueAsString(ir);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize workflow IR", e);
        }
    }
}
