package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

/** /checkpoint - create, list, or restore local checkpoints. */
public class CheckpointCommand implements Command {
    @Override public String getName() { return "checkpoint"; }
    @Override public String getDescription() { return "Create, list, or restore checkpoints"; }
    @Override public String getUsage() { return "checkpoint [list|restore <file>|name]"; }
    @Override public String getCategory() { return "session"; }
    @Override public CommandSource getSource() { return CommandSource.SESSION; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String baseDir = session != null && session.getWorkingDirectory() != null
            ? session.getWorkingDirectory() : System.getProperty("user.dir");
        Path dir = Paths.get(baseDir, ".jwcode", "checkpoints");
        String sub = args.length > 0 ? args[0] : "";

        if ("list".equals(sub)) {
            return CommandResult.success(listCheckpoints(dir));
        }
        if ("restore".equals(sub)) {
            if (args.length < 2) {
                return CommandResult.error("Usage: checkpoint restore <file>");
            }
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Path file = dir.resolve(name).normalize();
            if (!file.startsWith(dir) || !Files.exists(file)) {
                return CommandResult.error("Checkpoint not found: " + name);
            }
            try {
                return CommandResult.success(Files.readString(file));
            } catch (IOException e) {
                return CommandResult.error("Failed to read checkpoint: " + e.getMessage());
            }
        }
        if (session == null) {
            return CommandResult.error("No active session to checkpoint.");
        }
        try {
            Files.createDirectories(dir);
            String suffix = sub.isEmpty() ? "manual" : sub.replaceAll("[^\\w.-]+", "-");
            String ts = Instant.now().toString().replace(":", "-").replace(".", "-");
            Path file = dir.resolve(ts + "-" + suffix + ".md");
            Files.writeString(file, exportText(session), StandardCharsets.UTF_8);
            return CommandResult.success("Local checkpoint saved to " + file + ".");
        } catch (Exception e) {
            return CommandResult.error("Checkpoint failed: " + e.getMessage());
        }
    }

    private String listCheckpoints(Path dir) {
        if (!Files.exists(dir)) {
            return "No local checkpoints found.";
        }
        StringBuilder sb = new StringBuilder("Local checkpoints:\n");
        try (Stream<Path> s = Files.list(dir)) {
            var files = s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().toList();
            if (files.isEmpty()) {
                return "No local checkpoints found.";
            }
            for (String f : files) {
                sb.append("- ").append(f).append("\n");
            }
        } catch (IOException e) {
            return "Failed to list checkpoints: " + e.getMessage();
        }
        return sb.toString();
    }

    private String exportText(Session session) {
        StringBuilder sb = new StringBuilder();
        for (var msg : session.getMessages()) {
            sb.append("## ").append(msg.getRole().name()).append("\n\n");
            sb.append(msg.getTextContent() != null ? msg.getTextContent() : "(empty)").append("\n\n");
        }
        return sb.toString();
    }
}
