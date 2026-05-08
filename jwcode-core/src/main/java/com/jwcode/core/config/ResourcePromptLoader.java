package com.jwcode.core.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ResourcePromptLoader — 从 classpath 资源目录加载分层提示词。
 *
 * <p>加载路径：{@code classpath:system-prompt/}
 * 与 {@link SystemPromptAssembler} 的文件系统加载互补。
 * 优先级：文件系统 > classpath 资源 > 内置默认值。</p>
 *
 * <p>目录结构：</p>
 * <pre>
 * system-prompt/
 * ├── core.md              — AI 身份定义
 * ├── rules.md             — 行为规则
 * ├── protocols/           — 协议定义
 * │   ├── 01-tool-calling.md
 * │   ├── 02-error-recovery.md
 * │   └── ...
 * ├── roles/               — 角色提示词
 * │   ├── orchestrator.md
 * │   ├── coder.md
 * │   └── ...
 * └── templates/           — 任务模板
 *     ├── feature-dev.md
 *     ├── bug-fix.md
 *     └── ...
 * </pre>
 */
public class ResourcePromptLoader {

    private static final Logger logger = Logger.getLogger(ResourcePromptLoader.class.getName());

    private static final String BASE_PATH = "system-prompt/";

    /**
     * 从 classpath 加载完整提示词
     */
    public static String loadFullPrompt() {
        StringBuilder sb = new StringBuilder();

        // 1. 核心身份
        String core = loadResource(BASE_PATH + "core.md");
        if (core != null) sb.append(core).append("\n\n");

        // 2. 行为规则
        String rules = loadResource(BASE_PATH + "rules.md");
        if (rules != null) sb.append(rules).append("\n\n");

        // 3. 协议定义
        String protocols = loadProtocols();
        if (protocols != null) sb.append(protocols).append("\n\n");

        // 4. 角色定义（作为上下文参考）
        String roles = loadRoles();
        if (roles != null) sb.append(roles).append("\n\n");

        // 5. 任务模板（作为上下文参考）
        String templates = loadTemplates();
        if (templates != null) sb.append(templates).append("\n\n");

        return sb.toString();
    }

    /**
     * 加载特定角色的提示词
     */
    public static String loadRolePrompt(String roleName) {
        return loadResource(BASE_PATH + "roles/" + roleName + ".md");
    }

    /**
     * 加载特定任务的模板
     */
    public static String loadTaskTemplate(String templateName) {
        return loadResource(BASE_PATH + "templates/" + templateName + ".md");
    }

    /**
     * 加载 protocols/ 目录下所有 .md 文件
     */
    private static String loadProtocols() {
        return loadDirectory(BASE_PATH + "protocols/", "## PROTOCOLS\n\n");
    }

    /**
     * 加载 roles/ 目录下所有 .md 文件
     */
    private static String loadRoles() {
        return loadDirectory(BASE_PATH + "roles/", "## AGENT ROLES\n\n");
    }

    /**
     * 加载 templates/ 目录下所有 .md 文件
     */
    private static String loadTemplates() {
        return loadDirectory(BASE_PATH + "templates/", "## TASK TEMPLATES\n\n");
    }

    /**
     * 从 classpath 加载单个资源文件
     */
    private static String loadResource(String path) {
        try (InputStream is = ResourcePromptLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                logger.fine("Resource not found: " + path);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load resource: " + path, e);
            return null;
        }
    }

    /**
     * 加载目录下所有 .md 文件（通过已知文件名列表）
     * 注意：由于 classpath 不支持列出目录，这里使用已知文件名列表
     */
    private static String loadDirectory(String dirPath, String header) {
        // 由于 classpath 无法列出目录内容，这里通过已知文件名尝试加载
        // protocols 目录
        String[] knownProtocols = {
            "01-tool-calling.md",
            "02-error-recovery.md",
            "03-task-decomposition.md",
            "04-report-format.md",
            "05-interruption.md"
        };

        // roles 目录
        String[] knownRoles = {
            "orchestrator.md",
            "coder.md",
            "tester.md",
            "reviewer.md",
            "debug.md",
            "explorer.md",
            "architect.md",
            "doc.md"
        };

        // templates 目录
        String[] knownTemplates = {
            "feature-dev.md",
            "bug-fix.md",
            "refactoring.md",
            "code-review.md"
        };

        String[] filesToLoad;
        if (dirPath.contains("protocols")) {
            filesToLoad = knownProtocols;
        } else if (dirPath.contains("roles")) {
            filesToLoad = knownRoles;
        } else if (dirPath.contains("templates")) {
            filesToLoad = knownTemplates;
        } else {
            return null;
        }

        StringBuilder sb = new StringBuilder(header);
        for (String fileName : filesToLoad) {
            String content = loadResource(dirPath + fileName);
            if (content != null) {
                sb.append(content).append("\n\n");
            }
        }

        return sb.length() > header.length() ? sb.toString() : null;
    }

    /**
     * 检查 classpath 资源是否存在
     */
    public static boolean resourceExists(String path) {
        return ResourcePromptLoader.class.getClassLoader().getResource(path) != null;
    }

    /**
     * 检查 system-prompt 资源目录是否存在
     */
    public static boolean promptResourcesExist() {
        return resourceExists(BASE_PATH + "core.md");
    }
}
