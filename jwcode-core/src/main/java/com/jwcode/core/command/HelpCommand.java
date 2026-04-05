package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.util.Collection;

/**
 * 帮助命令
 */
public class HelpCommand implements Command {
    
    private final CommandRegistry registry;
    
    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public String getDescription() {
        return "显示帮助信息";
    }
    
    @Override
    public String getUsage() {
        return "help [command]";
    }
    
    @Override
    public CommandResult execute(String[] args, Session session) {
        if (args.length > 0) {
            // 显示特定命令的帮助
            String commandName = args[0];
            Command command = registry.getCommand(commandName);
            
            if (command == null) {
                return CommandResult.error("未知命令: " + commandName);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("命令: ").append(command.getName()).append("\n");
            sb.append("描述: ").append(command.getDescription()).append("\n");
            sb.append("用法: ").append(command.getUsage()).append("\n");
            
            if (!command.getAliases().isEmpty()) {
                sb.append("别名: ").append(String.join(", ", command.getAliases())).append("\n");
            }
            
            return CommandResult.success(sb.toString());
        }
        
        // 显示所有命令
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                     JwCode 可用命令                          ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        
        Collection<Command> commands = registry.getAllCommands();
        int maxNameLength = commands.stream()
            .mapToInt(c -> c.getName().length())
            .max()
            .orElse(0);
        
        for (Command command : commands) {
            sb.append(String.format("  %%-%ds  %%s\n", maxNameLength + 2, 
                command.getName(), 
                command.getDescription()));
        }
        
        sb.append("\n使用 'help <command>' 查看详细帮助\n");
        
        return CommandResult.success(sb.toString());
    }
}
