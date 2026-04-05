package com.jwcode.core.advanced.yolo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * YOLO Mode - 全自动执行模式
 * 
 * 参照 Kimi Code 的 YOLO Mode (kimi --yolo)
 * 开启后自动执行命令和修改文件，无需确认
 * 
 * ⚠️ 谨慎使用，建议仅在受控环境中开启
 */
public class YoloModeManager {
    
    private static final Logger log = Logger.getLogger(YoloModeManager.class.getName());
    
    private volatile boolean yoloModeEnabled = false;
    private YoloConfig config;
    private final List<YoloActionRecord> actionHistory = new CopyOnWriteArrayList<>();
    private final List<Predicate<YoloAction>> safetyChecks = new ArrayList<>();
    
    public YoloModeManager() {
        this.config = YoloConfig.defaultConfig();
        initializeSafetyChecks();
    }
    
    /**
     * 切换 YOLO Mode
     */
    public boolean toggle() {
        yoloModeEnabled = !yoloModeEnabled;
        if (yoloModeEnabled) {
            log.warning("[YOLO Mode] ⚠️ 全自动模式已开启 - 将自动执行命令和修改文件！");
        } else {
            log.info("[YOLO Mode] 已关闭 - 回到安全确认模式");
        }
        return yoloModeEnabled;
    }
    
    /**
     * 设置 YOLO Mode 状态
     */
    public void setEnabled(boolean enabled) {
        this.yoloModeEnabled = enabled;
        log.info("[YOLO Mode] " + (enabled ? "开启" : "关闭"));
    }
    
    /**
     * 是否开启 YOLO Mode
     */
    public boolean isEnabled() {
        return yoloModeEnabled;
    }
    
    /**
     * 检查操作是否允许执行
     */
    public boolean canExecute(YoloAction action) {
        if (!yoloModeEnabled) {
            return false; // 非 YOLO 模式需要手动确认
        }
        
        // 运行安全检查
        for (Predicate<YoloAction> check : safetyChecks) {
            if (!check.test(action)) {
                log.warning("[YOLO Mode] 安全检查阻止操作: " + action.getDescription());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 记录执行的操作
     */
    public void recordAction(YoloAction action, boolean success) {
        YoloActionRecord record = new YoloActionRecord(
            System.currentTimeMillis(),
            action,
            success,
            yoloModeEnabled
        );
        actionHistory.add(record);
        
        // 限制历史记录大小
        if (actionHistory.size() > config.getMaxHistorySize()) {
            actionHistory.remove(0);
        }
        
        log.info("[YOLO Mode] 执行操作: " + action.getDescription() + " - " + (success ? "成功" : "失败"));
    }
    
    /**
     * 获取操作历史
     */
    public List<YoloActionRecord> getActionHistory() {
        return new ArrayList<>(actionHistory);
    }
    
    /**
     * 生成执行报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("╔════════════════════════════════════════════════════════╗\n");
        report.append("║           YOLO Mode 执行报告                           ║\n");
        report.append("╚════════════════════════════════════════════════════════╝\n");
        report.append("状态: ").append(yoloModeEnabled ? "🟢 开启" : "🔴 关闭").append("\n");
        report.append("总操作数: ").append(actionHistory.size()).append("\n");
        
        long successCount = actionHistory.stream().filter(YoloActionRecord::isSuccess).count();
        report.append("成功: ").append(successCount).append("\n");
        report.append("失败: ").append(actionHistory.size() - successCount).append("\n");
        
        if (!actionHistory.isEmpty()) {
            report.append("\n最近操作:\n");
            actionHistory.stream()
                .skip(Math.max(0, actionHistory.size() - 5))
                .forEach(r -> report.append(String.format("  %s %s%n",
                    r.isSuccess() ? "✓" : "✗",
                    r.getAction().getDescription())));
        }
        
        return report.toString();
    }
    
    /**
     * 初始化安全检查
     */
    private void initializeSafetyChecks() {
        // 检查 1: 危险命令检测
        safetyChecks.add(action -> {
            if (action.getType() == YoloActionType.COMMAND) {
                String cmd = action.getContent().toLowerCase();
                for (String dangerous : config.getDangerousCommands()) {
                    if (cmd.contains(dangerous)) {
                        log.warning("[YOLO Safety] 检测到危险命令: " + dangerous);
                        return false;
                    }
                }
            }
            return true;
        });
        
        // 检查 2: 系统关键文件保护
        safetyChecks.add(action -> {
            if (action.getType() == YoloActionType.FILE_DELETE || 
                action.getType() == YoloActionType.FILE_MODIFY) {
                String path = action.getTarget().toLowerCase();
                for (String protectedPath : config.getProtectedPaths()) {
                    if (path.contains(protectedPath)) {
                        log.warning("[YOLO Safety] 检测到系统关键文件操作: " + protectedPath);
                        return false;
                    }
                }
            }
            return true;
        });
        
        // 检查 3: 操作频率限制
        safetyChecks.add(action -> {
            long recentActions = actionHistory.stream()
                .filter(r -> System.currentTimeMillis() - r.getTimestamp() < 60000)
                .count();
            if (recentActions > config.getMaxActionsPerMinute()) {
                log.warning("[YOLO Safety] 操作频率过高，已限制");
                return false;
            }
            return true;
        });
    }
    
    public static class YoloConfig {
        private int maxHistorySize = 100;
        private int maxActionsPerMinute = 30;
        private List<String> dangerousCommands = List.of(
            "rm -rf /", "format", "del /f /s /q", "rd /s /q"
        );
        private List<String> protectedPaths = List.of(
            "/etc/passwd", "/etc/shadow", "c:/windows/system32"
        );
        
        public int getMaxHistorySize() { return maxHistorySize; }
        public int getMaxActionsPerMinute() { return maxActionsPerMinute; }
        public List<String> getDangerousCommands() { return dangerousCommands; }
        public List<String> getProtectedPaths() { return protectedPaths; }
        
        public void setMaxHistorySize(int maxHistorySize) { this.maxHistorySize = maxHistorySize; }
        public void setMaxActionsPerMinute(int maxActionsPerMinute) { this.maxActionsPerMinute = maxActionsPerMinute; }
        public void setDangerousCommands(List<String> dangerousCommands) { this.dangerousCommands = dangerousCommands; }
        public void setProtectedPaths(List<String> protectedPaths) { this.protectedPaths = protectedPaths; }
        
        public static YoloConfig defaultConfig() {
            return new YoloConfig();
        }
    }
    
    /**
     * YOLO 操作类型
     */
    public enum YoloActionType {
        COMMAND,        // 执行命令
        FILE_CREATE,    // 创建文件
        FILE_MODIFY,    // 修改文件
        FILE_DELETE,    // 删除文件
        API_CALL        // API 调用
    }
    
    public static class YoloAction {
        private final YoloActionType type;
        private final String description;
        private final String target;
        private final String content;
        
        public YoloAction(YoloActionType type, String description, String target, String content) {
            this.type = type;
            this.description = description;
            this.target = target;
            this.content = content;
        }
        
        public YoloActionType getType() { return type; }
        public String getDescription() { return description; }
        public String getTarget() { return target; }
        public String getContent() { return content; }
    }
    
    public static class YoloActionRecord {
        private final long timestamp;
        private final YoloAction action;
        private final boolean success;
        private final boolean inYoloMode;
        
        public YoloActionRecord(long timestamp, YoloAction action, boolean success, boolean inYoloMode) {
            this.timestamp = timestamp;
            this.action = action;
            this.success = success;
            this.inYoloMode = inYoloMode;
        }
        
        public long getTimestamp() { return timestamp; }
        public YoloAction getAction() { return action; }
        public boolean isSuccess() { return success; }
        public boolean isInYoloMode() { return inYoloMode; }
    }
}
