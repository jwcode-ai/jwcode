package com.jwcode.core.command;

import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.session.Session;

/** /config - manage application configuration (merged provider + key logic). */
public class ConfigCommand implements Command {
    @Override public String getName() { return "config"; }
    @Override public String getDescription() { return "Manage application configuration"; }
    @Override public String getUsage() { return "config [list|status|provider|get <key>|set <key> <value>]"; }
    @Override public String getCategory() { return "config"; }
    @Override public CommandSource getSource() { return CommandSource.CONFIG; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String action = args.length > 0 ? args[0] : "list";
        switch (action) {
            case "list":
            case "status":
                return showStatus();
            case "provider":
            case "providers":
                return showProviders();
            case "get":
                if (args.length < 2) return CommandResult.error("Please specify a config key.");
                String value = ConfigManager.getInstance().get(args[1]);
                if (value == null) return CommandResult.error("Config item not found: " + args[1]);
                return CommandResult.success(args[1] + " = " + value);
            case "set":
                if (args.length < 3) return CommandResult.error("Please specify a config key and value.");
                ConfigManager.getInstance().set(args[1], args[2]);
                return CommandResult.success("Config updated: " + args[1] + " = " + args[2]);
            case "delete":
                if (args.length < 2) return CommandResult.error("Please specify a config key.");
                ConfigManager.getInstance().delete(args[1]);
                return CommandResult.success("Config deleted: " + args[1]);
            default:
                return CommandResult.error("Unknown action: " + action + ". Use list, status, provider, get, set, or delete.");
        }
    }

    private CommandResult showStatus() {
        try {
            var loader = YamlConfigLoader.getInstance();
            boolean configured = loader.isProviderConfigured();
            var config = loader.getConfig();
            StringBuilder sb = new StringBuilder();
            sb.append("Configuration Status:\n");
            sb.append("  Configured: ").append(configured ? "yes" : "no (run setup wizard)").append("\n");
            sb.append("  Default provider: ")
                .append(config.getDefaultProviderName() != null ? config.getDefaultProviderName() : "(none)").append("\n");
            sb.append("  Providers: ");
            if (config.getProviders().isEmpty()) {
                sb.append("(none configured)\n");
            } else {
                sb.append("\n");
                for (var entry : config.getProviders().entrySet()) {
                    sb.append("    - ").append(entry.getKey());
                    sb.append(" (").append(entry.getValue().getModels().size()).append(" models");
                    boolean hasKey = entry.getValue().getApiKeys().stream()
                        .anyMatch(k -> k != null && !k.isBlank() && k.length() >= 20 && !k.contains("your-api-key"));
                    sb.append(", API key: ").append(hasKey ? "configured" : "missing").append(")\n");
                }
            }
            sb.append("  Models: ").append(config.getProviders().values().stream()
                .mapToInt(p -> (int) p.getModels().stream().filter(m -> m.isEnabled()).count()).sum())
                .append(" enabled\n");
            return CommandResult.success(sb.toString());
        } catch (Exception e) {
            return CommandResult.error("Config status failed: " + e.getMessage());
        }
    }

    private CommandResult showProviders() {
        try {
            var summary = YamlConfigLoader.getInstance().getProviderSummary();
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(summary);
            return CommandResult.success(json);
        } catch (Exception e) {
            return CommandResult.error("Provider query failed: " + e.getMessage());
        }
    }
}
