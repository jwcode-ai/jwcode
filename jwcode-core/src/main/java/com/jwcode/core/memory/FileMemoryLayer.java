package com.jwcode.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.model.Message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileMemoryLayer implements MemoryLayer {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(FileMemoryLayer.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path root;

    public FileMemoryLayer(Path root) {
        this.root = root == null ? Path.of(System.getProperty("user.home"), ".jwcode") : root;
    }

    @Override
    public void writeCheckpoint(String sessionId, Checkpoint checkpoint) {
        Path file = checkpointFile(sessionId);
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writeValue(file.toFile(), checkpoint == null ? Checkpoint.empty() : checkpoint);
            sessionMemory(sessionId).write(formatCheckpointAsNotes(checkpoint == null ? Checkpoint.empty() : checkpoint));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write session checkpoint: " + file, e);
        }
    }

    @Override
    public Checkpoint readCheckpoint(String sessionId) {
        Path file = checkpointFile(sessionId);
        if (!Files.exists(file)) {
            return Checkpoint.empty();
        }
        try {
            return MAPPER.readValue(file.toFile(), Checkpoint.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read session checkpoint: " + file, e);
        }
    }

    @Override
    public void writeProjectMemory(String projectId, String key, String value) {
        Path file = projectMemoryFile(projectId, key);
        try {
            Files.createDirectories(file.getParent());
            ExtractMemoriesService.MemoryEntry entry = new ExtractMemoriesService.MemoryEntry();
            entry.name = safeName(key);
            entry.description = "project memory: " + key;
            entry.type = ExtractMemoriesService.MemoryType.PROJECT;
            entry.body = value == null ? "" : value;
            entry.why = "Stored by MemoryLayer";
            entry.howToApply = "Inject when rebuilding workflow context for this project";
            Files.writeString(file, entry.toMarkdown(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write project memory: " + file, e);
        }
    }

    @Override
    public String readProjectMemory(String projectId, String key) {
        Path file = projectMemoryFile(projectId, key);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return ExtractMemoriesService.MemoryEntry.parse(file, Files.readString(file, StandardCharsets.UTF_8)).body;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read project memory: " + file, e);
        }
    }

    @Override
    public List<Message> rebuildContext(String sessionId, List<Message> currentWindow) {
        Checkpoint checkpoint = readCheckpoint(sessionId);
        List<Message> rebuilt = new ArrayList<>();
        rebuilt.add(Message.createSystemMessage("Task checklist:\n" + formatTaskTree(checkpoint.taskTree(), 0)));
        rebuilt.add(Message.createSystemMessage("Session checkpoint:\n" + formatCheckpoint(checkpoint)));
        rebuilt.addAll(recentUserMessages(currentWindow));
        rebuilt.add(Message.createSystemMessage("Project Memory:\n" + readAllProjectMemory()));
        rebuilt.add(Message.createSystemMessage("notes:\n" + sessionMemory(sessionId).readForCompact().content()));
        rebuilt.add(Message.createSystemMessage("File index:\n" + String.join("\n", checkpoint.involvedFiles())));
        rebuilt.add(Message.createSystemMessage("Tail reminder: continue from the checkpoint, preserve constraints, and do not repeat completed effects."));
        return rebuilt;
    }

    private SessionMemoryService sessionMemory(String sessionId) {
        return new SessionMemoryService(root.resolve("sessions").resolve(safeName(sessionId)).resolve("session-memory.md"));
    }

    private Path checkpointFile(String sessionId) {
        return root.resolve("sessions").resolve(safeName(sessionId)).resolve("checkpoint.json");
    }

    private Path projectMemoryFile(String projectId, String key) {
        return root.resolve("memory").resolve("projects").resolve(safeName(projectId)).resolve(safeName(key) + ".md");
    }

    private String readAllProjectMemory() {
        Path projects = root.resolve("memory").resolve("projects");
        if (!Files.exists(projects)) {
            return "";
        }
        try (var stream = Files.walk(projects)) {
            StringBuilder out = new StringBuilder();
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> {
                    try {
                        ExtractMemoriesService.MemoryEntry entry =
                            ExtractMemoriesService.MemoryEntry.parse(path, Files.readString(path, StandardCharsets.UTF_8));
                        out.append("## ").append(entry.name).append("\n");
                        if (entry.description != null) out.append(entry.description).append("\n");
                        if (entry.body != null) out.append(entry.body).append("\n");
                    } catch (IOException e) {
                        LOGGER.finest("Cannot read memory file: " + path + " — " + e.getMessage());
                    }
                });
            return out.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private List<Message> recentUserMessages(List<Message> currentWindow) {
        if (currentWindow == null || currentWindow.isEmpty()) {
            return List.of();
        }
        List<Message> users = currentWindow.stream()
            .filter(message -> message.getRole() == Message.Role.USER)
            .toList();
        return users.subList(Math.max(0, users.size() - 3), users.size());
    }

    private static String formatCheckpointAsNotes(Checkpoint checkpoint) {
        return """
            # Session Title
            Workflow session checkpoint

            # Current State
            %s
            Next: %s

            # Task specification
            %s

            # Files and Functions
            %s

            # Workflow
            %s

            # Errors & Fixes
            %s

            # Codebase and System Documentation
            %s

            # Learnings
            %s

            # Key results
            %s

            # Worklog
            %s
            """.formatted(
            checkpoint.currentWork(),
            checkpoint.nextAction(),
            checkpoint.intent(),
            String.join("\n", checkpoint.involvedFiles()),
            checkpoint.runtimeState(),
            checkpoint.errorsAndFixes(),
            checkpoint.designDecisions(),
            checkpoint.crossTaskFindings(),
            checkpoint.constraints(),
            checkpoint.miscNotes());
    }

    private static String formatCheckpoint(Checkpoint checkpoint) {
        try {
            return MAPPER.writeValueAsString(checkpoint);
        } catch (Exception e) {
            return String.valueOf(checkpoint);
        }
    }

    private static String formatTaskTree(List<TaskNode> nodes, int depth) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        String indent = "  ".repeat(Math.max(0, depth));
        for (TaskNode node : nodes) {
            out.append(indent)
                .append("- [").append(node.status() == null ? "unknown" : node.status()).append("] ")
                .append(node.title() == null ? node.id() : node.title())
                .append("\n");
            out.append(formatTaskTree(node.children(), depth + 1));
        }
        return out.toString();
    }

    private static String safeName(String value) {
        String raw = value == null || value.isBlank() ? "default" : value;
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
