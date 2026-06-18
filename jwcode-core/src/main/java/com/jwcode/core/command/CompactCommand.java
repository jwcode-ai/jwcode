package com.jwcode.core.command;

import com.jwcode.core.model.Message;
import com.jwcode.core.service.ContextWindowManager;
import com.jwcode.core.session.Session;

import java.util.List;

/** /compact - compress the conversation context window. */
public class CompactCommand implements Command {
    @Override public String getName() { return "compact"; }
    @Override public String getDescription() { return "Compress conversation context"; }
    @Override public String getUsage() { return "compact [normal|aggressive|summary]"; }
    @Override public String getCategory() { return "session"; }
    @Override public CommandSource getSource() { return CommandSource.SESSION; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        if (session == null || session.getMessages().isEmpty()) {
            return CommandResult.error("No need to compact: session is empty.");
        }
        String strategy = args.length > 0 ? args[0].trim() : "normal";
        ContextWindowManager windowManager;
        switch (strategy) {
            case "aggressive" -> windowManager = new ContextWindowManager(
                ContextWindowManager.DEFAULT_CONTEXT_LIMIT, 10, 2);
            case "summary" -> windowManager = new ContextWindowManager(
                ContextWindowManager.DEFAULT_CONTEXT_LIMIT, 20, 4);
            default -> {
                strategy = "normal";
                windowManager = new ContextWindowManager(
                    ContextWindowManager.DEFAULT_CONTEXT_LIMIT, 30, 4);
            }
        }
        int before = session.getMessageCount();
        List<Message> compacted = windowManager.prepareMessages(
            session.getMessages(), "aggressive".equals(strategy));
        session.setMessages(compacted);
        int after = compacted != null ? compacted.size() : before;
        if (compacted != null && after < before) {
            session.markCompacted();
            return CommandResult.success("Compacted (" + strategy + "): " + before + " -> " + after + " messages.");
        }
        return CommandResult.success("No compaction needed: message count within safe range.");
    }
}
