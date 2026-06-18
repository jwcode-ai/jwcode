package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.util.List;

/** /project (alias /update_docs) - generate or update project documentation (AI prompt marker). */
public class ProjectCommand implements Command {
    @Override public String getName() { return "project"; }
    @Override public List<String> getAliases() { return List.of("update_docs"); }
    @Override public String getDescription() { return "Generate or update project documentation"; }
    @Override public String getUsage() { return "project"; }
    @Override public String getCategory() { return "workspace"; }
    @Override public CommandSource getSource() { return CommandSource.WORKSPACE; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String prompt = "Analyze the current project structure, tech stack, build system, " +
            "coding conventions, and key files. Generate or update project documentation covering:\n" +
            "1. Project overview and architecture\n" +
            "2. Build and run instructions\n" +
            "3. Key directories and their purposes\n" +
            "4. Technology stack\n" +
            "5. Development conventions\n" +
            "Use Read, Glob, Grep tools to understand the project first.";
        return CommandResult.success(prompt, "AI_PROMPT");
    }
}
