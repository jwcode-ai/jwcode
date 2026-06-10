package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .skill.md 文件解析器。
 *
 * <p>格式：YAML front matter（--- 分隔） + Markdown body。
 *
 * <pre>
 * ---
 * id: code-review
 * name: 代码审查
 * description: 审查代码质量、安全性和最佳实践
 * trigger: 审查这段代码
 * tags: [review, security, quality]
 * tools: [FileReadTool, GrepTool]
 * injection: lazy
 * ---
 *
 * # 代码审查指南
 *
 * ... markdown body as system prompt ...
 * </pre>
 */
public class SkillMarkdownParser {
    private static final Logger logger = Logger.getLogger(SkillMarkdownParser.class.getName());

    private static final Pattern FRONT_MATTER = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);

    /**
     * 从文件解析技能定义。
     */
    public static SkillDefinition parse(Path file) throws IOException {
        String content = Files.readString(file);
        return parseContent(content, file.toString());
    }

    /**
     * 从内容字符串解析。
     */
    public static SkillDefinition parseContent(String content, String source) {
        Matcher m = FRONT_MATTER.matcher(content);
        if (!m.find()) {
            logger.warning("[SkillMarkdownParser] 未找到 front matter: " + source);
            return null;
        }

        Map<String, String> meta = parseYamlFrontMatter(m.group(1));
        String body = m.group(2).trim();

        String id = meta.getOrDefault("id", deriveId(source));
        String name = meta.getOrDefault("name", id);
        String description = meta.getOrDefault("description", "");
        String trigger = meta.getOrDefault("trigger", "");
        List<String> tags = parseList(meta.get("tags"));
        List<String> tools = parseList(meta.get("tools"));
        String injection = meta.getOrDefault("injection", "lazy");

        SkillDefinition.InjectionStrategy strategy;
        try {
            strategy = SkillDefinition.InjectionStrategy.valueOf(injection.toUpperCase());
        } catch (IllegalArgumentException e) {
            strategy = SkillDefinition.InjectionStrategy.LAZY;
        }

        return new SkillDefinition(id, name, description, trigger,
            body, tools, tags, strategy, source);
    }

    /**
     * 解析简易 YAML front matter（仅支持顶层字符串和列表）。
     */
    private static Map<String, String> parseYamlFrontMatter(String yaml) {
        Map<String, String> result = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : yaml.split("\n")) {
            if (line.trim().isEmpty()) continue;

            // 检查是否为新 key
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && (line.length() <= colonIdx + 1
                || line.charAt(colonIdx + 1) == ' '
                || line.charAt(colonIdx + 1) == '[')) {

                // 保存前一个 key
                if (currentKey != null) {
                    result.put(currentKey, currentValue.toString().trim());
                }

                currentKey = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                currentValue = new StringBuilder(value);
            } else if (currentKey != null) {
                // 续行（列表项）
                currentValue.append(" ").append(line.trim());
            }
        }

        if (currentKey != null) {
            result.put(currentKey, currentValue.toString().trim());
        }

        return result;
    }

    private static List<String> parseList(String value) {
        if (value == null || value.isBlank()) return List.of();
        // 去除 YAML 方括号
        value = value.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static String deriveId(String source) {
        String name = Path.of(source).getFileName().toString();
        // 去除 .skill.md 后缀
        if (name.endsWith(".skill.md")) name = name.substring(0, name.length() - 9);
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
