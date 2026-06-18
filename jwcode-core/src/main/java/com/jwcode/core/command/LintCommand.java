package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/** /lint - run the project lint script via npm. */
public class LintCommand implements Command {
    @Override public String getName() { return "lint"; }
    @Override public String getDescription() { return "Run the project lint script"; }
    @Override public String getUsage() { return "lint [extra args]"; }
    @Override public String getCategory() { return "tools"; }
    @Override public CommandSource getSource() { return CommandSource.TOOLS; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        return TestCommand.runPackageScript("lint", args, session);
    }
}
