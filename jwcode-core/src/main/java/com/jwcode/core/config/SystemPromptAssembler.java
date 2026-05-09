package com.jwcode.core.config;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SystemPromptAssembler — 将系统提示词作为一等架构构件自动组装。
 *
 * <p>加载顺序：</p>
 * <ol>
 *   <li>{@code core.md} — AI 身份定义</li>
 *   <li>{@code rules.md} — 行为规则</li>
 *   <li>{@code protocols/*.md} — 协议定义（工具调用、错误恢复、结束条件等）</li>
 *   <li>工具自描述 — 从 {@link ToolRegistry} 遍历所有工具，聚合 {@link Tool#getPrompt()}</li>
 *   <li>环境信息 — 动态注入当前 OS、时间、工作目录</li>
 * </ol>
 *
 * <p>关键思想：系统提示词不是静态配置，而是代码的自描述投影。
 * 新增工具 → 自动更新 AI 可读的文档，无需手动维护。</p>
 */
public class SystemPromptAssembler {

    private static final Logger logger = Logger.getLogger(SystemPromptAssembler.class.getName());

    private final Path promptDir;
    private final ToolRegistry toolRegistry;

    public SystemPromptAssembler(ToolRegistry toolRegistry) {
        this(getDefaultPromptDir(), toolRegistry);
    }

    public SystemPromptAssembler(Path promptDir, ToolRegistry toolRegistry) {
        this.promptDir = promptDir;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 组装完整系统提示词
     */
    public String assemble() {
        StringBuilder sb = new StringBuilder();

        // 1. 核心身份
        sb.append(loadSection("core.md", "# JwCode System Prompt — AI-Native Software Engineering\n\n"));

        // 2. 行为规则
        String rules = loadSection("rules.md", "");
        if (!rules.isEmpty()) {
            sb.append("\n").append(rules);
        }

        // 3. 协议定义
        String protocols = loadProtocols();
        if (!protocols.isEmpty()) {
            sb.append("\n").append(protocols);
        }

        // 4. 工具自描述
        if (toolRegistry != null) {
            sb.append("\n").append(assembleToolDescriptions());
        }

        // 5. 环境信息
        sb.append("\n\n").append(getEnvironmentInfo());

        return sb.toString();
    }

    /**
     * 从文件加载某一部分；文件不存在则返回 fallback
     */
    private String loadSection(String fileName, String fallback) {
        Path path = promptDir.resolve(fileName);
        if (!Files.exists(path)) {
            return fallback;
        }
        try {
            String content = Files.readString(path);
            logger.fine("Loaded prompt section: " + fileName + " (" + content.length() + " chars)");
            return content;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read prompt section: " + path, e);
            return fallback;
        }
    }

    /**
     * 加载 protocols/ 目录下所有 .md 文件并按文件名排序
     */
    private String loadProtocols() {
        Path protocolsDir = promptDir.resolve("protocols");
        if (!Files.exists(protocolsDir) || !Files.isDirectory(protocolsDir)) {
            return "";
        }
        try (Stream<Path> paths = Files.list(protocolsDir)) {
            List<Path> mdFiles = paths
                .filter(p -> p.toString().endsWith(".md"))
                .sorted()
                .collect(Collectors.toList());

            if (mdFiles.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder("## PROTOCOLS\n\n");
            for (Path md : mdFiles) {
                String content = Files.readString(md);
                sb.append(content).append("\n\n");
            }
            return sb.toString();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read protocols directory: " + protocolsDir, e);
            return "";
        }
    }

    /**
     * 自动聚合所有工具的自描述
     */
    private String assembleToolDescriptions() {
        List<Tool<?, ?, ?>> tools = toolRegistry.getAllTools();
        if (tools.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## AVAILABLE TOOLS (").append(tools.size()).append(")\n\n");
        sb.append("You have access to the following tools. Use them wisely.\n\n");

        for (Tool<?, ?, ?> tool : tools) {
            String name = tool.getName();
            String desc = tool.getDescription();
            String prompt = tool.getPrompt();

            sb.append("### ").append(name).append("\n");
            if (desc != null && !desc.isEmpty()) {
                sb.append(desc).append("\n");
            }
            if (prompt != null && !prompt.equals(desc) && !prompt.isEmpty()) {
                sb.append(prompt).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取动态环境信息（委托给 SystemPromptLoader）
     */
    private String getEnvironmentInfo() {
        return SystemPromptLoader.getEnvironmentInfo();
    }

    /**
     * 获取默认提示词目录
     */
    public static Path getDefaultPromptDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".jwcode", "system-prompt");
    }
}
