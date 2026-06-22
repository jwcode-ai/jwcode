package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorkflowArtifactStore {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path runDirectory;
    private final Path artifactsDirectory;

    public WorkflowArtifactStore(Path runDirectory) {
        this.runDirectory = runDirectory;
        this.artifactsDirectory = runDirectory.resolve("artifacts");
        try {
            Files.createDirectories(artifactsDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create workflow artifact directory: " + artifactsDirectory, e);
        }
    }

    public String writeJson(String effectId, JsonNode output) {
        String fileName = safeName(effectId) + ".json";
        Path file = artifactsDirectory.resolve(fileName);
        try {
            MAPPER.writeValue(file.toFile(), output);
            return "artifacts/" + fileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write workflow artifact: " + file, e);
        }
    }

    public JsonNode readJson(String artifactRef) {
        Path file = runDirectory.resolve(artifactRef).normalize();
        if (!file.startsWith(runDirectory.normalize())) {
            throw new IllegalArgumentException("Artifact path escapes workflow run directory: " + artifactRef);
        }
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read workflow artifact: " + file, e);
        }
    }

    private static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
