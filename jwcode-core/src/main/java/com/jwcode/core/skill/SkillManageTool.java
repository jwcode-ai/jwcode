package com.jwcode.core.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolResult;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.jwcode.core.tool.ConcurrencyLevel;
import com.jwcode.core.tool.ToolProgress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 技能管理工具 — 供 LLM Agent（如背景回顾 Agent）创建、编辑、修补和删除技能。
 *
 * <p>操作类型：
 * <ul>
 *   <li>create — 创建新技能（生成 .skill.md 文件）</li>
 *   <li>edit — 重写现有技能</li>
 *   <li>patch — 修补技能内容</li>
 *   <li>delete — 删除技能</li>
 * </ul>
 */
public class SkillManageTool implements Tool<Map<String, Object>, Map<String, Object>, Void> {

    private static final Logger logger = Logger.getLogger(SkillManageTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillRegistry skillRegistry;
    private final Path userSkillsDir;

    public SkillManageTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        this.userSkillsDir = Path.of(System.getProperty("user.home"), ".jwcode", "skills");
    }

    @Override
    public String getName() {
        return "skill-manage";
    }

    @Override
    public String getDescription() {
        return "创建、编辑、修补或删除技能。操作类型: create, edit, patch, delete。";
    }

    @Override
    public String getPrompt() {
        return """
            创建/管理 .skill.md 技能文件。

            参数:
            - action: "create" | "edit" | "patch" | "delete"
            - id: 技能 ID（仅字母数字和连字符）
            - name: 技能显示名称（仅 create/edit）
            - description: 技能描述（仅 create/edit）
            - trigger: 触发关键词，逗号分隔（仅 create/edit）
            - tags: 标签数组（仅 create/edit）
            - systemPrompt: Markdown 提示词正文（仅 create/edit）
            - injection: "lazy" | "eager" | "hybrid"（仅 create/edit，默认 lazy）

            操作结果:
            - success: true/false
            - message: 结果描述
            - skillId: 技能 ID（仅 create/edit）
            """;
    }

    @Override
    public CompletableFuture<ToolResult<Map<String, Object>>> call(
            Map<String, Object> input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {

        String action = (String) input.getOrDefault("action", "");

        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (action) {
                    case "create" -> handleCreate(input);
                    case "edit" -> handleEdit(input);
                    case "patch" -> handlePatch(input);
                    case "delete" -> handleDelete(input);
                    default -> ToolResult.error("未知操作: " + action);
                };
            } catch (Exception e) {
                logger.warning("[SkillManageTool] 操作失败: " + e.getMessage());
                return ToolResult.error("操作失败: " + e.getMessage());
            }
        });
    }

    private ToolResult<Map<String, Object>> handleCreate(Map<String, Object> input) {
        String id = (String) input.get("id");
        String name = (String) input.getOrDefault("name", id);
        String description = (String) input.getOrDefault("description", "");
        String trigger = (String) input.getOrDefault("trigger", "");
        String systemPrompt = (String) input.getOrDefault("systemPrompt", "");
        String injection = (String) input.getOrDefault("injection", "lazy");

        if (id == null || id.isBlank()) {
            return ToolResult.error("技能 ID 不能为空");
        }

        // 构建 .skill.md 内容
        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append("id: ").append(id).append("\n");
        content.append("name: ").append(name).append("\n");
        content.append("description: ").append(description).append("\n");
        if (!trigger.isBlank()) {
            content.append("trigger: ").append(trigger).append("\n");
        }
        content.append("tags: [").append(id).append("]\n");
        content.append("injection: ").append(injection).append("\n");
        content.append("---\n\n");
        content.append(systemPrompt.isBlank() ? "# " + name + "\n\n请描述此技能的行为。" : systemPrompt);
        content.append("\n");

        // 写入文件
        try {
            Files.createDirectories(userSkillsDir);
            Path file = userSkillsDir.resolve(id + ".skill.md");
            Files.writeString(file, content);

            // 解析并注册
            SkillDefinition def = SkillMarkdownParser.parse(file, Skill.Provenance.AGENT_CREATED);
            if (def != null) {
                skillRegistry.register(def.toSkill());
                logger.info("[SkillManageTool] 创建技能: " + id + " -> " + file);
                return ToolResult.success(Map.of(
                    "success", true,
                    "message", "技能已创建: " + id,
                    "skillId", id
                ));
            }

            return ToolResult.error("技能文件解析失败");
        } catch (IOException e) {
            return ToolResult.error("写入失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult<Map<String, Object>> handleEdit(Map<String, Object> input) {
        // 编辑 = 重新创建，覆盖已有文件
        return handleCreate(input);
    }

    @SuppressWarnings("unchecked")
    private ToolResult<Map<String, Object>> handlePatch(Map<String, Object> input) {
        String id = (String) input.get("id");
        if (id == null) {
            return ToolResult.error("技能 ID 不能为空");
        }

        Path file = userSkillsDir.resolve(id + ".skill.md");
        if (!Files.isRegularFile(file)) {
            return ToolResult.error("技能文件不存在: " + file);
        }

        return ToolResult.success(Map.of(
            "success", true,
            "message", "修补功能尚未完整实现，请使用 edit 重新创建"
        ));
    }

    @SuppressWarnings("unchecked")
    private ToolResult<Map<String, Object>> handleDelete(Map<String, Object> input) {
        String id = (String) input.get("id");
        if (id == null) {
            return ToolResult.error("技能 ID 不能为空");
        }

        Path file = userSkillsDir.resolve(id + ".skill.md");
        try {
            boolean deleted = Files.deleteIfExists(file);
            skillRegistry.unregister(id);
            logger.info("[SkillManageTool] 删除技能: " + id + " (文件删除: " + deleted + ")");
            return ToolResult.success(Map.of(
                "success", true,
                "message", "技能已删除: " + id
            ));
        } catch (IOException e) {
            return ToolResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly(Map<String, Object> input) {
        return false;
    }

    @Override
    public ConcurrencyLevel getConcurrencyLevel() {
        return ConcurrencyLevel.SEQUENTIAL;
    }
}
