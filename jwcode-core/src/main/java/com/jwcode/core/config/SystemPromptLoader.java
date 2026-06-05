package com.jwcode.core.config;

import com.jwcode.core.tool.ToolRegistry;

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
 * SystemPromptLoader - 系统提示词加载器（v1.0 兼容门面）。
 *
 * <p><b>Deprecated:</b> 新代码请直接使用 {@link SystemPromptAssembler}。
 * 此类保留作为向后兼容的薄委托层。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 * @deprecated Use {@link SystemPromptAssembler} for v4.0+ prompt assembly.
 */
@Deprecated
public class SystemPromptLoader {
    
    private static final Logger logger = Logger.getLogger(SystemPromptLoader.class.getName());
    
    // 系统提示词文件名
    private static final String PROMPT_FILE_NAME = "system-prompt.md";
    
    // 缓存的系统提示词
    private static String cachedPrompt = null;
    private static long lastModified = 0;
    
    /**
     * 获取系统提示词（包含动态环境信息，向后兼容）
     *
     * @return 系统提示词内容，如果无法加载则返回默认提示词
     */
    public static String getSystemPrompt() {
        return getSystemPrompt(null, null);
    }

    /**
     * 获取系统提示词（支持工具自描述聚合，并指定工作目录）
     *
     * @param toolRegistry 工具注册表，用于聚合工具自描述；可为 null
     * @param workingDirectory 指定的工作目录，用于环境信息注入
     * @return 系统提示词内容
     */
    public static String getSystemPrompt(ToolRegistry toolRegistry, String workingDirectory) {
        Path promptDir = SystemPromptAssembler.getDefaultPromptDir();
        if (Files.exists(promptDir) && Files.isDirectory(promptDir)) {
            SystemPromptAssembler assembler = new SystemPromptAssembler(promptDir, toolRegistry, workingDirectory);
            return assembler.assemble();
        }
        String basePrompt = getBasePrompt();
        String envInfo = getEnvironmentInfo(workingDirectory);
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
        cachedPrompt = """
            # System

            You are JWCode, an AI coding agent with senior engineering judgment.
            You and the user share one workspace. Read before writing, follow existing
            patterns, and verify your work before claiming it is done.

            Use Github-flavored markdown for formatting.
            """;
        logger.info("使用内置默认系统提示词 (" + cachedPrompt.length() + " 字符)");
        return cachedPrompt;
    }
    
    /**
     * 获取动态环境信息
     *
     * @return 包含系统版本、当前时间、工作目录等的环境信息字符串
     */
    public static String getEnvironmentInfo() {
        return getEnvironmentInfo(System.getProperty("user.dir", "Unknown"));
    }

    /**
     * 获取动态环境信息（使用指定的工作目录）
     *
     * @param workingDirectory 指定的工作目录路径
     * @return 包含系统版本、当前时间、工作目录等的环境信息字符串
     */
    public static String getEnvironmentInfo(String workingDirectory) {
        String osName = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        String dir = workingDirectory != null ? workingDirectory : System.getProperty("user.dir", "Unknown");
        String javaVersion = System.getProperty("java.version", "Unknown");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        String currentTime = formatter.format(Instant.now());

        return """
            [Environment Information]
            Operating System: %s %s (%s)
            Java Version: %s
            Current Time: %s
            Working Directory: %s

            You are allowed to change the working directory when needed (e.g., via Shell/BashTool cwd parameter or cd commands).
            """.formatted(osName, osVersion, osArch, javaVersion, currentTime, dir);
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
