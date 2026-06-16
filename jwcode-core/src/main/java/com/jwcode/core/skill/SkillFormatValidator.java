package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * 技能格式验证器 — 验证 .skill.md 文件的格式正确性。
 */
public class SkillFormatValidator {

    private static final Logger logger = Logger.getLogger(SkillFormatValidator.class.getName());

    private static final Pattern VALID_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");
    private static final Pattern FRONT_MATTER = Pattern.compile(
        "^---\\s*\\n.*?\\n---", Pattern.DOTALL);

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /**
     * 验证单个 .skill.md 文件。
     *
     * @return true 如果验证通过
     */
    public boolean validate(Path file) {
        errors.clear();
        warnings.clear();

        if (!Files.isRegularFile(file)) {
            errors.add("文件不存在: " + file);
            return false;
        }

        try {
            String content = Files.readString(file);
            validateContent(content, file.toString());
        } catch (IOException e) {
            errors.add("读取失败: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            logger.warning("[SkillFormatValidator] 验证失败: " + file + " (" + errors.size() + " 个错误)");
            return false;
        }
        return true;
    }

    /**
     * 验证技能内容字符串。
     */
    public boolean validateContent(String content, String source) {
        errors.clear();
        warnings.clear();

        if (content == null || content.isBlank()) {
            errors.add("内容为空: " + source);
            return false;
        }

        // 检查 front matter 分隔符
        if (!content.startsWith("---\n")) {
            errors.add("缺少开头的 --- front matter 分隔符");
        }

        Matcher fmMatcher = FRONT_MATTER.matcher(content);
        if (!fmMatcher.find()) {
            errors.add("front matter 格式错误（需要 --- 分隔的 YAML）");
        }

        // 解析并验证
        try {
            SkillDefinition def = SkillMarkdownParser.parseContent(content, source);
            if (def == null) {
                errors.add("解析失败");
                return false;
            }
            validateDefinition(def);
        } catch (Exception e) {
            errors.add("解析异常: " + e.getMessage());
            return false;
        }

        return errors.isEmpty();
    }

    private void validateDefinition(SkillDefinition def) {
        // 验证 ID 格式
        if (!VALID_ID.matcher(def.id()).matches()) {
            errors.add("技能 ID 格式无效（仅允许字母数字、下划线和连字符）: " + def.id());
        }

        // 验证名称
        if (def.name() == null || def.name().isBlank()) {
            warnings.add("技能名称建议不为空");
        }

        // 验证 systemPrompt
        if (def.systemPrompt() == null || def.systemPrompt().isBlank()) {
            warnings.add("技能 systemPrompt 为空");
        }

        // 验证 injection 策略
        if (def.injectionStrategy() == null) {
            warnings.add("注入策略未指定，将使用默认 LAZY");
        }
    }

    public List<String> getErrors() { return List.copyOf(errors); }
    public List<String> getWarnings() { return List.copyOf(warnings); }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
}
