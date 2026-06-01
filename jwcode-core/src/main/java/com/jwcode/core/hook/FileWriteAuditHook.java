package com.jwcode.core.hook;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * FileWriteAuditHook — 文件修改前需用户审批。
 *
 * <p>Claude Code 风格：PRE_TOOL_USE 阶段检查 FileWriteTool/FileEditTool，
 * 提取目标文件路径，要求用户确认。</p>
 *
 * <p>项目级 Hook，fail-open（超时默认放行）。</p>
 */
public class FileWriteAuditHook implements HookExecutor {

    private static final Set<String> TARGET_TOOLS = Set.of("FileWriteTool", "FileEditTool");

    @Override
    public CompletableFuture<HookResult> execute(HookContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (context.getToolName() == null
                    || !TARGET_TOOLS.contains(context.getToolName())) {
                    return HookResult.allow("FileWriteAuditHook");
                }
                // Act 模式下自动批准文件写入
                if (com.jwcode.core.plan.PlanModeManager.getInstance().isActMode()) {
                    return HookResult.allow("FileWriteAuditHook", "Act mode - auto approve file writes");
                }
                String fp = extractFilePath(context);
                String tool = context.getToolName();
                return HookResult.ask("FileWriteAuditHook",
                    fp != null ? fp : "(unknown file)",
                    "审批文件修改: " + tool);
            } catch (Exception e) {
                return HookResult.allow("FileWriteAuditHook", "Hook error, fail-open");
            }
        });
    }

    private String extractFilePath(HookContext ctx) {
        if (ctx.getToolResult() != null) {
            try {
                var fpNode = ctx.getToolResult().get("filePath");
                if (fpNode != null) return fpNode.asText();
            } catch (Exception ignored) {}
        }
        if (ctx.getToolInput() != null) {
            try {
                var fpNode = ctx.getToolInput().get("filePath");
                if (fpNode == null) fpNode = ctx.getToolInput().get("file_path");
                if (fpNode == null) fpNode = ctx.getToolInput().get("path");
                if (fpNode != null) return fpNode.asText();
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public HookImplementationType getType() { return HookImplementationType.SHELL; }

    @Override
    public String getName() { return "FileWriteAuditHook"; }

    @Override
    public HookPriority getPriority() { return HookPriority.PROJECT; }

    @Override
    public boolean isFailOpen() { return true; }

    @Override
    public long getTimeoutMs() { return 3000; }

    @Override
    public boolean supportsEvent(HookEventType eventType) {
        return eventType == HookEventType.PRE_TOOL_USE;
    }
}
