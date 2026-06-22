package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.config.SystemPromptAssembler;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.ChangeDirectoryInput;
import com.jwcode.core.tool.output.ChangeDirectoryOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * ChangeDirectory 工具 — 切换 AI 的当前工作目录（黑板模式）。
 *
 * <p>不执行任何 shell 命令，直接更新 Session.workingDirectory。
 * 更新后通过清除缓存的 [ENV_INFO] 系统消息触发环境信息刷新，
 * 使后续所有工具（Bash, FileRead, Glob, Grep 等）在新目录下执行。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>不依赖 BashTool — 直接操作 Session 状态（黑板模式）</li>
 *   <li>路径校验 — 目标必须存在且为目录</li>
 *   <li>环境刷新 — 切换后清除旧环境缓存，确保 &lt;environment&gt; 使用最新目录</li>
 *   <li>WorkspaceGuard 更新 — 切换后创建新的 WorkspaceGuard 保护新目录</li>
 * </ul>
 *
 * @since 3.1.0
 */
public class ChangeDirectoryTool implements Tool<ChangeDirectoryInput, ChangeDirectoryOutput, Void> {

    private static final Logger logger = LoggerFactory.getLogger(ChangeDirectoryTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "ChangeDirectory";
    }

    @Override
    public String getDescription() {
        return "切换 AI 的工作目录。更新后所有文件和命令操作将在新目录下执行。";
    }

    @Override
    public String getPrompt() {
        return """
               ChangeDirectory — 切换 AI 的当前工作目录。

               ## 何时使用：
               - 需要处理不同位置的项目文件时
               - 用户要求"切换到某个目录"时
               - 当前 <environment> 中的 workingDirectory 不匹配用户需求时

               ## 工作原理：
               - 更新会话的工作目录（Session.workingDirectory）
               - 刷新 <environment> 环境信息中的 workingDirectory
               - 后续所有操作（Bash, FileRead, FileWrite, Glob, Grep）
                 将基于新目录执行

               参数:
               - path: 目标目录路径（必需）。支持绝对路径和相对路径。

               注意:
               - 不要使用 Bash 的 cd 命令来切换目录，那只在当前命令进程内有效
               - 请使用本工具 ChangeDirectory 来持久化切换目录
               - 目标路径必须存在且是一个目录

               示例:
               - {"path": "/home/user/project"} — 切换到绝对路径
               - {"path": "../other-project"} — 切换到父目录下的兄弟项目
               - {"path": "subdir"} — 切换到当前目录下的子目录
               """;
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string", "description": "目标目录路径，支持绝对路径和相对路径"}
                    },
                    "required": ["path"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TypeReference<ChangeDirectoryInput> getInputType() {
        return new TypeReference<ChangeDirectoryInput>() {};
    }

    @Override
    public TypeReference<ChangeDirectoryOutput> getOutputType() {
        return new TypeReference<ChangeDirectoryOutput>() {};
    }

    @Override
    public CompletableFuture<ToolResult<ChangeDirectoryOutput>> call(
            ChangeDirectoryInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String rawPath = input.path().trim();

                // 1. 解析路径
                Path resolvedPath = resolveTargetPath(rawPath, context.getWorkingDirectory());
                String resolvedStr = resolvedPath.toAbsolutePath().normalize().toString();

                // 2. 校验路径存在且是目录
                if (!Files.exists(resolvedPath)) {
                    logger.warn("ChangeDirectory target does not exist: {}", resolvedStr);
                    return ToolResult.error("目录不存在: " + resolvedStr);
                }
                if (!Files.isDirectory(resolvedPath)) {
                    logger.warn("ChangeDirectory target is not a directory: {}", resolvedStr);
                    return ToolResult.error("路径不是目录: " + resolvedStr);
                }

                // 3. 更新 Session.workingDirectory
                Session session = context.getSession();
                String oldDir = session.getWorkingDirectory();
                session.setWorkingDirectory(resolvedStr);

                // 4. 清除旧的 [ENV_INFO] 系统消息，使下次组装环境信息时刷新
                int removedCount = session.removeSystemMessagesContaining("Working Directory");

                // 5. 失效系统提示词的环境缓存
                SystemPromptAssembler.invalidateEnvironmentCache();

                logger.info("ChangeDirectory: {} -> {} (removed {} env messages)", oldDir, resolvedStr, removedCount);

                // 6. 构建成功消息
                String message = "✅ 工作目录已切换:\n"
                    + "  旧目录: " + oldDir + "\n"
                    + "  新目录: " + resolvedStr + "\n\n"
                    + "所有后续操作将基于新目录执行。";

                ChangeDirectoryOutput output = ChangeDirectoryOutput.success(resolvedStr, message);

                ToolResult<ChangeDirectoryOutput> result = ToolResult.success(output);
                result.setContent(message);
                result.setMetadata(java.util.Map.of(
                    "oldDir", oldDir,
                    "newDir", resolvedStr
                ));

                return result;

            } catch (Exception e) {
                logger.error("ChangeDirectory failed", e);
                return ToolResult.error("切换目录失败: " + e.getMessage());
            }
        });
    }

    /**
     * 解析目标路径。
     * <ul>
     *   <li>如果路径以 / 开头，视为绝对路径</li>
     *   <li>如果路径以 ~ 开头，替换为当前用户 home 目录</li>
     *   <li>其他情况，相对于当前工作目录解析</li>
     * </ul>
     */
    private Path resolveTargetPath(String pathStr, Path currentWorkingDirectory) {
        // 处理 ~ 开头的路径
        if (pathStr.startsWith("~")) {
            String userHome = System.getProperty("user.home");
            if (pathStr.equals("~") || pathStr.equals("~/")) {
                return Paths.get(userHome);
            }
            return Paths.get(userHome, pathStr.substring(1).replace("/", "\\"));
        }

        Path path = Paths.get(pathStr);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        // 相对路径：相对于当前工作目录
        if (currentWorkingDirectory != null) {
            return currentWorkingDirectory.resolve(path).normalize();
        }
        return Paths.get("").toAbsolutePath().resolve(path).normalize();
    }

    @Override
    public ToolValidationResult validate(ChangeDirectoryInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.path() == null || input.path().isBlank()) {
            return ToolValidationResult.invalid("path 是必需的");
        }
        return ToolValidationResult.valid();
    }

    @Override
    public boolean isReadOnly(ChangeDirectoryInput input) {
        return false;
    }

    @Override
    public boolean isDestructive(ChangeDirectoryInput input) {
        return false;
    }

    @Override
    public boolean requiresApproval(ChangeDirectoryInput input) {
        return false;
    }
}
