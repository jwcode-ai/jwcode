package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/** /effort - set the reasoning effort level on the session. */
public class EffortCommand implements Command {
    @Override public String getName() { return "effort"; }
    @Override public String getDescription() { return "Set reasoning effort level"; }
    @Override public String getUsage() { return "effort low|medium|high"; }
    @Override public String getCategory() { return "config"; }
    @Override public CommandSource getSource() { return CommandSource.CONFIG; }
    @Override public boolean requiresArgs() { return true; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        if (args.length == 0) {
            return CommandResult.error("Please specify effort level: low, medium, or high.");
        }
        String level = args[0].toLowerCase().trim();
        if (!level.equals("low") && !level.equals("medium") && !level.equals("high")) {
            return CommandResult.error("effort must be low, medium, or high, got: " + level);
        }
        if (session != null) {
            session.setMetadata("effort", level);
        }
        return CommandResult.success("Effort level set to: " + level);
    }
}
