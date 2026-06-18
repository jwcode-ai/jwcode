package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/** /init - analyze the project and generate a JWCODE.md file (AI prompt marker). */
public class InitCommand implements Command {
    @Override public String getName() { return "init"; }
    @Override public String getDescription() { return "Analyze project and generate JWCODE.md"; }
    @Override public String getUsage() { return "init"; }
    @Override public String getCategory() { return "workspace"; }
    @Override public CommandSource getSource() { return CommandSource.WORKSPACE; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String prompt = "[System /init] Analyze the current project structure, tech stack, build system, " +
            "coding conventions, and key files. Generate a JWCODE.md file in the project root containing:\n" +
            "1. Project overview and tech stack\n" +
            "2. Build and run commands\n" +
            "3. Directory structure and key paths\n" +
            "4. Coding conventions and standards\n" +
            "5. Architecture design highlights\n" +
            "Use Read, Glob, Grep tools to fully understand the project before generating the file.";
        return CommandResult.success(prompt, "AI_PROMPT");
    }
}
