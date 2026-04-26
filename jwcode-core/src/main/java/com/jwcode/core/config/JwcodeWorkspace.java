package com.jwcode.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JwcodeWorkspace — AI 的持久化工作台和记忆库。
 *
 * <p>将 {@code ~/.jwcode/} 从单纯配置目录升级为 AI 的<strong>工作空间</strong>：</p>
 * <ul>
 *   <li>{@code workspace/temp/} — 临时文件、中间结果</li>
 *   <li>{@code workspace/analysis/} — 分析结果缓存（项目指纹、依赖图等）</li>
 *   <li>{@code workspace/patches/} — 待应用/已生成的代码补丁</li>
 *   <li>{@code memory/} — 长期记忆（项目模式、用户偏好、常见问题）</li>
 *   <li>{@code audit/} — 审计日志（工具调用记录、成本追踪）</li>
 *   <li>{@code skills/} — 技能定义</li>
 *   <li>{@code agents/} — Agent 配置</li>
 * </ul>
 *
 * <p>设计原则：AI 可以像人类开发者一样使用"草稿纸"、"笔记本"和"参考手册"。</p>
 */
public class JwcodeWorkspace {

    private static final Logger logger = Logger.getLogger(JwcodeWorkspace.class.getName());
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final Path root;

    public JwcodeWorkspace() {
        this(getDefaultRoot());
    }

    public JwcodeWorkspace(Path root) {
        this.root = root;
        initialize();
    }

    /**
     * 初始化目录结构（幂等）
     */
    public void initialize() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(getWorkspaceTempDir());
            Files.createDirectories(getWorkspaceAnalysisDir());
            Files.createDirectories(getWorkspacePatchesDir());
            Files.createDirectories(getMemoryDir());
            Files.createDirectories(getAuditDir());
            Files.createDirectories(getSkillsDir());
            Files.createDirectories(getAgentsDir());
            logger.fine("JwcodeWorkspace initialized at: " + root);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to initialize workspace directories", e);
        }
    }

    // ==================== 目录访问 ====================

    public Path getRoot() {
        return root;
    }

    public Path getSystemPromptDir() {
        return root.resolve("system-prompt");
    }

    public Path getWorkspaceTempDir() {
        return root.resolve("workspace/temp");
    }

    public Path getWorkspaceAnalysisDir() {
        return root.resolve("workspace/analysis");
    }

    public Path getWorkspacePatchesDir() {
        return root.resolve("workspace/patches");
    }

    public Path getMemoryDir() {
        return root.resolve("memory");
    }

    public Path getAuditDir() {
        return root.resolve("audit");
    }

    public Path getSkillsDir() {
        return root.resolve("skills");
    }

    public Path getAgentsDir() {
        return root.resolve("agents");
    }

    // ==================== 工具方法 ====================

    /**
     * 在工作台临时目录创建临时文件
     */
    public Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(getWorkspaceTempDir(), prefix, suffix);
    }

    /**
     * 写入分析结果缓存
     */
    public void writeAnalysis(String name, String content) throws IOException {
        Path path = getWorkspaceAnalysisDir().resolve(name);
        Files.writeString(path, content);
    }

    /**
     * 读取分析结果缓存
     */
    public String readAnalysis(String name) throws IOException {
        Path path = getWorkspaceAnalysisDir().resolve(name);
        if (!Files.exists(path)) {
            return null;
        }
        return Files.readString(path);
    }

    /**
     * 写入审计日志（追加模式，JSON Lines 格式）
     */
    public void appendAudit(String eventType, String jsonPayload) {
        Path auditFile = getAuditDir().resolve("tool-calls.jsonl");
        String line = String.format("{\"timestamp\":\"%s\",\"type\":\"%s\",\"payload\":%s}%n",
            TIMESTAMP.format(Instant.now()), eventType, jsonPayload);
        try {
            Files.writeString(auditFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write audit log", e);
        }
    }

    /**
     * 读取长期记忆文件
     */
    public String readMemory(String name) {
        Path path = getMemoryDir().resolve(name + ".md");
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read memory: " + path, e);
            return null;
        }
    }

    /**
     * 写入长期记忆文件
     */
    public void writeMemory(String name, String content) {
        Path path = getMemoryDir().resolve(name + ".md");
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write memory: " + path, e);
        }
    }

    // ==================== 静态方法 ====================

    public static Path getDefaultRoot() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".jwcode");
    }

    public static JwcodeWorkspace getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        static final JwcodeWorkspace INSTANCE = new JwcodeWorkspace();
    }
}
