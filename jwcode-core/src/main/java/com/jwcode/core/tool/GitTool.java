package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class GitTool implements Tool<GitTool.Input, GitTool.Output, GitTool.Progress> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(GitTool.class.getName());
    private static final int DEFAULT_TIMEOUT_MS = 60000;

    @Override public String getName() { return "GitTool"; }
    @Override public String getDescription() { return "Execute git commands for version control operations."; }

    @Override
    public String getPrompt() {
        return """
            Use GitTool to run git commands.
            Parameters:
            - command: git subcommand (e.g. "status", "log --oneline -5", "diff")
            - args: additional arguments (optional, appended to command)
            - cwd: working directory (optional, defaults to workspace root)
            Examples:
            - {"command": "status"}
            - {"command": "log", "args": "--oneline -10"}
            """;
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "command": {"type": "string", "description": "git subcommand"},
                        "args": {"type": "string", "description": "additional arguments"},
                        "cwd": {"type": "string", "description": "working directory"},
                        "timeout": {"type": "integer", "description": "timeout in ms", "default": 60000}
                    },
                    "required": ["command"]
                }
                """);
        } catch (Exception e) { return null; }
    }

    @Override
    public TypeReference<Input> getInputType() { return new TypeReference<Input>() {}; }
    @Override
    public TypeReference<Output> getOutputType() { return new TypeReference<Output>() {}; }

    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args, ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (args.command == null || args.command.trim().isEmpty()) {
                    return ToolResult.error("command is required");
                }

                List<String> cmdParts = new ArrayList<>();
                cmdParts.add("git");
                cmdParts.add(args.command.trim());
                if (args.args != null && !args.args.trim().isEmpty()) {
                    cmdParts.add(args.args.trim());
                }

                Path workDir;
                if (args.cwd != null && !args.cwd.isEmpty()) {
                    workDir = Paths.get(args.cwd);
                } else if (context != null && context.getWorkingDirectory() != null) {
                    workDir = context.getWorkingDirectory();
                } else {
                    workDir = Paths.get(System.getProperty("user.dir"));
                }

                int timeout = args.timeout != null ? args.timeout : DEFAULT_TIMEOUT_MS;

                ProcessBuilder pb = new ProcessBuilder(cmdParts);
                pb.directory(workDir.toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                StringBuilder outputBuilder = new StringBuilder();
                boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    return ToolResult.error("git command timed out after " + timeout + "ms");
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append("\n");
                    }
                }

                int exitCode = process.exitValue();
                String output = outputBuilder.toString().trim();

                Output result = new Output();
                result.success = exitCode == 0;
                result.exitCode = exitCode;
                result.output = output;
                result.command = String.join(" ", cmdParts);

                return ToolResult.success(result);

            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("CreateProcess") && msg.contains("git")) {
                    return ToolResult.error("Git is not installed or not in PATH. Please install Git from https://git-scm.com/");
                }
                return ToolResult.error("Git execution error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.error("Git command was interrupted");
            } catch (Exception e) {
                return ToolResult.error("Git error: " + e.getMessage());
            }
        });
    }

    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null || input.command == null || input.command.trim().isEmpty()) {
            return ToolValidationResult.invalid("command is required");
        }
        return ToolValidationResult.valid();
    }

    public static class Input { public String command; public String args; public String cwd; public Integer timeout; }
    public static class Output { public boolean success; public int exitCode; public String output; public String command; }
    public static class Progress { public String status; public Progress() {} public Progress(String s) { status = s; } }
}
