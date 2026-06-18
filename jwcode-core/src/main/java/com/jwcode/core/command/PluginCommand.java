package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/** /plugin - plugin management (placeholder). */
public class PluginCommand implements Command {
    @Override public String getName() { return "plugin"; }
    @Override public String getDescription() { return "Plugin management"; }
    @Override public String getUsage() { return "plugin [list|install|remove]"; }
    @Override public String getCategory() { return "config"; }
    @Override public CommandSource getSource() { return CommandSource.CONFIG; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        return CommandResult.success(
            "Plugin management: use /plugin <install|list|remove>. Plugin system is under development.");
    }
}
