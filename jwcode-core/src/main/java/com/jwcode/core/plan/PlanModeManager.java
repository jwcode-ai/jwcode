package com.jwcode.core.plan;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolCategory;
import com.jwcode.core.tool.SideEffect;
import com.jwcode.core.permission.PermissionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * PlanModeManager — Plan/Act 模式管理器。
 * 
 * <p>管理 Plan Mode 和 Act Mode 之间的切换，以及 Plan Mode 下的工具权限隔离。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>模式状态管理</b>：plan / act / normal 三种模式的状态机</li>
 *   <li><b>工具白名单</b>：Plan Mode 下只允许只读工具，禁用写工具</li>
 *   <li><b>持久化</b>：模式状态持久化到 .jwcode/state.json</li>
 *   <li><b>事件通知</b>：模式切换时通知监听器</li>
 * </ul>
 * 
 * <h3>Plan Mode 工具白名单</h3>
 * <p>Plan Mode 下只允许以下类别的工具：</p>
 * <ul>
 *   <li>SEARCH — 搜索类（Grep、Glob、WebSearch）</li>
 *   <li>CODE_ANALYSIS — 代码分析类（LSP、语义分析）</li>
 *   <li>METACOGNITION — 元认知类（TodoWrite、AgentTool 等）</li>
 *   <li>COMMUNICATION — 通信类（AskUserQuestion）</li>
 *   <li>READ_ONLY 副作用的工具</li>
 * </ul>
 * 
 * <p>Plan Mode 下禁用的工具：</p>
 * <ul>
 *   <li>FILE_OPERATION — 文件写操作（FileWrite、FileEdit、FileDelete）</li>
 *   <li>EXECUTION — 命令执行（Bash、PowerShell、REPL）</li>
 *   <li>Git 操作</li>
 *   <li>破坏性操作</li>
 * </ul>
 */
public class PlanModeManager {
    
    private static final Logger logger = Logger.getLogger(PlanModeManager.class.getName());
    private static final String STATE_FILE = ".jwcode/state.json";
    
    /** 模式枚举 */
    public enum Mode {
        NORMAL("normal"),
        PLAN("plan"),
        ACT("act");
        
        private final String value;
        
        Mode(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static Mode fromString(String s) {
            for (Mode mode : values()) {
                if (mode.value.equals(s)) {
                    return mode;
                }
            }
            return NORMAL;
        }
    }
    
    /** Plan Mode 下允许的工具类别 */
    private static final Set<ToolCategory> PLAN_MODE_ALLOWED_CATEGORIES = Set.of(
        ToolCategory.SEARCH,
        ToolCategory.CODE_ANALYSIS,
        ToolCategory.METACOGNITION,
        ToolCategory.COMMUNICATION
    );
    
    /** Plan Mode 下允许的副作用类型 */
    private static final Set<SideEffect> PLAN_MODE_ALLOWED_SIDE_EFFECTS = Set.of(
        SideEffect.READ_ONLY
    );
    
    /** Plan Mode 下始终允许的工具名称 */
    private static final Set<String> PLAN_MODE_ALWAYS_ALLOWED_TOOLS = Set.of(
        // "TodoWrite" 已移除：Plan 模式只做计划，不做任何写操作（包括 TodoWrite）
        "AskUserQuestion",
        "SmartAnalyze",
        "ToolSearch",
        "Config"
    );
    
    /** Plan Mode 下始终禁止的工具名称 */
    private static final Set<String> PLAN_MODE_ALWAYS_BLOCKED_TOOLS = Set.of(
        "Bash",
        "PowerShell",
        "REPL",
        "FileWrite",
        "FileEdit",
        "FileDelete",
        "FileCreate",
        "NotebookEdit",
        "Git",
        "RemoteTrigger",
        "ScheduleCron",
        "SendMessage",
        "TeamCreate",
        "TeamDelete",
        "McpAuth",
        // AgentTool 在 Plan 模式下也应禁止 — Plan 模式只做调研不做执行
        // 如果需要创建子 Agent 做调研，应通过 Orchestrator 的只读子 Agent 机制
        "AgentTool",
        "EnterPlanMode",
        "ExitPlanMode",
        "ExitPlanModeV2"
    );
    
    // 单例
    private static volatile PlanModeManager instance;
    
    // 当前模式
    private volatile Mode currentMode = Mode.NORMAL;

    /** Hard lock: prevents direct API calls from bypassing Plan mode isolation. */
    private volatile boolean planModeLocked = false;
    
    // 模式切换监听器
    private final List<ModeChangeListener> listeners = new ArrayList<>();
    
    // 模式切换历史（用于审计）
    private final List<ModeChangeEvent> history = Collections.synchronizedList(new ArrayList<>());
    
    // 状态文件路径
    private final Path stateFilePath;
    
    private PlanModeManager() {
        this.stateFilePath = Paths.get(System.getProperty("user.dir"), STATE_FILE);
        loadMode();
    }
    
    /** 残留状态过期阈值（5分钟） */
    private static final long STALE_THRESHOLD_MS = 300_000;

    /**
     * 残留状态检测：如果状态文件超过阈值且模式为 PLAN 或 ACT，自动重置为 NORMAL。
     * 防止上次会话崩溃/异常退出导致残留的 Plan/Act Mode 影响本次启动。
     *
     * <p>ACT 模式残留尤其危险：autoApproveWrite=true 状态会意外批准文件写入。</p>
     */
    private boolean isStaleMode() {
        if (currentMode == Mode.NORMAL) return false;
        try {
            if (Files.exists(stateFilePath)) {
                long lastModified = Files.getLastModifiedTime(stateFilePath).toMillis();
                long age = System.currentTimeMillis() - lastModified;
                if (age > STALE_THRESHOLD_MS) {
                    logger.warning(currentMode.getValue() + " mode state stale ("
                        + (age / 1000) + "s old), resetting to NORMAL");
                    return true;
                }
            }
        } catch (Exception e) {
            logger.fine("Cannot check state file age: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * 获取单例实例
     */
    public static PlanModeManager getInstance() {
        if (instance == null) {
            synchronized (PlanModeManager.class) {
                if (instance == null) {
                    instance = new PlanModeManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 重置单例（用于测试）
     */
    public static synchronized void resetInstance() {
        instance = null;
    }
    
    // ==================== 模式查询 ====================
    
    /**
     * 获取当前模式
     */
    public Mode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 是否处于 Plan Mode
     */
    public boolean isPlanMode() {
        return currentMode == Mode.PLAN;
    }
    
    /**
     * 是否处于 Act Mode
     */
    /**
     * Check if a tool is allowed in Plan Mode.
     */
    public boolean isToolAllowedInCurrentMode(ToolCategory category, SideEffect sideEffect, String toolName) {
        if (currentMode != Mode.PLAN) return true;
        if (toolName != null && PLAN_MODE_ALWAYS_BLOCKED_TOOLS.contains(toolName)) return false;
        if (toolName != null && PLAN_MODE_ALWAYS_ALLOWED_TOOLS.contains(toolName)) return true;
        if (category != null && PLAN_MODE_ALLOWED_CATEGORIES.contains(category)) return true;
        if (sideEffect != null && PLAN_MODE_ALLOWED_SIDE_EFFECTS.contains(sideEffect)) return true;
        return false;
    }


    public boolean isActMode() {
        return currentMode == Mode.ACT;
    }
    
    /**
     * 是否处于 Normal Mode
     */
    public boolean isNormalMode() {
        return currentMode == Mode.NORMAL;
    }
    
    // ==================== 模式切换 ====================
    
    /**
     * 进入 Plan Mode
     * 
     * @param taskDescription 任务描述
     * @return 是否成功切换
     */
    public synchronized boolean enterPlanMode(String taskDescription) {
        if (currentMode == Mode.PLAN) {
            logger.fine("Already in plan mode");
            return true;
        }
        
        Mode previousMode = currentMode;
        currentMode = Mode.PLAN;
        planModeLocked = true;
        saveMode();
        
        ModeChangeEvent event = new ModeChangeEvent(previousMode, Mode.PLAN, taskDescription);
        history.add(event);
        notifyListeners(event);
        
        logger.info("Entered plan mode: " + taskDescription);
        return true;
    }
    
    /**
     * 退出 Plan Mode 并自动进入 Act Mode
     *
     * @param summary 计划摘要
     * @return 是否成功切换
     */
    public synchronized boolean exitPlanMode(String summary) {
        if (currentMode != Mode.PLAN) {
            logger.fine("Not in plan mode");
            return false;
        }

        Mode previousMode = currentMode;
        currentMode = Mode.ACT;
        planModeLocked = false;
        saveMode();

        ModeChangeEvent event = new ModeChangeEvent(previousMode, Mode.ACT, summary);
        history.add(event);
        notifyListeners(event);

        logger.info("Exited plan mode → Act mode: " + summary);
        return true;
    }
    
    /**
     * 进入 Act Mode
     */
    public synchronized boolean enterActMode() {
        if (currentMode == Mode.PLAN && planModeLocked) {
            logger.warning("Plan mode locked: use ExitPlanModeV2 tool to exit plan mode first.");
            return false;
        }
        Mode previousMode = currentMode;
        currentMode = Mode.ACT;
        planModeLocked = false;
        saveMode();

        // Act 模式下自动批准文件写入，只有 bash/shell 命令需要审批
        PermissionManager.getInstance().setAutoApproveWrite(true);

        ModeChangeEvent event = new ModeChangeEvent(previousMode, Mode.ACT, "Entered act mode");
        history.add(event);
        notifyListeners(event);

        logger.info("Entered act mode (autoApproveWrite=true)");
        return true;
    }
    
    /**
     * 退出 Act Mode
     */
    public synchronized boolean exitActMode() {
        if (currentMode != Mode.ACT) {
            return false;
        }

        Mode previousMode = currentMode;
        currentMode = Mode.NORMAL;
        saveMode();

        // 退出 Act 模式时恢复文件写入审批
        PermissionManager.getInstance().setAutoApproveWrite(false);

        ModeChangeEvent event = new ModeChangeEvent(previousMode, Mode.NORMAL, "Exited act mode");
        history.add(event);
        notifyListeners(event);

        logger.info("Exited act mode (autoApproveWrite=false)");
        return true;
    }
    
    // ==================== 权限检查 ====================
    
    /**
     * 检查工具是否在当前模式下允许执行
     * 
     * @param tool 要检查的工具
     * @param input 工具输入
     * @return 权限检查结果
     */
    public <I> PermissionResult checkToolPermission(Tool<I, ?, ?> tool, I input) {
        // 非 Plan Mode 下，所有工具都允许
        if (currentMode != Mode.PLAN) {
            return PermissionResult.allowed();
        }
        
        String toolName = tool.getName();
        
        // 始终允许的工具
        if (PLAN_MODE_ALWAYS_ALLOWED_TOOLS.contains(toolName)) {
            return PermissionResult.allowed();
        }
        
        // 始终禁止的工具 — 带替代建议
        if (PLAN_MODE_ALWAYS_BLOCKED_TOOLS.contains(toolName)) {
            String suggestion = getReplacementSuggestion(toolName);
            return PermissionResult.denied(
                "工具 '" + toolName + "' 在 Plan Mode 下不可用。Plan Mode 只允许只读操作。" + suggestion
            );
        }
        
        // 检查工具类别
        ToolCategory category = tool.getCategory();
        if (PLAN_MODE_ALLOWED_CATEGORIES.contains(category)) {
            return PermissionResult.allowed();
        }
        
        // 检查副作用
        Set<SideEffect> sideEffects = tool.getSideEffects();
        boolean hasOnlyReadSideEffects = sideEffects.isEmpty() || 
            sideEffects.stream().allMatch(se -> se == SideEffect.READ_ONLY);
        
        if (hasOnlyReadSideEffects) {
            return PermissionResult.allowed();
        }
        
        // 检查工具自身的只读声明
        if (tool.isReadOnly(input)) {
            return PermissionResult.allowed();
        }
        
        return PermissionResult.denied(
            "工具 '" + toolName + "' 在 Plan Mode 下不可用。Plan Mode 只允许只读操作。"
        );
    }
    
    /**
     * 获取 Plan Mode 下可用的工具列表
     */
    public List<Tool<?, ?, ?>> filterPlanModeTools(List<Tool<?, ?, ?>> allTools) {
        if (currentMode != Mode.PLAN) {
            return allTools;
        }
        
        return allTools.stream()
            .filter(tool -> {
                PermissionResult result = checkToolPermission(tool, null);
                return result.isAllowed();
            })
            .toList();
    }
    
    // ==================== 监听器 ====================
    
    /**
     * 添加模式切换监听器
     */
    public void addListener(ModeChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 移除模式切换监听器
     */
    public void removeListener(ModeChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners(ModeChangeEvent event) {
        for (ModeChangeListener listener : listeners) {
            try {
                listener.onModeChanged(event);
            } catch (Exception e) {
                logger.warning("ModeChangeListener error: " + e.getMessage());
            }
        }
    }
    
    // ==================== 持久化 ====================
    
    /**
     * 从文件加载模式
     */
    private void loadMode() {
        try {
            if (Files.exists(stateFilePath)) {
                String content = Files.readString(stateFilePath);
                if (content.contains("\"mode\"")) {
                    int start = content.indexOf("\"mode\"") + 7;
                    int end = content.indexOf("\"", start + 1);
                    if (end > start + 1) {
                        String modeStr = content.substring(start + 1, end);
                        Mode loadedMode = Mode.fromString(modeStr);
                        currentMode = loadedMode;
                        // 残留检测：超过阈值的 PLAN/ACT 状态自动重置
                        if (isStaleMode()) {
                            if (currentMode == Mode.ACT) {
                                PermissionManager.getInstance().setAutoApproveWrite(false);
                            }
                            currentMode = Mode.NORMAL;
                            saveMode();
                        }
                        logger.info("Loaded mode: " + currentMode);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load mode: " + e.getMessage());
        }
    }
    
    /**
     * 保存模式到文件
     */
    private void saveMode() {
        try {
            Files.createDirectories(stateFilePath.getParent());
            String content = "{\"mode\":\"" + currentMode.getValue() + "\"}";
            Files.writeString(stateFilePath, content);
        } catch (Exception e) {
            logger.warning("Failed to save mode: " + e.getMessage());
        }
    }
    
    // ==================== 事件模型 ====================
    
    /**
     * 模式切换事件
     */
    public record ModeChangeEvent(
        Mode previousMode,
        Mode newMode,
        String description,
        long timestamp
    ) {
        public ModeChangeEvent(Mode previousMode, Mode newMode, String description) {
            this(previousMode, newMode, description, System.currentTimeMillis());
        }
    }
    
    /**
     * 模式切换监听器
     */
    @FunctionalInterface
    public interface ModeChangeListener {
        void onModeChanged(ModeChangeEvent event);
    }
    
    /**
     * 权限检查结果
     */
    public static class PermissionResult {
        private final boolean allowed;
        private final String reason;
        
        private PermissionResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static PermissionResult allowed() {
            return new PermissionResult(true, null);
        }
        
        public static PermissionResult denied(String reason) {
            return new PermissionResult(false, reason);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public boolean isDenied() { return !allowed; }
    }
    
    // ==================== 替代工具建议 ====================
    
    /**
     * 判断当前模式是否为"只读调研"模式。
     *
     * <p>Plan 模式下，所有操作都应是只读的。此方法供 Orchestrator 和子 Agent 判断
     * 是否应使用只读策略（如 Explorer 只读调研，而非 Coder 写代码）。</p>
     *
     * @return Plan 模式下返回 true
     */
    public boolean isPlanModeReadOnly() {
        return currentMode == Mode.PLAN;
    }

    /**
     * 获取被禁用工具的替代建议
     */
    private String getReplacementSuggestion(String blockedToolName) {
        return switch (blockedToolName) {
            case "Bash", "PowerShell", "REPL" ->
                "\n💡 替代方案：用 SmartAnalyzeTool 分析项目结构，用 GlobTool 搜索文件，用 FileReadTool 读取文件内容。";
            case "FileWrite", "FileEdit", "FileDelete", "FileCreate" ->
                "\n💡 替代方案：Plan Mode 下不能写文件。先用 FileReadTool 读取现有内容，规划好后再退出 Plan Mode 执行写操作。";
            case "Git" ->
                "\n💡 替代方案：用 GlobTool + FileReadTool 查看文件状态，用 SmartAnalyzeTool 分析项目结构。";
            case "NotebookEdit" ->
                "\n💡 替代方案：用 FileReadTool 读取 notebook 内容进行规划。";
            case "AgentTool" ->
                "\n💡 替代方案：Plan Mode 下不能创建子 Agent 执行任务。用 SmartAnalyzeTool、GlobTool、FileReadTool 等只读工具做调研。";
            default ->
                "\n💡 提示：Plan Mode 只允许只读操作。尝试用 SmartAnalyzeTool、GlobTool、FileReadTool 等只读工具替代。";
        };
    }
    
    // ==================== 历史查询 ====================
    
    /**
     * 获取模式切换历史
     */
    public List<ModeChangeEvent> getHistory() {
        return List.copyOf(history);
    }
    
    /**
     * 获取最近的模式切换事件
     */
    public Optional<ModeChangeEvent> getLastEvent() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.get(history.size() - 1));
    }
}
