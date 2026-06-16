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
 * platforms: [windows, linux, macos]     # 可选，平台限制
 * config:                                 # 可选，所需配置变量
 *   - api_key
 *   - endpoint
 * examples:                               # 可选，使用示例
 *   - "审查这段代码"
 *   - "检查安全性"
 * ---
 *
 * # 代码审查指南
 *
 * ... markdown body as system prompt ...
 * </pre>
 *
 * <p>支持扩展字段（agentskills.io 兼容）：
 * <ul>
 *   <li>{@code platforms} — 平台限制列表</li>
 *   <li>{@code config} — 所需配置变量列表</li>
 *   <li>{@code examples} — 使用示例列表</li>
 * </ul>
 */
public class SkillMarkdownParser {
    private static final Logger logger = Logger.getLogger(SkillMarkdownParser.class.getName());

    private static final Pattern FRONT_MATTER = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);

    /**
     * 从文件解析技能定义。
     */
    public static SkillDefinition parse(Path file) throws IOException {
        return parse(file, Skill.Provenance.SYSTEM);
    }

    /**
     * 从文件解析技能定义，指定来源类型。
     */
    public static SkillDefinition parse(Path file, Skill.Provenance provenance) throws IOException {
        String content = Files.readString(file);
        return parseContent(content, file.toString(), provenance);
    }

    /**
     * 从内容字符串解析。
     */
    public static SkillDefinition parseContent(String content, String source) {
        return parseContent(content, source, Skill.Provenance.SYSTEM);
    }

    /**
     * 从内容字符串解析，指定来源类型。
     */
    public static SkillDefinition parseContent(String content, String source, Skill.Provenance provenance) {
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
            body, tools, tags, strategy, source, provenance);
    }

    /**
     * 解析额外扩展字段（agentskills.io 兼容）。
     *
     * @return 包含 platforms、config 和 examples 的映射
     */
    public static Map<String, List<String>> parseExtraFields(String content) {
        Matcher m = FRONT_MATTER.matcher(content);
        if (!m.find()) {
            return Map.of();
        }
        return parseExtraFieldsFromYaml(m.group(1));
    }

    /**
     * 从文件解析额外扩展字段。
     */
    public static Map<String, List<String>> parseExtraFields(Path file) throws IOException {
        String content = Files.readString(file);
        return parseExtraFields(content);
    }

    /**
     * 从 YAML front matter 解析扩展字段。
     */
    private static Map<String, List<String>> parseExtraFieldsFromYaml(String yaml) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String currentKey = null;
        List<String> currentList = new ArrayList<>();

        for (String line : yaml.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && (line.length() <= colonIdx + 1
                || line.charAt(colonIdx + 1) == ' '
                || line.charAt(colonIdx + 1) == '[')) {

                // 保存前一个 key
                if (currentKey != null && !currentList.isEmpty()) {
                    result.put(currentKey, currentList);
                }

                currentKey = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                currentList = new ArrayList<>();

                if (value.startsWith("[") && value.endsWith("]")) {
                    // 内联列表: [a, b, c]
                    String inner = value.substring(1, value.length() - 1);
                    for (String item : inner.split(",")) {
                        String itemTrimmed = item.trim();
                        if (!itemTrimmed.isEmpty()) {
                            currentList.add(stripQuotes(itemTrimmed));
                        }
                    }
                }
                // YAML 列表项以 - 开头，由下面的 else-if 处理
            } else if (trimmed.startsWith("- ") && currentKey != null) {
                currentList.add(stripQuotes(trimmed.substring(2).trim()));
            }
        }

        if (currentKey != null && !currentList.isEmpty()) {
            result.put(currentKey, currentList);
        }

        // 只返回扩展字段
        Map<String, List<String>> filtered = new LinkedHashMap<>();
        for (String key : List.of("platforms", "config", "examples")) {
            if (result.containsKey(key)) {
                filtered.put(key, result.get(key));
            }
        }
        return filtered;
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

            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && (line.length() <= colonIdx + 1
                || line.charAt(colonIdx + 1) == ' '
                || line.charAt(colonIdx + 1) == '[')) {

                if (currentKey != null) {
                    result.put(currentKey, currentValue.toString().trim());
                }

                currentKey = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                currentValue = new StringBuilder(value);
            } else if (currentKey != null) {
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
        if (name.endsWith(".skill.md")) name = name.substring(0, name.length() - 9);
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\""))
            || (s.startsWith("'") && s.endsWith("'"))
            && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
