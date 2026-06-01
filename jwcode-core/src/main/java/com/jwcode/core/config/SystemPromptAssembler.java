package com.jwcode.core.config;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SystemPromptAssembler — 优先级系统提示词组装器（v3.0 对标 Claude Code）。
 *
 * <p>对标 Claude Code 的 buildEffectiveSystemPrompt()，采用优先级分层：
 * <ol>
 *   <li><b>Override (最高优先级)</b> — 完全替换所有其他 prompt</li>
 *   <li><b>Coordinator</b> — 多 Agent 协调模式</li>
 *   <li><b>Agent</b> — 特定 Agent 角色 prompt</li>
 *   <li><b>Custom</b> — 用户自定义 system prompt</li>
 *   <li><b>Default (最低优先级)</b> — 标准 JWCode prompt</li>
 * </ol>
 * 外加 append — 始终追加到末尾（override 除外）。</p>
 *
 * <h3>组装顺序</h3>
 * <pre>
 *   1. core.md     — AI 身份定义 + Orchestrator 指令
 *   2. rules.md    — 行为规则 (Anti-Slop, 确定性工程, 反编造铁律)
 *   3. protocols/* — 协议定义 (工具调用, 错误恢复, 任务分解, 报告格式)
 *   4. roles/{agent}.md — Agent 角色 prompt
 *   5. tools 自描述 — 从 ToolRegistry 遍历聚合 Tool.getPrompt()
 *   6. memory types — 四种记忆类型定义 + 保存规则 (对标 Claude Code memoryTypes.ts)
 *   7. 环境信息 — OS, 时间, 工作目录, Git 状态
 * </pre>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public class SystemPromptAssembler {

    private static final Logger logger = Logger.getLogger(SystemPromptAssembler.class.getName());

    /** 组装优先级 */
    public enum Priority {
        OVERRIDE,   // 替换所有
        AGENT,       // Agent 角色 prompt
        CUSTOM,      // 用户自定义
        DEFAULT      // 标准 prompt
    }

    private final Path promptDir;
    private final ToolRegistry toolRegistry;
    private final String workingDirectory;

    /** 段落级缓存（可为 null 以禁用缓存） */
    private PromptSectionCache sectionCache;

    // ==================== 可注入组件 ====================

    private String overrideSystemPrompt;
    private String agentSystemPrompt;
    private String customSystemPrompt;
    private List<String> defaultSystemPrompt;
    private String appendSystemPrompt;
    private boolean includeMemoryTypes = true;
    private boolean includeSessionMemory = true;
    private boolean includeTools = true;
    private List<String> claudeMdContents = Collections.emptyList();
    private Map<String, String> memoryManifest = Collections.emptyMap();

    // ==================== 构造函数 ====================

    public SystemPromptAssembler(ToolRegistry toolRegistry) {
        this(getDefaultPromptDir(), toolRegistry, null);
    }

    public SystemPromptAssembler(Path promptDir, ToolRegistry toolRegistry) {
        this(promptDir, toolRegistry, null);
    }

    public SystemPromptAssembler(Path promptDir, ToolRegistry toolRegistry, String workingDirectory) {
        this.promptDir = promptDir;
        this.toolRegistry = toolRegistry;
        this.workingDirectory = workingDirectory;
    }

    // ==================== Builder 方法 ====================

    public SystemPromptAssembler withOverride(String override) {
        this.overrideSystemPrompt = override;
        return this;
    }

    public SystemPromptAssembler withAgent(String agentPrompt) {
        this.agentSystemPrompt = agentPrompt;
        return this;
    }

    public SystemPromptAssembler withCustom(String customPrompt) {
        this.customSystemPrompt = customPrompt;
        return this;
    }

    public SystemPromptAssembler withAppend(String appendPrompt) {
        this.appendSystemPrompt = appendPrompt;
        return this;
    }

    public SystemPromptAssembler withClaudeMdContents(List<String> contents) {
        this.claudeMdContents = contents != null ? new ArrayList<>(contents) : Collections.emptyList();
        return this;
    }

    public SystemPromptAssembler withMemoryManifest(Map<String, String> manifest) {
        this.memoryManifest = manifest != null ? new LinkedHashMap<>(manifest) : Collections.emptyMap();
        return this;
    }

    public SystemPromptAssembler includeMemoryTypes(boolean include) {
        this.includeMemoryTypes = include;
        return this;
    }

    public SystemPromptAssembler includeSessionMemory(boolean include) {
        this.includeSessionMemory = include;
        return this;
    }

    public SystemPromptAssembler includeTools(boolean include) {
        this.includeTools = include;
        return this;
    }

    public SystemPromptAssembler withSectionCache(PromptSectionCache cache) {
        this.sectionCache = cache;
        return this;
    }

    // ==================== 组装 ====================

    /**
     * 按优先级组装完整系统提示词。
     * 对标 Claude Code 的 buildEffectiveSystemPrompt()。
     */
    public String assemble() {
        // 0. Override — 最高优先级，完全替换
        if (overrideSystemPrompt != null && !overrideSystemPrompt.isEmpty()) {
            return overrideSystemPrompt;
        }

        StringBuilder sb = new StringBuilder();

        // 确定主体 prompt 来源
        if (agentSystemPrompt != null && !agentSystemPrompt.isEmpty()) {
            sb.append(agentSystemPrompt);
        } else if (customSystemPrompt != null && !customSystemPrompt.isEmpty()) {
            sb.append(customSystemPrompt);
        } else {
            // 默认 assembly
            sb.append(buildDefaultPrompt());
        }

        // 始终追加 append（如果有）
        if (appendSystemPrompt != null && !appendSystemPrompt.isEmpty()) {
            sb.append("\n\n").append(appendSystemPrompt);
        }

        return sb.toString();
    }

    /**
     * 构建默认系统 prompt。
     * 如果配置了 PromptSectionCache，各 section 会通过缓存获取。
     */
    private String buildDefaultPrompt() {
        StringBuilder sb = new StringBuilder();

        // 1. 核心身份
        if (sectionCache != null) {
            String coreVT = getFileVersionToken("core.md");
            sb.append(sectionCache.getOrCompute("core", coreVT, 0,
                () -> loadSection("core.md", defaultCorePrompt())));
        } else {
            sb.append(loadSection("core.md", defaultCorePrompt()));
        }

        // 2. 行为规则
        if (sectionCache != null) {
            String rulesVT = getFileVersionToken("rules.md");
            String rules = sectionCache.getOrCompute("rules", rulesVT, 0,
                () -> loadSection("rules.md", ""));
            if (!rules.isEmpty()) {
                sb.append("\n\n").append(rules);
            }
        } else {
            String rules = loadSection("rules.md", "");
            if (!rules.isEmpty()) {
                sb.append("\n\n").append(rules);
            }
        }

        // 3. CLAUDE.md 内容
        if (!claudeMdContents.isEmpty()) {
            String claudeMdVT = String.valueOf(claudeMdContents.hashCode());
            String claudeMdSection;
            if (sectionCache != null) {
                claudeMdSection = sectionCache.getOrCompute("claudeMd", claudeMdVT, 0, () -> {
                    StringBuilder csb = new StringBuilder();
                    csb.append("# Project Instructions (CLAUDE.md)\n\n");
                    csb.append("Contents of CLAUDE.md (project instructions, checked into the codebase):\n\n");
                    for (String content : claudeMdContents) {
                        csb.append(content).append("\n\n");
                    }
                    return csb.toString();
                });
            } else {
                StringBuilder csb = new StringBuilder();
                csb.append("# Project Instructions (CLAUDE.md)\n\n");
                csb.append("Contents of CLAUDE.md (project instructions, checked into the codebase):\n\n");
                for (String content : claudeMdContents) {
                    csb.append(content).append("\n\n");
                }
                claudeMdSection = csb.toString();
            }
            sb.append("\n\n").append(claudeMdSection);
        }

        // 4. 协议定义
        String protocolsVT = getProtocolsVersionToken();
        if (sectionCache != null) {
            String protocols = sectionCache.getOrCompute("protocols", protocolsVT, 0,
                this::loadProtocols);
            if (!protocols.isEmpty()) {
                sb.append("\n\n").append(protocols);
            }
        } else {
            String protocols = loadProtocols();
            if (!protocols.isEmpty()) {
                sb.append("\n\n").append(protocols);
            }
        }

        // 5. 工具自描述
        if (includeTools && toolRegistry != null) {
            String toolsVT = getToolsVersionToken();
            if (sectionCache != null) {
                String toolDesc = sectionCache.getOrCompute("tools", toolsVT, 0,
                    this::assembleToolDescriptions);
                if (!toolDesc.isEmpty()) {
                    sb.append("\n\n").append(toolDesc);
                }
            } else {
                String toolDesc = assembleToolDescriptions();
                if (!toolDesc.isEmpty()) {
                    sb.append("\n\n").append(toolDesc);
                }
            }
        }

        // 6. 记忆类型定义（静态常量，永不过期）
        if (includeMemoryTypes) {
            if (sectionCache != null) {
                String memTypes = sectionCache.getOrCompute("memoryTypes", "static", 0,
                    () -> MEMORY_TYPES_PROMPT);
                sb.append("\n\n").append(memTypes);
            } else {
                sb.append("\n\n").append(MEMORY_TYPES_PROMPT);
            }
        }

        // 7. 记忆清单（如果有）
        if (!memoryManifest.isEmpty()) {
            String manifestVT = String.valueOf(memoryManifest.hashCode());
            String manifestSection;
            if (sectionCache != null) {
                manifestSection = sectionCache.getOrCompute("memoryManifest", manifestVT, 0, () -> {
                    StringBuilder msb = new StringBuilder();
                    msb.append("# Memory Manifest\n\n");
                    msb.append("Current memory files available:\n\n");
                    for (var entry : memoryManifest.entrySet()) {
                        msb.append("- [").append(entry.getKey()).append("] — ").append(entry.getValue()).append("\n");
                    }
                    return msb.toString();
                });
            } else {
                StringBuilder msb = new StringBuilder();
                msb.append("# Memory Manifest\n\n");
                msb.append("Current memory files available:\n\n");
                for (var entry : memoryManifest.entrySet()) {
                    msb.append("- [").append(entry.getKey()).append("] — ").append(entry.getValue()).append("\n");
                }
                manifestSection = msb.toString();
            }
            sb.append("\n\n").append(manifestSection);
        }

        // 8. 环境信息（30s TTL）
        if (sectionCache != null) {
            sb.append("\n\n").append(sectionCache.getEnvironment("env", this::getEnvironmentInfo));
        } else {
            sb.append("\n\n").append(getEnvironmentInfo());
        }

        return sb.toString();
    }

    // ==================== 版本 Token 计算 ====================

    /**
     * 获取文件的版本 token（基于 mtime）。
     */
    private String getFileVersionToken(String fileName) {
        try {
            Path path = promptDir.resolve(fileName);
            if (Files.exists(path)) {
                return fileName + "@" + Files.getLastModifiedTime(path).toMillis();
            }
        } catch (IOException ignored) {}
        return fileName + "@missing";
    }

    /**
     * 获取 protocols 目录的版本 token（基于目录中最大 mtime）。
     */
    private String getProtocolsVersionToken() {
        try {
            Path dir = promptDir.resolve("protocols");
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                long maxMtime = 0;
                try (var paths = Files.list(dir)) {
                    for (Path p : paths.filter(p -> p.toString().endsWith(".md")).toList()) {
                        long mtime = Files.getLastModifiedTime(p).toMillis();
                        if (mtime > maxMtime) maxMtime = mtime;
                    }
                }
                return "protocols@" + maxMtime;
            }
        } catch (IOException ignored) {}
        return "protocols@missing";
    }

    /**
     * 获取工具的版本 token（基于工具数量 + 名称列表）。
     */
    private String getToolsVersionToken() {
        if (toolRegistry == null) return "tools@none";
        var tools = toolRegistry.getAllTools();
        return "tools@" + tools.size() + "@" + tools.stream()
            .map(t -> t.getName()).sorted().reduce("", (a, b) -> a + "," + b).hashCode();
    }

    // ==================== 文件加载 ====================

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

            if (mdFiles.isEmpty()) return "";

            StringBuilder sb = new StringBuilder("## PROTOCOLS\n\n");
            for (Path md : mdFiles) {
                String content = Files.readString(md);
                sb.append(content).append("\n\n");
            }
            return sb.toString();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read protocols directory", e);
            return "";
        }
    }

    // ==================== 工具自描述 ====================

    private String assembleToolDescriptions() {
        List<Tool<?, ?, ?>> tools = toolRegistry.getAllTools();
        if (tools.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## Tools\n\n");

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

    // ==================== 环境信息 ====================

    private String getEnvironmentInfo() {
        if (workingDirectory != null) {
            return SystemPromptLoader.getEnvironmentInfo(workingDirectory);
        }
        return SystemPromptLoader.getEnvironmentInfo();
    }

    // ==================== 默认核心 prompt ====================

    private String defaultCorePrompt() {
        return """
            # System

            You are an AI coding assistant powered by JWCode. You are paired with a user who is working on a software engineering project. Your task is to help them write, debug, and understand code.

            All text you output is displayed to the user. Use Github-flavored markdown for formatting.

            # Rules

            - Follow the user's instructions carefully
            - Prefer editing existing files to creating new ones
            - Be careful not to introduce security vulnerabilities (command injection, XSS, SQL injection)
            - Don't add features, refactor, or introduce abstractions beyond what the task requires
            - Default to writing no comments unless the WHY is non-obvious
            - For UI changes, start the dev server and test before reporting complete
            """;
    }

    // ==================== 公共常量 ====================

    /**
     * 四种记忆类型定义（对标 Claude Code MEMORY_TYPES + TYPES_SECTION）。
     * 注入到系统 prompt 中，使 AI 理解记忆类型分类。
     */
    public static final String MEMORY_TYPES_PROMPT = """
        ## Types of memory

        There are several discrete types of memory that you can store in your memory system:

        <types>
        <type>
            <name>user</name>
            <description>Contain information about the user's role, goals, responsibilities, and knowledge. Great user memories help you tailor your future behavior to the user's preferences and perspective. Your goal in reading and writing these memories is to build up an understanding of who the user is and how you can be most helpful to them specifically. For example, you should collaborate with a senior software engineer differently than a student who is coding for the very first time. Keep in mind, that the aim here is to be helpful to the user. Avoid writing memories about the user that could be viewed as a negative judgement or that are not relevant to the work you're trying to accomplish together.</description>
            <when_to_save>When you learn any details about the user's role, preferences, responsibilities, or knowledge</when_to_save>
            <how_to_use>When your work should be informed by the user's profile or perspective. For example, if the user is asking you to explain a part of the code, you should answer that question in a way that is tailored to the specific details that they will find most valuable or that helps them build their mental model in relation to domain knowledge they already have.</how_to_use>
        </type>
        <type>
            <name>feedback</name>
            <description>Guidance the user has given you about how to approach work — both what to avoid and what to keep doing. These are a very important type of memory to read and write as they allow you to remain coherent and responsive to the way you should approach work in the project. Record from failure AND success: if you only save corrections, you will avoid past mistakes but drift away from approaches the user has already validated, and may grow overly cautious.</description>
            <when_to_save>Any time the user corrects your approach or confirms a non-obvious approach worked. Corrections are easy to notice; confirmations are quieter — watch for them. Include *why* so you can judge edge cases later.</when_to_save>
            <how_to_use>Let these memories guide your behavior so that the user does not need to offer the same guidance twice.</how_to_use>
        </type>
        <type>
            <name>project</name>
            <description>Information that you learn about ongoing work, goals, initiatives, bugs, or incidents within the project that is not otherwise derivable from the code or git history. Project memories help you understand the broader context and motivation behind the work the user is doing within this working directory.</description>
            <when_to_save>When you learn who is doing what, why, or by when. Always convert relative dates to absolute dates when saving.</when_to_save>
            <how_to_use>Use these memories to more fully understand the details and nuance behind the user's request and make better informed suggestions.</how_to_use>
        </type>
        <type>
            <name>reference</name>
            <description>Stores pointers to where information can be found in external systems. These memories allow you to remember where to look to find up-to-date information outside of the project directory.</description>
            <when_to_save>When you learn about resources in external systems and their purpose.</when_to_save>
            <how_to_use>When the user references an external system or information that may be in an external system.</how_to_use>
        </type>
        </types>

        ## What NOT to save in memory

        - Code patterns, conventions, architecture, file paths, or project structure — these can be derived by reading the current project state.
        - Git history, recent changes, or who-changed-what — `git log` / `git blame` are authoritative.
        - Debugging solutions or fix recipes — the fix is in the code; the commit message has the context.
        - Anything already documented in CLAUDE.md files.
        - Ephemeral task details: in-progress work, temporary state, current conversation context.

        These exclusions apply even when the user explicitly asks you to save. If they ask you to save a PR list or activity summary, ask what was *surprising* or *non-obvious* about it — that is the part worth keeping.

        ## How to save memories

        Saving a memory is a two-step process:

        **Step 1** — write the memory to its own file (e.g., `user_role.md`, `feedback_testing.md`) using frontmatter format:
        ```
        ---
        name: {{short-slug}}
        description: {{one-line summary}}
        type: {{user|feedback|project|reference}}
        ---

        {{memory content}}
        ```

        **Step 2** — add a pointer to that file in `MEMORY.md`. Each entry should be one line: `- [Title](file.md) — one-line hook`.

        - `MEMORY.md` is always loaded into context — keep the index concise
        - Keep name, description, type fields up-to-date
        - Update or remove stale memories
        - Do not write duplicate memories

        ## Before recommending from memory

        A memory that names a specific function, file, or flag is a claim that it existed *when the memory was written*. It may have been renamed, removed, or never merged. Before recommending it:
        - If the memory names a file path: check the file exists.
        - If the memory names a function or flag: grep for it.
        - If the user is about to act on your recommendation, verify first.

        "The memory says X exists" is not the same as "X exists now."

        Memory records can become stale over time. Use memory as context for what was true at a given point in time. Before answering the user or building assumptions based solely on information in memory records, verify that the memory is still correct and up-to-date by reading the current state of the files or resources. If a recalled memory conflicts with current information, trust what you observe now — and update or remove the stale memory rather than acting on it.
        """;

    /**
     * 默认的 prompt 目录。
     */
    public static Path getDefaultPromptDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".jwcode", "system-prompt");
    }
}
