package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.git.WorktreeManager;
import com.jwcode.core.git.WorktreeState;
import com.jwcode.core.service.GitService;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.ExitWorktreeInput;
import com.jwcode.core.tool.output.ExitWorktreeOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * ExitWorktreeTool - 退出 Git Worktree 工具
 * 
 * 功能说明：
 * 退出当前的 Git Worktree，恢复到进入前的状态。
 * 支持恢复原始工作目录、可选恢复原始分支。
 * 
 * 使用场景：
 * - 完成 worktree 中的工作后返回主工作树
 * - 切换回之前的上下文
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ExitWorktreeTool implements Tool<ExitWorktreeInput, ExitWorktreeOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(ExitWorktreeTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /** Worktree 状态存储的上下文键 */
    private static final String WORKTREE_STATE_KEY = "worktree.state";
    
    private final WorktreeManager worktreeManager;
    private final GitService gitService;
    
    public ExitWorktreeTool() {
        this.worktreeManager = new WorktreeManager();
        this.gitService = new GitService();
    }
    
    public ExitWorktreeTool(WorktreeManager worktreeManager, GitService gitService) {
        this.worktreeManager = worktreeManager;
        this.gitService = gitService;
    }
    
    @Override
    public String getName() {
        return "ExitWorktree";
    }
    
    @Override
    public String getDescription() {
        return "Exit the current Git worktree and return to the previous working context";
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool to exit the current worktree and return to the previous " +
               "working directory. Optionally restore the original branch if it was " +
               "saved when entering the worktree.";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "restoreBranch": {
                            "type": "boolean",
                            "description": "Whether to restore the original branch after exiting",
                            "default": true
                        },
                        "force": {
                            "type": "boolean",
                            "description": "Force exit even if there are uncommitted changes",
                            "default": false
                        }
                    }
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<ExitWorktreeInput> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<ExitWorktreeOutput> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<ExitWorktreeOutput>> call(
            ExitWorktreeInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error(validation.getFirstError());
                }
                
                // 获取保存的 worktree 状态
                WorktreeState state = context.getState(WORKTREE_STATE_KEY);
                
                if (state == null) {
                    // 没有保存的状态，尝试返回到主工作树
                    logger.warn("No worktree state found in context, attempting to return to main worktree");
                    
                    // 尝试从 git 获取主工作树
                    Path mainWorktree = findMainWorktree();
                    if (mainWorktree != null) {
                        System.setProperty("user.dir", mainWorktree.toString());
                        gitService.setWorkingDirectory(mainWorktree.toString());
                        worktreeManager.setCurrentWorktreePath(mainWorktree);
                        
                        ExitWorktreeOutput output = ExitWorktreeOutput.success(
                            "Returned to main worktree (no previous state found)",
                            mainWorktree.toString(),
                            false,
                            null
                        );
                        return ToolResult.success(output);
                    }
                    
                    return ToolResult.error("No worktree state found. Are you currently in a worktree?");
                }
                
                // 确定是否恢复分支
                boolean shouldRestoreBranch = input.restoreBranch();
                Boolean savedRestoreFlag = state.getSessionData("restoreBranch");
                if (savedRestoreFlag != null) {
                    shouldRestoreBranch = shouldRestoreBranch && savedRestoreFlag;
                }
                
                Path originalDir = state.getOriginalDir();
                String originalBranch = state.getOriginalBranch();
                Path currentWorktreePath = state.getWorktreePath();
                
                // 恢复原始工作目录
                System.setProperty("user.dir", originalDir.toString());
                gitService.setWorkingDirectory(originalDir.toString());
                worktreeManager.setCurrentWorktreePath(originalDir);
                worktreeManager.setWorkingDirectory(originalDir.toString());
                
                boolean branchRestored = false;
                
                // 可选：恢复原始分支
                if (shouldRestoreBranch && originalBranch != null && !originalBranch.isEmpty()) {
                    try {
                        String currentBranch = gitService.getCurrentBranch().get();
                        if (!originalBranch.equals(currentBranch)) {
                            gitService.checkoutBranch(originalBranch).get();
                            branchRestored = true;
                            logger.info("Restored branch: {}", originalBranch);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to restore branch '{}': {}", originalBranch, e.getMessage());
                    }
                }
                
                // 清除 worktree 状态
                context.getState().remove(WORKTREE_STATE_KEY);
                
                logger.info("Exited worktree: {} -> {}", currentWorktreePath, originalDir);
                
                // 构建成功消息
                StringBuilder message = new StringBuilder();
                message.append(String.format("Successfully exited worktree '%s'.", currentWorktreePath));
                message.append(String.format(" Returned to '%s'.", originalDir));
                if (branchRestored) {
                    message.append(String.format(" Restored branch '%s'.", originalBranch));
                }
                
                ExitWorktreeOutput output = ExitWorktreeOutput.success(
                    message.toString(),
                    originalDir.toString(),
                    branchRestored,
                    originalBranch
                );
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                logger.error("Failed to exit worktree: {}", e.getMessage(), e);
                return ToolResult.error("Failed to exit worktree: " + e.getMessage());
            }
        });
    }
    
    /**
     * 尝试找到主工作树
     * 
     * @return 主工作树路径，如果找不到返回 null
     */
    private Path findMainWorktree() {
        try {
            for (var wt : worktreeManager.listWorktreesSync()) {
                if (wt.isMain()) {
                    return wt.getPath();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to find main worktree: {}", e.getMessage());
        }
        return null;
    }
    
    @Override
    public ToolValidationResult validate(ExitWorktreeInput input) {
        if (input == null) {
            // ExitWorktree 可以接受空输入，使用默认值
            return ToolValidationResult.valid();
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(ExitWorktreeInput input) {
        // 退出 worktree 不是只读操作，因为它改变了工作目录
        return false;
    }
    
    @Override
    public boolean isDestructive(ExitWorktreeInput input) {
        return false;
    }
    
    @Override
    public boolean requiresApproval(ExitWorktreeInput input) {
        return false;
    }
}
