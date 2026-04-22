package com.jwcode.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SystemPromptLoader - 系统提示词加载器
 * 
 * 功能说明：
 * 从用户配置目录加载系统提示词文件，为 AI 提供角色定义和行为准则。
 * 
 * 加载顺序（优先级从高到低）：
 * 1. C:\Users\<用户名>\.jwcode\system-prompt.md (Windows)
 * 2. ~/.jwcode/system-prompt.md (Linux/Mac)
 * 3. 内置默认提示词
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SystemPromptLoader {
    
    private static final Logger logger = Logger.getLogger(SystemPromptLoader.class.getName());
    
    // 系统提示词文件名
    private static final String PROMPT_FILE_NAME = "system-prompt.md";
    
    // 缓存的系统提示词
    private static String cachedPrompt = null;
    private static long lastModified = 0;
    
    /**
     * 获取系统提示词（包含动态环境信息）
     * 
     * @return 系统提示词内容，如果无法加载则返回默认提示词
     */
    public static String getSystemPrompt() {
        String basePrompt = getBasePrompt();
        String envInfo = getEnvironmentInfo();
        return envInfo + "\n\n" + basePrompt;
    }
    
    /**
     * 获取基础系统提示词（不含环境信息，用于缓存）
     * 
     * @return 基础提示词内容
     */
    private static String getBasePrompt() {
        Path promptPath = getPromptPath();
        
        // 检查缓存是否有效
        if (cachedPrompt != null && Files.exists(promptPath)) {
            try {
                long currentModified = Files.getLastModifiedTime(promptPath).toMillis();
                if (currentModified == lastModified) {
                    return cachedPrompt;
                }
            } catch (IOException e) {
                logger.warning("无法获取文件修改时间: " + e.getMessage());
            }
        }
        
        // 尝试从文件加载
        String prompt = loadFromFile(promptPath);
        if (prompt != null) {
            cachedPrompt = prompt;
            try {
                lastModified = Files.getLastModifiedTime(promptPath).toMillis();
            } catch (IOException e) {
                // 忽略
            }
            logger.info("已从 " + promptPath + " 加载系统提示词 (" + prompt.length() + " 字符)");
            return prompt;
        }
        
        // 使用默认提示词
        cachedPrompt = getDefaultPrompt();
        logger.info("使用内置默认系统提示词 (" + cachedPrompt.length() + " 字符)");
        return cachedPrompt;
    }
    
    /**
     * 获取动态环境信息
     * 
     * @return 包含系统版本、当前时间、工作目录等的环境信息字符串
     */
    public static String getEnvironmentInfo() {
        String osName = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        String workingDir = System.getProperty("user.dir", "Unknown");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        String currentTime = formatter.format(Instant.now());
        
        return """
            [Environment Information]
            Operating System: %s %s (%s)
            Current Time: %s
            Working Directory: %s
            
            You are allowed to change the working directory when needed (e.g., via Shell/BashTool cwd parameter or cd commands).
            """.formatted(osName, osVersion, osArch, currentTime, workingDir);
    }
    
    /**
     * 强制重新加载系统提示词
     * 
     * @return 重新加载的系统提示词
     */
    public static String reloadSystemPrompt() {
        cachedPrompt = null;
        lastModified = 0;
        return getSystemPrompt();
    }
    
    /**
     * 获取系统提示词文件路径
     * 
     * @return 提示词文件路径
     */
    public static Path getPromptPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".jwcode", PROMPT_FILE_NAME);
    }
    
    /**
     * 检查系统提示词文件是否存在
     * 
     * @return true 如果文件存在
     */
    public static boolean promptFileExists() {
        return Files.exists(getPromptPath());
    }
    
    /**
     * 从文件加载提示词
     * 
     * @param path 文件路径
     * @return 文件内容，如果失败返回 null
     */
    private static String loadFromFile(Path path) {
        if (!Files.exists(path)) {
            logger.fine("系统提示词文件不存在: " + path);
            return null;
        }
        
        try {
            return Files.readString(path);
        } catch (IOException e) {
            logger.log(Level.WARNING, "无法读取系统提示词文件: " + path, e);
            return null;
        }
    }
    
    /**
     * 获取默认系统提示词
     * 
     * @return 默认提示词内容
     */
    private static String getDefaultPrompt() {
        return """
            # JwCode System Prompt — AI-Native Software Engineering Specification v2.0

            ## 1. ROLE ANCHORING
            You are JwCode, an expert software engineer employed to deliver production-grade code.
            The user is your Engineering Manager. You deliver shippable artifacts; they provide requirements and make decisions.
            Rules: NEVER act as a "helper". NEVER use filler openers. Output must be "code review ready".

            ## 2. ANTI-SLOP CHECKLIST (STRICTLY FORBIDDEN)
            - Over-apologizing ("I'm sorry") → State facts directly.
            - Emojis in code/comments → Use TODO:/FIXME:/NOTE: markers.
            - Generic filler ("Let's get started") → Skip preamble; start with analysis/action.
            - Inventing paths/APIs → Use Glob/Grep to verify before referencing.
            - Pseudo-code → Deliver compilable code or mark explicit PLACEHOLDER.
            - Over-commenting obvious logic → Comment the "why", not the "what".
            - "latest" dependency versions → Pin exact versions (e.g., 3.1.1).
            - Hallucinated test results → Run tests via Shell; evidence required.
            Core Principle: Placeholders > Poor Implementations.

            ## 3. CONTEXT-FIRST DESIGN
            Before ANY code change:
            1. Read AGENTS.md if it exists.
            2. Read target files with ReadFile/Grep — never edit blindly.
            3. Check existing tests related to the change.
            4. Verify dependency versions in pom.xml.
            5. Inspect adjacent code for style consistency.
            Iron Law: "Mocking a full solution from scratch is a LAST RESORT."
            For non-trivial decisions, present >=3 variants (Conservative / Balanced / Creative).

            ## 4. DETERMINISTIC ENGINEERING
            - Maven dependencies MUST use exact versions; NEVER ranges or LATEST.
            - Reuse existing utilities (Preconditions, StringUtils) instead of reimplementing.
            - Follow existing code style, naming, and architectural patterns.

            ## 5. TWO-STAGE VERIFICATION
            Stage 1 Functional: mvn compile; mvn test -Dtest=ClassName. Fix failures before proceeding.
            Stage 2 Logical Review checklist:
            - Null safety; resource leaks closed (try-with-resources)
            - Concurrency/thread safety documented if applicable
            - Edge cases (empty collections, null inputs, boundaries)
            - Meaningful exceptions; no silent swallowing
            - API compatibility preserved unless intentionally broken
            Stage 1 failure blocks Stage 2. Loop back findings to code changes.

            ## 6. CONTEXT COMPRESSION
            - Use /compact when context >80% of limit.
            - Mark abandoned exploratory branches as deprecated in summary.
            - Prefer Grep/Glob over reading entire large files.

            ## 7. DELIVERY STANDARDS
            - Every file edit MUST be complete and compilable.
            - Include Javadoc for public APIs per project template.
            - New features MUST include tests; bug fixes MUST include regression tests.
            - Summarize changes in commit-ready format.

            ## 8. ENVIRONMENT & SAFETY
            - OS: Windows PowerShell (primary).
            - Working Directory: project root; no external file access without instruction.
            - No elevated privileges without authorization.
            - No system-wide install/delete without confirmation.

            ## 9. OUTPUT FORMAT
            ReAct pattern: Thought → Action.
            End marker when done: [FINISH]
            Language: same as user's query.
            """;
    }
    
    /**
     * 获取系统提示词的简要信息（用于日志显示）
     * 
     * @return 提示词来源和长度信息
     */
    public static String getPromptInfo() {
        Path path = getPromptPath();
        String prompt = getSystemPrompt();
        if (Files.exists(path)) {
            return "Loaded from " + path + " (" + (prompt != null ? prompt.length() : 0) + " chars)";
        }
        return "Using default prompt (" + (prompt != null ? prompt.length() : 0) + " chars)";
    }
}
