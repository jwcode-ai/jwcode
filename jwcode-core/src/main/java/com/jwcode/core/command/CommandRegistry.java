package com.jwcode.core.command;

import java.util.*;

/**
 * 命令注册表 - 管理所有可用命令
 */
public class CommandRegistry {
    
    private final Map<String, Command> commands = new HashMap<>();
    private final Map<String, String> aliasToCommand = new HashMap<>();
    
    /**
     * 注册命令
     */
    public void register(Command command) {
        commands.put(command.getName(), command);
        
        // 注册别名
        for (String alias : command.getAliases()) {
            aliasToCommand.put(alias, command.getName());
        }
    }
    
    /**
     * 批量注册命令
     */
    public void registerAll(List<Command> commands) {
        for (Command command : commands) {
            register(command);
        }
    }
    
    /**
     * 获取命令
     */
    public Command getCommand(String name) {
        // 先查找直接匹配的命令
        Command command = commands.get(name);
        if (command != null) {
            return command;
        }
        
        // 再查找别名
        String actualName = aliasToCommand.get(name);
        if (actualName != null) {
            return commands.get(actualName);
        }
        
        return null;
    }
    
    /**
     * 检查命令是否存在
     */
    public boolean hasCommand(String name) {
        return commands.containsKey(name) || aliasToCommand.containsKey(name);
    }
    
    /**
     * 获取所有命令
     */
    public Collection<Command> getAllCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }
    
    /**
     * 获取命令数量
     */
    public int getCommandCount() {
        return commands.size();
    }
    
    /**
     * 获取匹配的命令（用于自动补全）
     */
    public List<Command> getMatchingCommands(String prefix) {
        List<Command> matching = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase();
        
        for (Command command : commands.values()) {
            if (command.getName().toLowerCase().startsWith(lowerPrefix)) {
                matching.add(command);
            }
        }
        
        return matching;
    }
    
    /**
     * 创建默认注册表
     */
    public static CommandRegistry createDefault() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new HelpCommand(registry));
        registry.register(new ExitCommand());
        registry.register(new ClearCommand());
        registry.register(new ConfigCommand());
        registry.register(new StatusCommand());
        registry.register(new ModelCommand());
        registry.register(new EvalCommand());
        registry.register(new DoctorCommand());
        registry.register(new CostCommand());
        registry.register(new CompactCommand());
        registry.register(new ReviewCommand());
        registry.register(new SecurityReviewCommand());
        registry.register(new MemoryCommand());
        registry.register(new TasksCommand());
        return registry;
    }
}
