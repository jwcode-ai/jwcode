package com.jwcode.core.memory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SessionMemoryService — 会话记忆服务（对标 Claude Code SessionMemory）。
 *
 * <p>维护一个会话级记忆文件（session-memory.md），使用 9 节模板在压缩后
 * 保留关键上下文。对标 Claude Code 的 sessionMemory.ts 和 prompts.ts。</p>
 *
 * <h3>9 节模板结构</h3>
 * <pre>
 * # Session Title — 5-10 词描述性标题
 * # Current State — 当前正在处理的内容
 * # Task specification — 用户要求构建的内容
 * # Files and Functions — 重要文件和函数
 * # Workflow — 常用命令和执行顺序
 * # Errors & Fixes — 错误及修复方法
 * # Codebase and System Documentation — 系统组件及其关系
 * # Learnings — 经验教训
 * # Key results — 用户要求的精确输出
 * # Worklog — 步骤级摘要
 * </pre>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>每节最大 2000 tokens（可配置）</li>
 *   <li>总文件最大 12000 tokens</li>
 *   <li>超限时按节截断，保留 Current State 和 Errors & Fixes 的精度</li>
 *   <li>用户可自定义模板（~/.jwcode/session-memory/template.md）</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public class SessionMemoryService {

    private static final Logger logger = Logger.getLogger(SessionMemoryService.class.getName());

    /** 每节最大 tokens */
    private static final int MAX_SECTION_TOKENS = 2000;
    /** 每节最大字符数（≈ 4 chars/token） */
    private static final int MAX_SECTION_CHARS = MAX_SECTION_TOKENS * 4;
    /** 总文件最大 tokens */
    private static final int MAX_TOTAL_TOKENS = 12000;

    /** 会话记忆文件路径 */
    private final Path notesPath;
    /** 自定义模板（可选） */
    private String customTemplate;
    /** 自定义更新 prompt（可选） */
    private String customUpdatePrompt;

    /**
     * 默认会话记忆模板。
     */
    public static final String DEFAULT_TEMPLATE = """
        # Session Title
        _A short and distinctive 5-10 word descriptive title for the session. Super info dense, no filler_

        # Current State
        _What is actively being worked on right now? Pending tasks not yet completed. Immediate next steps._

        # Task specification
        _What did the user ask to build? Any design decisions or other explanatory context_

        # Files and Functions
        _What are the important files? In short, what do they contain and why are they relevant?_

        # Workflow
        _What bash commands are usually run and in what order? How to interpret their output if not obvious?_

        # Errors & Fixes
        _Errors encountered and how they were fixed. What did the user correct? What approaches failed and should not be tried again?_

        # Codebase and System Documentation
        _What are the important system components? How do they work/fit together?_

        # Learnings
        _What has worked well? What has not? What to avoid? Do not duplicate items from other sections_

        # Key results
        _If the user asked a specific output such as an answer to a question, a table, or other document, repeat the exact result here_

        # Worklog
        _Step by step, what was attempted, done? Very terse summary for each step_
        """;

    // ==================== 构造函数 ====================

    public SessionMemoryService(Path notesPath) {
        this.notesPath = notesPath;
        ensureExists();
    }

    private void ensureExists() {
        try {
            Path parent = notesPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(notesPath)) {
                Files.writeString(notesPath, DEFAULT_TEMPLATE);
            }
        } catch (IOException e) {
            logger.warning("[sessionMemory] 创建文件失败: " + e.getMessage());
        }
    }

    // ==================== 读取 ====================

    /**
     * 读取当前会话记忆内容。
     */
    public String read() {
        try {
            return Files.readString(notesPath);
        } catch (IOException e) {
            logger.warning("[sessionMemory] 读取失败: " + e.getMessage());
            return DEFAULT_TEMPLATE;
        }
    }

    /**
     * 获取用于 compact 截断的会话记忆。
     * 对标 Claude Code 的 truncateSessionMemoryForCompact()。
     *
     * @return truncatedContent 和 wasTruncated 标志
     */
    public TruncationResult readForCompact() {
        String content = read();
        List<String> lines = Arrays.asList(content.split("\n", -1));
        StringBuilder output = new StringBuilder();
        boolean wasTruncated = false;

        StringBuilder currentSection = new StringBuilder();
        String currentHeader = "";

        for (String line : lines) {
            if (line.startsWith("# ")) {
                // 刷新上一节
                SectionFlushResult result = flushSection(currentHeader, currentSection.toString());
                output.append(result.content);
                wasTruncated = wasTruncated || result.wasTruncated;

                currentHeader = line;
                currentSection = new StringBuilder();
            } else {
                if (!currentSection.isEmpty()) currentSection.append("\n");
                currentSection.append(line);
            }
        }

        // 刷新最后一节
        SectionFlushResult result = flushSection(currentHeader, currentSection.toString());
        output.append(result.content);
        wasTruncated = wasTruncated || result.wasTruncated;

        return new TruncationResult(output.toString().trim(), wasTruncated);
    }

    /**
     * 截断结果。
     */
    public record TruncationResult(String content, boolean wasTruncated) {}

    private record SectionFlushResult(String content, boolean wasTruncated) {}

    private SectionFlushResult flushSection(String header, String sectionContent) {
        if (header.isEmpty()) {
            return new SectionFlushResult(sectionContent, false);
        }

        if (sectionContent.length() <= MAX_SECTION_CHARS) {
            return new SectionFlushResult(header + "\n" + sectionContent, false);
        }

        // 截断到 MAX_SECTION_CHARS 字符以内（以换行为边界）
        String[] lines = sectionContent.split("\n", -1);
        StringBuilder truncated = new StringBuilder(header);
        int charCount = 0;
        for (String line : lines) {
            if (charCount + line.length() + 1 > MAX_SECTION_CHARS) break;
            truncated.append("\n").append(line);
            charCount += line.length() + 1;
        }
        truncated.append("\n\n[... section truncated for length ...]");
        return new SectionFlushResult(truncated.toString(), true);
    }

    // ==================== 写入 ====================

    /**
     * 写入会话记忆内容。
     */
    public void write(String content) {
        try {
            Files.writeString(notesPath, content);
        } catch (IOException e) {
            logger.warning("[sessionMemory] 写入失败: " + e.getMessage());
        }
    }

    /**
     * 检查会话记忆是否为空（等于模板）。
     */
    public boolean isEmpty() {
        String content = read().trim();
        String template = (customTemplate != null ? customTemplate : DEFAULT_TEMPLATE).trim();
        return content.equals(template);
    }

    // ==================== 分析 ====================

    /**
     * 分析各节大小。
     *
     * @return sectionHeader → tokenCount
     */
    public Map<String, Long> analyzeSections() {
        String content = read();
        Map<String, Long> sections = new LinkedHashMap<>();
        String[] lines = content.split("\n", -1);

        String currentSection = "";
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# ")) {
                if (!currentSection.isEmpty()) {
                    sections.put(currentSection, estimateTokens(currentContent.toString()));
                }
                currentSection = line;
                currentContent = new StringBuilder();
            } else {
                if (!currentContent.isEmpty()) currentContent.append("\n");
                currentContent.append(line);
            }
        }

        if (!currentSection.isEmpty()) {
            sections.put(currentSection, estimateTokens(currentContent.toString()));
        }

        return sections;
    }

    /**
     * 检查是否有节超限。
     */
    public List<String> getOversizedSections() {
        Map<String, Long> sections = analyzeSections();
        List<String> oversized = new ArrayList<>();
        for (var entry : sections.entrySet()) {
            if (entry.getValue() > MAX_SECTION_TOKENS) {
                oversized.add(String.format("%s: ~%d tokens (limit: %d)",
                    entry.getKey(), entry.getValue(), MAX_SECTION_TOKENS));
            }
        }
        return oversized;
    }

    /**
     * 估算总 tokens。
     */
    public long getTotalTokens() {
        return estimateTokens(read());
    }

    // ==================== 更新 Prompt ====================

    /**
     * 构建会话记忆更新 prompt。
     * 对标 Claude Code 的 buildSessionMemoryUpdatePrompt()。
     */
    public String buildUpdatePrompt() {
        String currentContent = read();
        Map<String, Long> sectionSizes = analyzeSections();
        long totalTokens = getTotalTokens();

        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            IMPORTANT: This message and these instructions are NOT part of the actual user conversation.
            Do NOT include any references to "note-taking" or "session notes extraction" in the notes content.

            Based on the user conversation above (EXCLUDING this note-taking instruction message),
            update the session notes file using the Edit tool.

            <current_notes_content>
            """);
        prompt.append(currentContent);
        prompt.append("""
            </current_notes_content>

            CRITICAL RULES:
            - Maintain exact structure with all sections, headers, and italic descriptions intact
            - NEVER modify or delete section headers (# lines)
            - NEVER modify or delete italic _section descriptions_
            - ONLY update content BELOW each italic description
            - Do NOT add new sections
            - Write DETAILED, INFO-DENSE content with file paths, function names, error messages
            - Keep each section under ~2000 tokens — condense by cycling out less important details
            - Always update "Current State" to reflect the most recent work
            - Focus on actionable, specific information
            """);

        // 添加超限提醒
        boolean isOverBudget = totalTokens > MAX_TOTAL_TOKENS;
        List<String> oversized = getOversizedSections();

        if (isOverBudget) {
            prompt.append(String.format(
                "\nCRITICAL: Total session memory is ~%d tokens (limit: %d). Aggressively condense all sections.\n",
                totalTokens, MAX_TOTAL_TOKENS));
        }

        if (!oversized.isEmpty()) {
            prompt.append("\nOversized sections to condense:\n");
            for (String s : oversized) {
                prompt.append("- ").append(s).append("\n");
            }
        }

        prompt.append("\nUse the Edit tool with file_path: ").append(notesPath).append("\n");
        prompt.append("\nREMEMBER: Use Edit tool and stop. Do not continue after the edits.");

        return prompt.toString();
    }

    // ==================== 模板管理 ====================

    /**
     * 加载自定义模板。
     */
    public void setCustomTemplate(String template) {
        this.customTemplate = template;
    }

    /**
     * 加载自定义更新 prompt。
     */
    public void setCustomUpdatePrompt(String prompt) {
        this.customUpdatePrompt = prompt;
    }

    /**
     * 重置为默认模板。
     */
    public void resetToDefault() {
        write(DEFAULT_TEMPLATE);
    }

    // ==================== 工具方法 ====================

    private long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 4;
    }

    /**
     * 提取所有节标题。
     */
    public static List<String> extractSectionHeaders(String content) {
        List<String> headers = new ArrayList<>();
        Pattern pattern = Pattern.compile("^# (.*)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            headers.add(matcher.group(1));
        }
        return headers;
    }
}
