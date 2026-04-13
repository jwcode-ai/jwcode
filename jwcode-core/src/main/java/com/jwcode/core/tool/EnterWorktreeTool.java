package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.git.WorktreeInfo;
import com.jwcode.core.git.WorktreeManager;
import com.jwcode.core.git.WorktreeState;
import com.jwcode.core.git.WorktreeValidator;
import com.jwcode.core.service.GitService;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.EnterWorktreeInput;
import com.jwcode.core.tool.output.EnterWorktreeOutput;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EnterWorktreeTool - 进入 Git Worktree 工具
 * 
 * 功能说明：
 * 允许用户进入一个 Git Worktree，自动保存当前状态以便后续恢复。
 * 支持切换工作目录、更新 Git 上下文。
 * 
 * 使用场景：
 * - 需要同时处理多个分支时
 * - 需要在独立目录中工作时
 * - 避免频繁切换分支带来的上下文丢失
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class EnterWorktreeTool implements Tool<EnterWorktreeInput, EnterWorktreeOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(EnterWorktreeTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /** Worktree 状态存储的上下文键 */
    private static final String WORKTREE_STATE_KEY = "worktree.state";
    
    private final WorktreeManager worktreeManager;
    private final GitService gitService;
    
    public EnterWorktreeTool() {
        this.worktreeManager = new WorktreeManager();
        this.gitService = new GitService();
    }
    
    public EnterWorktreeTool(WorktreeManager worktreeManager, GitService gitService) {
        this.worktreeManager = worktreeManager;
        this.gitService = gitService;
    }
    
    @Override
    public String getName() {
        return "EnterWorktree";
    }
    
    @Override
    public String getDescription() {
        return "Enter a Git worktree and switch the working context to it";
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool to enter a Git worktree. This will switch your working directory " +
               "and Git context to the specified worktree. The original state will be saved " +
               "so you can return to it later using ExitWorktree.";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "Path to the worktree to enter"
                        },
                        "restoreBranch": {
                            "type": "boolean",
                            "description": "Whether to restore original branch when exiting",
                            "default": false
                        }
                    },
                    "required": ["path"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<EnterWorktreeInput> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<EnterWorktreeOutput> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<EnterWorktreeOutput>> call(
            EnterWorktreeInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error(validation.getFirstError());
                }
                
                String worktreePath = input.path().trim();
                
                // 验证 worktree 存在
                WorktreeValidator.ValidationResult existsValidation = 
                    WorktreeValidator.validateEnterWorktree(worktreePath, worktreeManager);
                if (!existsValidation.isValid()) {
                    return ToolResult.error(existsValidation.getMessage());
                }
                
                // 获取当前工作目录
                Path originalDir = context.getWorkingDirectory();
                String originalDirStr = originalDir.toString();
                
                // 获取当前分支
                String originalBranch = null;
                try {
                    originalBranch = gitService.getCurrentBranch().get();
                } catch (Exception e) {
                    logger.warn("Failed to get current branch: {}", e.getMessage());
                }
                
                // 获取目标 worktree 信息
                Optional<WorktreeInfo> worktreeOpt = worktreeManager.getCurrentWorktree().get();
                WorktreeInfo targetWorktree = null;
                
                Path targetPath = Paths.get(worktreePath).toAbsolutePath().normalize();
                for (WorktreeInfo wt : worktreeManager.listWorktreesSync()) {
                    if (wt.getPath().toAbsolutePath().normalize().equals(targetPath)) {
                        targetWorktree = wt;
                        break;
                    }
                }
                
                if (targetWorktree == null) {
                    return ToolResult.error("Worktree not found at path: " + worktreePath);
                }
                
                // 创建状态对象保存当前状态
                WorktreeState state = new WorktreeState(originalDir, originalBranch, targetPath);
                state.putSessionData("restoreBranch", input.restoreBranch());
                
                // 切换工作目录
                System.setProperty("user.dir", targetPath.toString());
                
                // 更新 GitService 的工作目录
                gitService.setWorkingDirectory(targetPath.toString());
                worktreeManager.setCurrentWorktreePath(targetPath);
                worktreeManager.setWorkingDirectory(targetPath.toString());
                
                // 保存状态到上下文
                context.putState(WORKTREE_STATE_KEY, state);
                
                logger.info("Entered worktree: {} from {}", targetPath, originalDir);
                
                // 构建成功响应
                String message = String.format(
                    "Successfully entered worktree at '%s' [%s]. Previous directory was '%s'.",
                    targetPath, targetWorktree.getBranchName(), originalDirStr
                );
                
                EnterWorktreeOutput output = EnterWorktreeOutput.success(
                    targetWorktree, message, originalDirStr
                );
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                logger.error("Failed to enter worktree: {}", e.getMessage(), e);
                return ToolResult.error("Failed to enter worktree: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(EnterWorktreeInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("Input cannot be null");
        }
        
        if (input.path() == null || input.path().trim().isEmpty()) {
            return ToolValidationResult.invalid("Worktree path is required");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(EnterWorktreeInput input) {
        // 进入 worktree 不是只读操作，因为它改变了工作目录
        return false;
    }
    
    @Override
    public boolean isDestructive(EnterWorktreeInput input) {
        return false;
    }
    
    @Override
    public boolean requiresApproval(EnterWorktreeInput input) {
        return false;
    }
    
    /**
     * 从上下文中获取保存的 Worktree 状态
     * 
     * @param context 工具执行上下文
     * @return Worktree 状态，如果没有则返回 null
     */
    public static WorktreeState getWorktreeState(ToolExecutionContext context) {
        return context.getState(WORKTREE_STATE_KEY);
    }
    
    /**
     * 检查当前是否在 worktree 中
     * 
     * @param context 工具执行上下文
     * @return true 如果在 worktree 中
     */
    public static boolean isInWorktree(ToolExecutionContext context) {
        return context.getState(WORKTREE_STATE_KEY) != null;
    }
    
    /**
     * 清除 worktree 状态
     * 
     * @param context 工具执行上下文
     */
    public static void clearWorktreeState(ToolExecutionContext context) {
        context.getState().remove(WORKTREE_STATE_KEY);
    }
}
