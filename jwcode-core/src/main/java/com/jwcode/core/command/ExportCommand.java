package com.jwcode.core.command;

import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/** /export - export the conversation to a Markdown file. */
public class ExportCommand implements Command {
    @Override public String getName() { return "export"; }
    @Override public String getDescription() { return "Export conversation to a Markdown file"; }
    @Override public String getUsage() { return "export <path>"; }
    @Override public String getCategory() { return "session"; }
    @Override public CommandSource getSource() { return CommandSource.SESSION; }
    @Override public boolean requiresArgs() { return true; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        if (session == null) {
            return CommandResult.error("No active session to export.");
        }
        if (args.length == 0) {
            return CommandResult.error("Please specify a target path. Usage: export <path>");
        }
        String baseDir = session.getWorkingDirectory() != null ? session.getWorkingDirectory() : System.getProperty("user.dir");
        Path target = Paths.get(baseDir).resolve(args[0]).normalize();
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, conversationMarkdown(session), StandardCharsets.UTF_8);
            return CommandResult.success("Conversation exported to " + target + ".");
        } catch (Exception e) {
            return CommandResult.error("Export failed: " + e.getMessage());
        }
    }

    private String conversationMarkdown(Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("# JWCode conversation export\n\n");
        sb.append("Exported: ").append(Instant.now().toString()).append("\n\n");
        for (Message msg : session.getMessages()) {
            sb.append("## ").append(msg.getRole().name()).append("\n\n");
            String reasoning = msg.getReasoningContent();
            if (reasoning != null && !reasoning.isEmpty()) {
                sb.append("### Thinking\n\n").append(reasoning).append("\n\n");
            }
            if (msg.hasToolCalls()) {
                sb.append("### Tool calls\n\n");
                for (var tc : msg.getToolCalls()) {
                    sb.append("- ").append(tc.getName());
                    if (tc.getArguments() != null && !tc.getArguments().isEmpty()) {
                        sb.append(" (").append(tc.getArguments()).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            sb.append(msg.getTextContent() != null ? msg.getTextContent() : "(empty)").append("\n\n");
        }
        return sb.toString();
    }
}
