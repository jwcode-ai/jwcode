package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EffectIdTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void explicitNodeIdIgnoresDynamicInput() {
        assertEquals(
            EffectId.explicit("run", "e1-explore-deps"),
            EffectId.explicit("run", "e1-explore-deps"));
        assertNotEquals(
            EffectId.explicit("run", "e1-explore-deps"),
            EffectId.explicit("run", "e2-explore-deps"));
    }

    @Test
    void sameInputProducesSameId() {
        String first = EffectId.create("run", "node", "agent", mapper.createObjectNode().put("a", 1), List.of("b", "a"), "v1");
        String second = EffectId.create("run", "node", "agent", mapper.createObjectNode().put("a", 1), List.of("a", "b"), "v1");
        assertEquals(first, second);
    }

    @Test
    void inputToolsAndSchemaAffectId() {
        String baseline = EffectId.create("run", "node", "agent", mapper.createObjectNode().put("a", 1), List.of("read"), "v1");
        assertNotEquals(baseline, EffectId.create("run", "node", "agent", mapper.createObjectNode().put("a", 2), List.of("read"), "v1"));
        assertNotEquals(baseline, EffectId.create("run", "node", "agent", mapper.createObjectNode().put("a", 1), List.of("write"), "v1"));
        assertNotEquals(baseline, EffectId.create("run", "node", "agent", mapper.createObjectNode().put("a", 1), List.of("read"), "v2"));
    }
}
