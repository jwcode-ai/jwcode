package com.jwcode.core.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class EffectId {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private EffectId() {
    }

    public static String explicit(String runId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Workflow node id is required for stable effect ids");
        }
        return nullToEmpty(runId) + ":" + nodeId;
    }

    public static String create(
        String runId,
        String nodeId,
        String kind,
        JsonNode normalizedInput,
        List<String> allowedTools,
        String schemaVersion
    ) {
        try {
            String payload = MAPPER.writeValueAsString(Map.of(
                "runId", nullToEmpty(runId),
                "nodeId", nullToEmpty(nodeId),
                "kind", nullToEmpty(kind),
                "input", normalizedInput == null ? "" : MAPPER.writeValueAsString(normalizedInput),
                "allowedTools", allowedTools == null ? List.of() : allowedTools.stream().sorted().toList(),
                "schemaVersion", nullToEmpty(schemaVersion)));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to create effect id", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
