package com.jwcode.core.command;

import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.session.Session;

/** /tokens - report token usage for the session. */
public class TokensCommand implements Command {
    @Override public String getName() { return "tokens"; }
    @Override public String getDescription() { return "Show token usage details"; }
    @Override public String getUsage() { return "tokens"; }
    @Override public String getCategory() { return "session"; }
    @Override public CommandSource getSource() { return CommandSource.SESSION; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        int msgCount = session != null ? session.getMessageCount() : 0;
        StringBuilder report = new StringBuilder();
        report.append("Token Usage:\n");
        report.append("  Messages in session: ").append(msgCount).append("\n");
        report.append("  Model: ").append(session != null ? session.getModel() : "unknown").append("\n");
        long promptTokens = session != null && session.getMetadata("total_prompt_tokens") instanceof Number n ? n.longValue() : 0;
        long completionTokens = session != null && session.getMetadata("total_completion_tokens") instanceof Number n ? n.longValue() : 0;
        long totalTokens = session != null && session.getMetadata("total_tokens") instanceof Number n ? n.longValue() : 0;
        report.append("  Prompt tokens:     ").append(promptTokens).append("\n");
        report.append("  Completion tokens: ").append(completionTokens).append("\n");
        report.append("  Total tokens:      ").append(totalTokens).append("\n");
        try {
            var modelDef = YamlConfigLoader.getInstance().getConfig().getDefaultModel();
            report.append("  Max tokens: ").append(modelDef != null ? modelDef.getMaxTokens() : "unknown").append("\n");
        } catch (Exception ignored) {}
        return CommandResult.success(report.toString());
    }
}
