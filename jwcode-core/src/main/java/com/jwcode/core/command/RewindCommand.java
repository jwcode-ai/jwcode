package com.jwcode.core.command;

import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;

import java.util.ArrayList;
import java.util.List;

/** /rewind - remove recent assistant+user message pairs from the session. */
public class RewindCommand implements Command {
    @Override public String getName() { return "rewind"; }
    @Override public String getDescription() { return "Rewind the session by removing recent messages"; }
    @Override public String getUsage() { return "rewind [steps]"; }
    @Override public String getCategory() { return "session"; }
    @Override public CommandSource getSource() { return CommandSource.SESSION; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        if (session == null || session.getMessages().isEmpty()) {
            return CommandResult.error("Nothing to rewind - session is empty.");
        }
        int steps = 1;
        if (args.length > 0) {
            try { steps = Integer.parseInt(args[0].trim()); } catch (NumberFormatException ignored) {}
        }
        if (steps < 1) steps = 1;
        List<Message> msgs = new ArrayList<>(session.getMessages());
        int removed = 0;
        for (int i = msgs.size() - 1; i >= 0 && removed < steps * 2; i--) {
            String role = msgs.get(i).getRole().name();
            if ("ASSISTANT".equalsIgnoreCase(role) || "USER".equalsIgnoreCase(role)) {
                msgs.remove(i);
                removed++;
            }
        }
        session.setMessages(msgs);
        session.markCompacted();
        return CommandResult.success("Rewound " + removed + " messages. " + msgs.size() + " remaining.");
    }
}
