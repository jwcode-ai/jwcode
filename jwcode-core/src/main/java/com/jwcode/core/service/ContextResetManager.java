package com.jwcode.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.model.HandoffArtifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * ContextResetManager — 上下文重置管理器。
 *
 * <p>实现"进程级重启 + 结构化交接"的 Context Reset 协议。
 * 当 Agent 上下文接近上限时，不压缩而是杀死旧 Agent、生成交接文档、启动新 Agent。</p>
 *
 * <p>触发条件：</p>
 * <ul>
 *   <li><b>Token 阈值</b>：使用率 > 85%（硬阈值）</li>
 *   <li><b>迭代轮数</b>：迭代循环超过 3 轮（软阈值）</li>
 *   <li><b>阶段切换</b>：任务阶段切换（如从实现切到测试）</li>
 *   <li><b>显式请求</b>：Agent 主动请求重置</li>
 * </ul>
 *
 * <p>重置流程：</p>
 * <pre>
 * 1. 检测到需要重置
 * 2. 冻结当前 Agent 状态
 * 3. 提取 HandoffArtifact（结构化）
 * 4. 保存到文件系统（.jwcode/handoff/{sessionId}_{taskId}.json）
 * 5. 通知 Orchestrator
 * 6. Orchestrator 启动新 Agent 实例
 * 7. 新 Agent 读取 HandoffArtifact 恢复状态
 * 8. 旧 Agent 上下文释放
 * </pre>
 */
public class ContextResetManager {

    private static final Logger logger = Logger.getLogger(ContextResetManager.class.getName());

    /** Token 使用率硬阈值（超过此值触发重置） */
    public static final double TOKEN_THRESHOLD = 0.85;

    /** 迭代轮数软阈值（超过此值建议重置） */
    public static final int ITERATION_THRESHOLD = 3;

    /** 交接文档存储目录 */
    private static final String HANDOFF_DIR = ".jwcode/handoff";

    private final ObjectMapper objectMapper;
    private Path handoffBasePath;
    private boolean initialized = false;

    public ContextResetManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.handoffBasePath = null;
        this.initialized = false;
        logger.info("[ContextResetManager] 延迟初始化模式: 需要调用 setWorkspaceRoot 完成初始化");
    }

    public ContextResetManager(Path workspaceRoot) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        initWithRoot(workspaceRoot);
    }

    /**
     * 使用工作目录根路径初始化。
     */
    private void initWithRoot(Path workspaceRoot) {
        if (workspaceRoot != null) {
            this.handoffBasePath = workspaceRoot.resolve(HANDOFF_DIR);
            initHandoffDirectory();
            this.initialized = true;
        } else {
            this.handoffBasePath = null;
            this.initialized = false;
        }
    }

    /**
     * 初始化交接文档目录。
     */
    private void initHandoffDirectory() {
        if (handoffBasePath != null) {
            try {
                Files.createDirectories(handoffBasePath);
                logger.info("[ContextResetManager] 交接文档目录: " + handoffBasePath);
            } catch (IOException e) {
                logger.warning("[ContextResetManager] 无法创建交接文档目录: " + e.getMessage());
            }
        }
    }

    /**
     * 设置工作目录根路径。
     */
    public void setWorkspaceRoot(Path workspaceRoot) {
        initWithRoot(workspaceRoot);
    }

    /**
     * 检查是否已初始化。
     */
    private void checkInitialized() {
        if (!initialized || handoffBasePath == null) {
            throw new IllegalStateException(
                "ContextResetManager 未初始化: 请先调用 setWorkspaceRoot(Path) 设置工作目录根路径");
        }
    }

    // ==================== 触发条件检查 ====================

    /**
     * 检查是否需要触发 Context Reset。
     *
     * @param tokenUsageRatio Token 使用率（0.0 - 1.0）
     * @param iterationCount  当前迭代轮数
     * @param phaseChanged    是否发生阶段切换
     * @param agentRequested  Agent 是否主动请求重置
     * @return 重置原因（null 表示不需要重置）
     */
    public HandoffArtifact.ResetReason checkResetNeeded(double tokenUsageRatio,
                                                          int iterationCount,
                                                          boolean phaseChanged,
                                                          boolean agentRequested) {
        if (tokenUsageRatio >= TOKEN_THRESHOLD) {
            logger.info("[ContextResetManager] Token 阈值触发: " + String.format("%.1f", tokenUsageRatio * 100) + "%");
            return HandoffArtifact.ResetReason.TOKEN_THRESHOLD_REACHED;
        }
        if (agentRequested) {
            return HandoffArtifact.ResetReason.AGENT_REQUESTED;
        }
        if (iterationCount >= ITERATION_THRESHOLD) {
            logger.info("[ContextResetManager] 迭代轮数触发: " + iterationCount + " 轮");
            return HandoffArtifact.ResetReason.ITERATION_LIMIT_REACHED;
        }
        if (phaseChanged) {
            return HandoffArtifact.ResetReason.PHASE_TRANSITION;
        }
        return null;
    }

    // ==================== 交接文档管理 ====================

    /**
     * 保存交接文档到文件系统。
     *
     * @param artifact 交接文档
     * @return 文件路径
     * @throws IOException 保存失败
     */
    public String saveHandoffArtifact(HandoffArtifact artifact) throws IOException {
        checkInitialized();

        artifact.setCreatedAt(Instant.now());
        String fileName = artifact.getSessionId() + "_" + artifact.getTaskId() + ".json";
        Path filePath = handoffBasePath.resolve(fileName);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), artifact);
        logger.info("[ContextResetManager] 交接文档已保存: " + filePath);

        return filePath.toString();
    }

    /**
     * 从文件系统加载交接文档。
     *
     * @param sessionId 会话 ID
     * @param taskId    任务 ID
     * @return 交接文档
     * @throws IOException 加载失败
     */
    public HandoffArtifact loadHandoffArtifact(String sessionId, String taskId) throws IOException {
        checkInitialized();

        String fileName = sessionId + "_" + taskId + ".json";
        Path filePath = handoffBasePath.resolve(fileName);

        if (!Files.exists(filePath)) {
            throw new IOException("交接文档不存在: " + fileName);
        }

        HandoffArtifact artifact = objectMapper.readValue(filePath.toFile(), HandoffArtifact.class);
        logger.info("[ContextResetManager] 交接文档已加载: " + filePath);

        return artifact;
    }

    /**
     * 删除交接文档。
     */
    public boolean deleteHandoffArtifact(String sessionId, String taskId) {
        if (!initialized || handoffBasePath == null) return false;

        String fileName = sessionId + "_" + taskId + ".json";
        Path filePath = handoffBasePath.resolve(fileName);

        boolean deleted = filePath.toFile().delete();
        if (deleted) {
            logger.info("[ContextResetManager] 交接文档已删除: " + filePath);
        }
        return deleted;
    }

    /**
     * 列出所有交接文档。
     */
    public File[] listHandoffArtifacts() {
        if (!initialized || handoffBasePath == null) return new File[0];

        File dir = handoffBasePath.toFile();
        if (!dir.exists()) return new File[0];

        return dir.listFiles((d, name) -> name.endsWith(".json"));
    }

    // ==================== 重置执行 ====================

    /**
     * 执行完整的 Context Reset 流程。
     *
     * <p>此方法会：</p>
     * <ol>
     *   <li>生成 HandoffArtifact</li>
     *   <li>保存到文件系统</li>
     *   <li>返回 HandoffArtifact 供 Orchestrator 启动新 Agent</li>
     * </ol>
     *
     * @param artifact 交接文档
     * @param reason   重置原因
     * @return 保存后的交接文档路径
     */
    public String executeReset(HandoffArtifact artifact, HandoffArtifact.ResetReason reason) {
        logger.info("[ContextResetManager] 执行 Context Reset: reason=" + reason
            + ", sessionId=" + artifact.getSessionId()
            + ", taskId=" + artifact.getTaskId());

        try {
            // 保存交接文档
            String path = saveHandoffArtifact(artifact);

            // 记录元数据
            artifact.setEnvironmentValue("reset_reason", reason.name());
            artifact.setEnvironmentValue("reset_time", Instant.now().toString());

            logger.info("[ContextResetManager] Context Reset 完成: " + path);
            return path;
        } catch (IOException e) {
            logger.severe("[ContextResetManager] Context Reset 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从交接文档恢复上下文。
     *
     * @param sessionId 会话 ID
     * @param taskId    任务 ID
     * @return 恢复的交接文档（供新 Agent 读取）
     */
    public HandoffArtifact restoreFromReset(String sessionId, String taskId) {
        logger.info("[ContextResetManager] 从交接文档恢复: sessionId=" + sessionId + ", taskId=" + taskId);

        try {
            HandoffArtifact artifact = loadHandoffArtifact(sessionId, taskId);
            logger.info("[ContextResetManager] 恢复完成: phase=" + artifact.getPhase()
                + ", nextActions=" + artifact.getNextActions());
            return artifact;
        } catch (IOException e) {
            logger.severe("[ContextResetManager] 恢复失败: " + e.getMessage());
            return null;
        }
    }
}
