package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.checkpoint.Checkpoint;
import com.jwcode.core.checkpoint.CheckpointManager;

import java.util.List;

/**
 * CheckpointCommand - 检查点管理
 * 
 * 功能：
 * - checkpoint list    列出所有检查点
 * - checkpoint create  创建检查点
 * - checkpoint revert  恢复到检查点
 * - checkpoint history 显示历史
 */
public class CheckpointCommand implements Command {
    
    @Override
    public String getName() {
        return "checkpoint";
    }
    
    @Override
    public String getDescription() {
        return "管理会话检查点（时间旅行）";
    }
    
    @Override
    public String getUsage() {
        return "checkpoint <list|create|revert|history> [args]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return CommandResult.error("请指定操作: list, create, revert, history");
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String action = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;
        
        // 获取或创建 CheckpointManager
        CheckpointManager manager = getCheckpointManager(context);
        
        switch (action) {
            case "list":
                return listCheckpoints(manager);
            case "create":
                return createCheckpoint(manager, context, arg);
            case "revert":
                return revertCheckpoint(manager, context, arg);
            case "history":
                return showHistory(manager);
            default:
                return CommandResult.error("未知操作: " + action);
        }
    }
    
    private CommandResult listCheckpoints(CheckpointManager manager) {
        List<Checkpoint> checkpoints = manager.getAllCheckpoints();
        
        if (checkpoints.isEmpty()) {
            return CommandResult.success("暂无检查点，使用 'checkpoint create' 创建");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== 检查点列表 ===\n\n");
        
        for (Checkpoint cp : checkpoints) {
            sb.append(String.format("[%d] %s\n", cp.getStepNumber(), cp.getDescription()));
            sb.append(String.format("    ID: %s\n", cp.getId()));
            sb.append(String.format("    时间: %s\n", cp.getTimestamp()));
            sb.append(String.format("    消息数: %d\n\n", cp.getMessages().size()));
        }
        
        sb.append("当前步骤: ").append(manager.getCurrentStep());
        
        return CommandResult.success(sb.toString());
    }
    
    private CommandResult createCheckpoint(CheckpointManager manager, CommandContext context, String description) {
        if (description == null || description.isEmpty()) {
            description = "手动创建的检查点";
        }
        
        // 简化实现，实际应从 context 获取 session
        Checkpoint checkpoint = manager.createCheckpoint(null, description);
        
        return CommandResult.success(
            String.format("检查点已创建 [%d]: %s\nID: %s", 
                checkpoint.getStepNumber(), 
                checkpoint.getDescription(),
                checkpoint.getId())
        );
    }
    
    private CommandResult revertCheckpoint(CheckpointManager manager, CommandContext context, String checkpointId) {
        if (checkpointId == null || checkpointId.isEmpty()) {
            return CommandResult.error("请指定检查点 ID，或使用 'checkpoint revert prev' 回到上一步");
        }
        
        if ("prev".equalsIgnoreCase(checkpointId) || "previous".equalsIgnoreCase(checkpointId)) {
            if (manager.revertToPrevious(null)) {
                return CommandResult.success("已恢复到上一步");
            } else {
                return CommandResult.error("没有可恢复的上一步");
            }
        }
        
        if (manager.revertTo(null, checkpointId)) {
            return CommandResult.success("已恢复到检查点: " + checkpointId);
        } else {
            return CommandResult.error("检查点不存在: " + checkpointId);
        }
    }
    
    private CommandResult showHistory(CheckpointManager manager) {
        return CommandResult.success(manager.getCheckpointHistory());
    }
    
    private CheckpointManager getCheckpointManager(CommandContext context) {
        // 简化实现，实际应从 context 获取
        return new CheckpointManager("default");
    }
}
