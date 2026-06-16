package com.jwcode.core.skill;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能模板变量解析器。
 *
 * <p>支持在 .skill.md 文件中使用以下变量：
 * <ul>
 *   <li>{@code ${JWCODE_SKILL_DIR}} — 技能文件所在目录</li>
 *   <li>{@code ${JWCODE_SESSION_ID}} — 当前会话 ID</li>
 *   <li>{@code ${JWCODE_PROJECT_ROOT}} — 当前项目根目录</li>
 *   <li>{@code ${JWCODE_USER_HOME}} — ~/.jwcode/ 目录</li>
 *   <li>{@code ${config:<key>}} — 配置值</li>
 * </ul>
 */
public class SkillTemplateResolver {
    private static final Logger logger = Logger.getLogger(SkillTemplateResolver.class.getName());

    private static final Pattern VAR_PATTERN = Pattern.compile(
        "\\$\\{(JWCODE_[A-Z_]+|config:[a-zA-Z0-9._-]+)\\}");

    private final ThreadLocal<String> sessionId = new ThreadLocal<>();
    private final ThreadLocal<String> projectRoot = new ThreadLocal<>();
    private final Map<String, String> configOverrides = new ConcurrentHashMap<>();

    /**
     * 设置当前线程的会话 ID。
     */
    public void setSessionId(String id) {
        sessionId.set(id);
    }

    /**
     * 设置当前线程的项目根目录。
     */
    public void setProjectRoot(String root) {
        projectRoot.set(root);
    }

    /**
     * 设置配置覆盖值。
     */
    public void setConfig(String key, String value) {
        configOverrides.put(key, value);
    }

    /**
     * 解析文本中的所有模板变量。
     *
     * @param text    包含模板变量的文本
     * @param skillSource 技能文件路径（用于推导 SKILL_DIR）
     * @return 解析后的文本
     */
    public String resolve(String text, String skillSource) {
        if (text == null || text.isBlank()) return text;

        String userHome = Path.of(System.getProperty("user.home"), ".jwcode").toString();
        String skillDir = deriveSkillDir(skillSource);

        Matcher matcher = VAR_PATTERN.matcher(text);
        StringBuffer buf = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = resolveVar(varName, skillDir, userHome);
            if (replacement != null) {
                matcher.appendReplacement(buf, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(buf);
        return buf.toString();
    }

    private String resolveVar(String varName, String skillDir, String userHome) {
        return switch (varName) {
            case "JWCODE_SKILL_DIR" -> skillDir;
            case "JWCODE_SESSION_ID" -> {
                String v = sessionId.get();
                yield v != null ? v : "";
            }
            case "JWCODE_PROJECT_ROOT" -> {
                String root = projectRoot.get();
                yield root != null ? root : System.getProperty("user.dir");
            }
            case "JWCODE_USER_HOME" -> userHome;
            default -> {
                if (varName.startsWith("config:")) {
                    String key = varName.substring(7);
                    yield configOverrides.getOrDefault(key, "${" + varName + "}");
                }
                yield "${" + varName + "}"; // 未识别的变量保留原样
            }
        };
    }

    private static String deriveSkillDir(String source) {
        if (source == null) return "";
        Path path = Path.of(source);
        Path parent = path.getParent();
        return parent != null ? parent.toString().replace('\\', '/') : "";
    }
}
