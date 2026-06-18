package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** /memory - browse project memory files (JWCODE.md and .jwcode/). */
public class MemoryCommand implements Command {
    @Override public String getName() { return "memory"; }
    @Override public String getDescription() { return "Browse project memory files"; }
    @Override public String getUsage() { return "memory [file]"; }
    @Override public String getCategory() { return "workspace"; }
    @Override public CommandSource getSource() { return CommandSource.WORKSPACE; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String baseDir = session != null && session.getWorkingDirectory() != null
            ? session.getWorkingDirectory() : System.getProperty("user.dir");
        Path root = Paths.get(baseDir);
        if (args.length > 0 && !args[0].isEmpty()) {
            Path target = root.resolve(args[0]).normalize();
            if (!target.startsWith(root) || !Files.exists(target) || Files.isDirectory(target)) {
                return CommandResult.error("Memory file not found: " + args[0]);
            }
            try {
                String content = Files.readString(target);
                return CommandResult.success(content.length() > 8000 ? content.substring(0, 8000) : content);
            } catch (IOException e) {
                return CommandResult.error("Failed to read memory file: " + e.getMessage());
            }
        }
        StringBuilder sb = new StringBuilder("Project memory\n");
        Path jwcodeMd = root.resolve("JWCODE.md");
        if (Files.exists(jwcodeMd) && Files.isRegularFile(jwcodeMd)) {
            sb.append("- ").append(jwcodeMd).append("\n");
        }
        Path dot = root.resolve(".jwcode");
        if (Files.exists(dot) && Files.isDirectory(dot)) {
            try (Stream<Path> walk = Files.walk(dot)) {
                walk.filter(Files::isRegularFile).limit(40).forEach(p -> sb.append("- ").append(p).append("\n"));
            } catch (IOException e) {
                return CommandResult.error("Failed to scan memory: " + e.getMessage());
            }
        }
        if (sb.length() <= "Project memory\n".length()) {
            return CommandResult.success("No project memory files found.");
        }
        return CommandResult.success(sb.toString());
    }
}
