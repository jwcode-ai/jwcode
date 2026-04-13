package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.git.WorktreeInfo;
import com.jwcode.core.git.WorktreeManager;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.WorktreeListInput;
import com.jwcode.core.tool.output.WorktreeListOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * WorktreeListTool - 列出 Git Worktree 工具
 * 
 * 功能说明：
 * 列出所有的 Git Worktree，并标记当前所在的 worktree。
 * 支持显示详细信息。
 * 
 * 使用场景：
 * - 查看所有可用的 worktree
 * - 确认当前所在的 worktree
 * - 管理工作树
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class WorktreeListTool implements Tool<WorktreeListInput, WorktreeListOutput, Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(WorktreeListTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final WorktreeManager worktreeManager;
    
    public WorktreeListTool() {
        this.worktreeManager = new WorktreeManager();
    }
    
    public WorktreeListTool(WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
    }
    
    @Override
    public String getName() {
        return "WorktreeList";
    }
    
    @Override
    public String getDescription() {
        return "List all Git worktrees and mark the current one";
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool to list all available Git worktrees. " +
               "The current worktree will be marked in the output.";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "verbose": {
                            "type": "boolean",
                            "description": "Show detailed information about each worktree",
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
    public TypeReference<WorktreeListInput> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<WorktreeListOutput> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<WorktreeListOutput>> call(
            WorktreeListInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error(validation.getFirstError());
                }
                
                // 获取当前工作目录
                Path currentDir = context.getWorkingDirectory().toAbsolutePath().normalize();
                
                // 列出所有 worktrees
                List<WorktreeInfo> worktrees = worktreeManager.listWorktreesSync();
                
                if (worktrees.isEmpty()) {
                    String message = "No worktrees found. Use 'git worktree add' to create one.";
                    WorktreeListOutput output = WorktreeListOutput.success(
                        worktrees, null, null, message
                    );
                    return ToolResult.success(output);
                }
                
                // 找到当前 worktree 的索引
                Integer currentIndex = null;
                Path currentWorktreePath = null;
                
                for (int i = 0; i < worktrees.size(); i++) {
                    WorktreeInfo wt = worktrees.get(i);
                    Path wtPath = wt.getPath().toAbsolutePath().normalize();
                    
                    if (wtPath.equals(currentDir)) {
                        currentIndex = i;
                        currentWorktreePath = wtPath;
                        break;
                    }
                }
                
                // 构建消息
                StringBuilder message = new StringBuilder();
                message.append(String.format("Found %d worktree(s):%n", worktrees.size()));
                
                for (int i = 0; i < worktrees.size(); i++) {
                    WorktreeInfo wt = worktrees.get(i);
                    boolean isCurrent = (i == currentIndex);
                    
                    message.append(String.format("%n[%d] %s%s%n", 
                        i + 1,
                        isCurrent ? "* " : "  ",
                        wt.getPath()
                    ));
                    
                    message.append(String.format("    Branch: %s%n", wt.getBranchName()));
                    
                    if (input != null && input.verbose()) {
                        message.append(String.format("    Commit: %s%n", 
                            wt.getCommit() != null ? wt.getCommit().substring(0, 7) : "N/A"
                        ));
                        message.append(String.format("    Detached: %s%n", wt.isDetached()));
                        message.append(String.format("    Main: %s%n", wt.isMain()));
                        message.append(String.format("    Valid: %s%n", wt.isValid()));
                    }
                }
                
                if (currentIndex != null) {
                    message.append(String.format("%nCurrent worktree: [%d] %s", 
                        currentIndex + 1, currentWorktreePath));
                } else {
                    message.append(String.format("%nCurrent directory is not a worktree: %s", currentDir));
                }
                
                WorktreeListOutput output = WorktreeListOutput.success(
                    worktrees,
                    currentIndex,
                    currentWorktreePath != null ? currentWorktreePath.toString() : null,
                    message.toString()
                );
                
                logger.debug("Listed {} worktrees, current index: {}", worktrees.size(), currentIndex);
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                logger.error("Failed to list worktrees: {}", e.getMessage(), e);
                return ToolResult.error("Failed to list worktrees: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(WorktreeListInput input) {
        // WorktreeList 没有必填参数
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(WorktreeListInput input) {
        // 列出 worktrees 是只读操作
        return true;
    }
    
    @Override
    public boolean isDestructive(WorktreeListInput input) {
        return false;
    }
    
    @Override
    public boolean requiresApproval(WorktreeListInput input) {
        return false;
    }
}
