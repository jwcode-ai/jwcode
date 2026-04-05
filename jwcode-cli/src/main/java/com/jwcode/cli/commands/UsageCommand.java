package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

/**
 * UsageCommand - 使用统计命令
 */
public class UsageCommand implements Command {
    
    @Override
    public String getName() { return "usage"; }
    
    @Override
    public String getDescription() { return "查看 API 使用统计"; }
    
    @Override
    public String getUsage() { return "/usage"; }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    API 使用统计                            ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        
        sb.append("【今日使用】\n");
        sb.append("请求次数: 0\n");
        sb.append("输入令牌: 0\n");
        sb.append("输出令牌: 0\n");
        sb.append("估计费用: $0.00\n\n");
        
        sb.append("【本月使用】\n");
        sb.append("请求次数: 0\n");
        sb.append("输入令牌: 0\n");
        sb.append("输出令牌: 0\n");
        sb.append("估计费用: $0.00\n\n");
        
        sb.append("提示: 使用 '/cost' 查看详细成本分析\n");
        
        return CommandResult.success(sb.toString());
    }
}
