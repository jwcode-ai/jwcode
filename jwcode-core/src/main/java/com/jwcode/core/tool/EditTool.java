package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * EditTool - 代码编辑工具（基于 old_string/new_string 的精确字符串替换）。
 *
 * <p>与 FileEditTool（old_content/new_content）互补，提供更精确的单次替换语义，
 * 依赖于调用方传入文件中实际存在的精确字符串。</p>
 *
 * <p>【反编造】此工具真实执行文件读写操作，返回实际结果而非虚假成功。</p>
 */
public class EditTool implements Tool<EditTool.Input, EditTool.Output, EditTool.Progress> {

    private static final Logger logger = Logger.getLogger(EditTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "EditTool";
    }

    @Override
    public String getDescription() {
        return "对文件内容进行精确编辑——使用 old_string 匹配并替换为 new_string。支持单次精确替换。";
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {
                   "file_path": {"type": "string", "description": "要编辑的文件路径"},
                   "old_string": {"type": "string", "description": "文件中存在的精确字符串，将被替换"},
                   "new_string": {"type": "string", "description": "替换后的新字符串"}
                 },
                 "required": ["file_path", "old_string"]
               }
               """;
            return MAPPER.readTree(schema);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }

    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }

    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // === 1. 输入校验 ===
                if (args.file_path == null || args.file_path.trim().isEmpty()) {
                    return ToolResult.error("EditTool 失败: file_path 不能为空");
                }
                if (args.old_string == null) {
                    return ToolResult.error("EditTool 失败: old_string 不能为 null");
                }
                String newStr = args.new_string != null ? args.new_string : "";

                // === 2. 路径解析与工作区安全校验 ===
                Path filePath = Paths.get(args.file_path);
                if (!filePath.isAbsolute()) {
                    Path workingDir = context.getWorkingDirectory();
                    if (workingDir != null) {
                        filePath = workingDir.resolve(filePath);
                    }
                }
                filePath = filePath.normalize().toAbsolutePath();

                // 工作区边界校验
                try {
                    context.validatePath(filePath, getName());
                } catch (SecurityException se) {
                    return ToolResult.error("EditTool 安全拒绝: " + se.getMessage());
                }

                // === 3. 读取文件内容 ===
                if (!Files.exists(filePath)) {
                    return ToolResult.error("EditTool 失败: 文件不存在 - " + filePath);
                }
                String fileContent;
                try {
                    fileContent = Files.readString(filePath);
                } catch (IOException e) {
                    return ToolResult.error("EditTool 失败: 无法读取文件 - " + e.getMessage());
                }

                // === 4. 执行字符串替换 ===
                String oldStr = args.old_string;
                int matchCount = countOccurrences(fileContent, oldStr);

                if (matchCount == 0) {
                    return ToolResult.error(
                        "EditTool 失败: 在文件 " + args.file_path + " 中找不到 old_string。\n"
                        + "请使用 FileReadTool 重新读取文件最新内容，确认 old_string 与实际内容完全一致（包括空格、缩进、换行）。"
                    );
                }
                if (matchCount > 1) {
                    return ToolResult.error(
                        "EditTool 失败: old_string 在文件中匹配到 " + matchCount + " 处。\n"
                        + "请提供更长的上下文使 old_string 唯一匹配，或使用 FileEditTool 进行多处替换。"
                    );
                }

                String newContent = fileContent.replace(oldStr, newStr);

                // === 5. 写入文件 ===
                try {
                    Files.writeString(filePath, newContent, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    return ToolResult.error("EditTool 失败: 无法写入文件 - " + e.getMessage());
                }

                // === 6. 构建真实输出 ===
                Output output = new Output();
                output.file_path = filePath.toString();
                output.success = true;
                output.old_string = oldStr;
                output.new_string = newStr;
                output.bytes_before = fileContent.length();
                output.bytes_after = newContent.length();

                logger.info("EditTool 成功: " + filePath + " (修改 " + (newContent.length() - fileContent.length()) + " 字节)");
                return ToolResult.success(output);

            } catch (Exception e) {
                logger.severe("EditTool 异常: " + e.getMessage());
                return ToolResult.error("EditTool 执行异常: " + e.getMessage());
            }
        });
    }

    /**
     * 统计子字符串在内容中出现的次数。
     */
    private static int countOccurrences(String content, String sub) {
        if (sub.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    @Override
    public boolean isReadOnly(Input input) {
        return false;  // 文件编辑是写操作
    }

    // ==================== 数据类 ====================

    public static class Input {
        public String file_path;
        public String old_string;
        public String new_string;
    }

    public static class Output {
        public String file_path;
        public boolean success;
        /** 实际被替换的旧字符串（回显确认） */
        public String old_string;
        /** 替换后的新字符串（回显确认） */
        public String new_string;
        /** 编辑前文件大小（字节） */
        public int bytes_before;
        /** 编辑后文件大小（字节） */
        public int bytes_after;
    }

    public static class Progress {}
}
