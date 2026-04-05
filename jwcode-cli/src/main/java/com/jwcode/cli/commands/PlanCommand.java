package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

/**
 * PlanCommand - 计划模式命令
 */
public class PlanCommand implements Command {
    
    @Override
    public String getName() { return "plan"; }
    
    @Override
    public String getDescription() { return "进入计划模式，制定多步骤任务"; }
    
    @Override
    public String getUsage() { return "/plan [任务描述]"; }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                      计划模式                              ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        
        if (args == null || args.trim().isEmpty()) {
            sb.append("请输入要计划的任务，例如:\n");
            sb.append("  /plan 实现一个用户登录功能\n");
            return CommandResult.success(sb.toString());
        }
        
        sb.append("任务: ").append(args.trim()).append("\n\n");
        sb.append("计划步骤:\n");
        sb.append("1. 分析需求\n");
        sb.append("2. 设计架构\n");
        sb.append("3. 编写代码\n");
        sb.append("4. 测试验证\n\n");
        sb.append("提示: 使用 '/todo' 命令创建具体待办事项\n");
        
        return CommandResult.success(sb.toString());
    }
}
