package com.jwcode.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * 获取系统提示词
     * 
     * @return 系统提示词内容，如果无法加载则返回默认提示词
     */
    public static String getSystemPrompt() {
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
            You are JwCode, an interactive AI coding assistant running on the user's computer.
            
            Your primary goal is to help users with software engineering tasks:
            - Answer questions and complete tasks safely and efficiently
            - Write, debug, and analyze code
            - Read, edit, and search files
            - Search the web for latest information
            - Manage tasks and track progress
            - Coordinate with subagents when needed
            
            Core principles:
            1. Be helpful and efficient
            2. Stay focused on the task
            3. Keep it simple - don't overcomplicate
            4. Be thorough and check your work
            5. Avoid hallucination - do fact-checking
            
            Safety guidelines:
            - Never access files outside working directory unless instructed
            - Never execute commands requiring elevated privileges unless authorized
            - Never install/delete anything outside working directory without confirmation
            - Be cautious with any system modifications
            
            Output format (REQUIRED):
            You MUST wrap your response in the following format:
            
            <thinking>
            Your internal thinking process, analysis, and reasoning goes here.
            This will not be shown to the user.
            </thinking>
            
            <final>
            Your actual response to the user goes here.
            Only this part will be displayed to the user.
            </final>
            
            Important:
            - Always include both <thinking> and <final> sections
            - The <final> section must contain the complete response to the user
            - If you need to use tools, you can include tool calls after the </final> tag
            - Respond in the same language as the user's query
            """;
    }
    
    /**
     * 获取系统提示词的简要信息（用于日志显示）
     * 
     * @return 提示词来源和长度信息
     */
    public static String getPromptInfo() {
        Path path = getPromptPath();
        if (Files.exists(path)) {
            String prompt = getSystemPrompt();
            return "Loaded from " + path + " (" + (prompt != null ? prompt.length() : 0) + " chars)";
        }
        String prompt = getSystemPrompt();
        return "Using default prompt (" + (prompt != null ? prompt.length() : 0) + " chars)";
    }
}
