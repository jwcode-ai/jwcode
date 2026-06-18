package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** /search - case-insensitive text search across the workspace. */
public class SearchCommand implements Command {
    private static final Set<String> IGNORE_DIRS = Set.of(
        ".git", "node_modules", "dist", "target", "build", "coverage", ".idea", ".vscode");
    private static final Set<String> TEXT_EXTS = Set.of(
        ".ts", ".tsx", ".js", ".jsx", ".json", ".md", ".txt", ".yaml", ".yml",
        ".css", ".html", ".java", ".kt", ".xml", ".mjs", ".cjs", ".sh", ".bat",
        ".ps1", ".toml", ".ini", ".env", ".gitignore");
    private static final long MAX_FILE_SIZE = 1_000_000L;
    private static final int CAP = 80;

    @Override public String getName() { return "search"; }
    @Override public String getDescription() { return "Search text in the workspace"; }
    @Override public String getUsage() { return "search <keyword>"; }
    @Override public String getCategory() { return "workspace"; }
    @Override public CommandSource getSource() { return CommandSource.WORKSPACE; }
    @Override public boolean requiresArgs() { return true; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        if (args.length == 0) {
            return CommandResult.error("Please specify a search keyword. Usage: search <keyword>");
        }
        String query = String.join(" ", args).trim();
        String lower = query.toLowerCase();
        String baseDir = session != null && session.getWorkingDirectory() != null
            ? session.getWorkingDirectory() : System.getProperty("user.dir");
        Path root = Paths.get(baseDir);
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                if (hits.size() >= CAP) return;
                if (!isProbablyText(p)) return;
                try {
                    if (Files.size(p) > MAX_FILE_SIZE) return;
                    List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size() && hits.size() < CAP; i++) {
                        if (lines.get(i).toLowerCase().contains(lower)) {
                            String rel = root.relativize(p).toString();
                            String line = lines.get(i).trim();
                            if (line.length() > 160) line = line.substring(0, 160);
                            hits.add(rel + ":" + (i + 1) + ": " + line);
                        }
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            return CommandResult.error("Search failed: " + e.getMessage());
        }
        if (hits.isEmpty()) {
            return CommandResult.success("No results for \"" + query + "\".");
        }
        return CommandResult.success("Search results for \"" + query + "\":\n" + String.join("\n", hits));
    }

    private boolean isProbablyText(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXT_EXTS.contains(name.substring(dot).toLowerCase());
    }
}
