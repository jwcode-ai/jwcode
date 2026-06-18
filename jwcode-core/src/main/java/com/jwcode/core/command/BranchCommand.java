package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/** /branch - create a named branch session (requires backend orchestration). */
public class BranchCommand implements Command {
    @Override public String getName() { return "branch"; }
    @Override public String getDescription() { return "Create a branch conversation"; }
    @Override public String getUsage() { return "branch <name>"; }
    @Override public String getCategory() { return "session"; }
    @Override public CommandSource getSource() { return CommandSource.SESSION; }
    @Override public boolean requiresArgs() { return true; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String name = args.length > 0 ? args[0] : "branch";
        return CommandResult.error("Branch '" + name + "' requires backend session orchestration; " +
            "executed via the command_execute WebSocket path.");
    }
}
