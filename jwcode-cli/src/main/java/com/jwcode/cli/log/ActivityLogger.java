package com.jwcode.cli.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * AI 活动日志记录器
 * 
 * 提供类似 KimiCode 的实时活动显示功能：
 * - 显示AI正在执行的操作
 * - 实时进度更新
 * - 活动历史记录
 * - 多种显示模式（紧凑/详细）
 */
public class ActivityLogger {
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private static ActivityLogger instance;
    
    // 配置选项
    private boolean enabled = true;
    private boolean useColor = true;
    private boolean compactMode = true;
    private boolean showTimestamp = true;
    private int maxHistorySize = 100;
    
    // 活动记录
    private final List<ActivityEntry> activityHistory = new CopyOnWriteArrayList<>();
    private final Map<String, ActivityEntry> activeActivities = new ConcurrentHashMap<>();
    
    // 监听器
    private final List<Consumer<ActivityEntry>> activityListeners = new CopyOnWriteArrayList<>();
    
    // 当前活动显示
    private String currentStatusLine = "";
    private boolean isStatusLineDirty = false;
    
    private ActivityLogger() {}
    
    public static synchronized ActivityLogger getInstance() {
        if (instance == null) {
            instance = new ActivityLogger();
        }
        return instance;
    }
    
    // ============ 配置方法 ============
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void setUseColor(boolean useColor) {
        this.useColor = useColor;
    }
    
    public void setCompactMode(boolean compactMode) {
        this.compactMode = compactMode;
    }
    
    public void setShowTimestamp(boolean showTimestamp) {
        this.showTimestamp = showTimestamp;
    }
    
    public void setMaxHistorySize(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
        trimHistory();
    }
    
    // ============ 核心活动记录方法 ============
    
    /**
     * 开始记录一个活动
     * 
     * @param type 活动类型
     * @param description 活动描述
     * @return 活动ID，用于后续更新
     */
    public String startActivity(ActivityType type, String description) {
        if (!enabled) return null;
        
        ActivityEntry entry = new ActivityEntry(type, description);
        String id = entry.getId();
        
        activeActivities.put(id, entry);
        activityHistory.add(entry);
        trimHistory();
        
        // 通知监听器
        notifyListeners(entry);
        
        // 显示活动开始
        displayActivityStart(entry);
        
        return id;
    }
    
    /**
     * 开始记录一个活动（带元数据）
     */
    public String startActivity(ActivityType type, String description, Map<String, Object> metadata) {
        String id = startActivity(type, description);
        if (id != null && metadata != null) {
            ActivityEntry entry = activeActivities.get(id);
            if (entry != null) {
                metadata.forEach(entry::addMetadata);
            }
        }
        return id;
    }
    
    /**
     * 更新活动进度
     * 
     * @param activityId 活动ID
     * @param progressPercent 进度百分比 (0-100)
     * @param message 可选的进度消息
     */
    public void updateProgress(String activityId, int progressPercent, String message) {
        if (!enabled || activityId == null) return;
        
        ActivityEntry entry = activeActivities.get(activityId);
        if (entry == null) return;
        
        entry.setProgressPercent(progressPercent);
        if (message != null) {
            entry.addMetadata("progressMessage", message);
        }
        
        // 实时更新显示
        if (!compactMode) {
            displayProgressUpdate(entry);
        }
    }
    
    /**
     * 完成活动
     * 
     * @param activityId 活动ID
     * @param result 活动结果描述
     */
    public void completeActivity(String activityId, String result) {
        if (!enabled || activityId == null) return;
        
        ActivityEntry entry = activeActivities.remove(activityId);
        if (entry == null) return;
        
        entry.complete(result);
        displayActivityComplete(entry);
        notifyListeners(entry);
    }
    
    /**
     * 完成活动（无结果）
     */
    public void completeActivity(String activityId) {
        completeActivity(activityId, null);
    }
    
    /**
     * 活动失败
     * 
     * @param activityId 活动ID
     * @param errorMessage 错误消息
     */
    public void failActivity(String activityId, String errorMessage) {
        if (!enabled || activityId == null) return;
        
        ActivityEntry entry = activeActivities.remove(activityId);
        if (entry == null) return;
        
        entry.fail(errorMessage);
        displayActivityFailed(entry);
        notifyListeners(entry);
    }
    
    /**
     * 取消活动
     */
    public void cancelActivity(String activityId) {
        if (!enabled || activityId == null) return;
        
        ActivityEntry entry = activeActivities.remove(activityId);
        if (entry == null) return;
        
        entry.cancel();
        displayActivityCancelled(entry);
        notifyListeners(entry);
    }
    
    /**
     * 添加活动元数据
     */
    public void addActivityMetadata(String activityId, String key, Object value) {
        if (!enabled || activityId == null) return;
        
        ActivityEntry entry = activeActivities.get(activityId);
        if (entry != null) {
            entry.addMetadata(key, value);
        }
    }
    
    // ============ 便捷方法 ============
    
    /**
     * 记录一个瞬时活动（自动开始和完成）
     */
    public void logActivity(ActivityType type, String description) {
        if (!enabled) return;
        
        String id = startActivity(type, description);
        completeActivity(id);
    }
    
    /**
     * 记录一个瞬时活动（带结果）
     */
    public void logActivity(ActivityType type, String description, String result) {
        if (!enabled) return;
        
        String id = startActivity(type, description);
        completeActivity(id, result);
    }
    
    /**
     * 记录工具调用
     */
    public String logToolCall(String toolName, String params) {
        ActivityType type = ActivityType.fromToolName(toolName);
        String description = toolName;
        if (params != null && !params.isEmpty()) {
            description += ": " + truncate(params, 60);
        }
        return startActivity(type, description);
    }
    
    /**
     * 记录工具调用（带文件路径）
     */
    public String logToolCall(String toolName, String filePath, String operation) {
        ActivityType type = ActivityType.fromToolName(toolName);
        String description = operation + " " + filePath;
        return startActivity(type, description);
    }
    
    // ============ 显示方法 ============
    
    private void displayActivityStart(ActivityEntry entry) {
        clearStatusLine();
        
        if (compactMode) {
            // 紧凑模式：单行显示
            String line = formatCompactLine(entry);
            System.out.println(line);
        } else {
            // 详细模式：显示开始框
            String header = formatActivityHeader(entry, true);
            System.out.println(header);
        }
    }
    
    private void displayActivityComplete(ActivityEntry entry) {
        if (compactMode) {
            // 在同一行更新状态
            String line = formatCompactLine(entry);
            System.out.println(line);
        } else {
            System.out.println(formatActivityFooter(entry, true));
        }
    }
    
    private void displayActivityFailed(ActivityEntry entry) {
        if (compactMode) {
            String line = formatCompactLine(entry);
            System.out.println(line);
        } else {
            System.out.println(formatActivityFooter(entry, false));
        }
    }
    
    private void displayActivityCancelled(ActivityEntry entry) {
        if (compactMode) {
            String line = formatCompactLine(entry);
            System.out.println(line);
        } else {
            System.out.println(formatActivityFooter(entry, false));
        }
    }
    
    private void displayProgressUpdate(ActivityEntry entry) {
        String line = formatProgressLine(entry);
        updateStatusLine(line);
    }
    
    // ============ 格式化方法 ============
    
    private String formatCompactLine(ActivityEntry entry) {
        StringBuilder sb = new StringBuilder();
        
        // 时间戳
        if (showTimestamp) {
            sb.append(colorize("[" + entry.getStartTime().format(TIME_FORMATTER) + "]", CliLogger.GRAY));
            sb.append(" ");
        }
        
        // 状态图标
        String statusIcon = entry.getStatus().getIcon();
        String statusColor = entry.getStatus() == ActivityEntry.ActivityStatus.SUCCESS ? CliLogger.GREEN :
                            entry.getStatus() == ActivityEntry.ActivityStatus.FAILED ? CliLogger.RED :
                            entry.getStatus() == ActivityEntry.ActivityStatus.RUNNING ? CliLogger.YELLOW : CliLogger.GRAY;
        sb.append(colorize(statusIcon, statusColor));
        sb.append(" ");
        
        // 活动类型图标和标签
        sb.append(colorize(entry.getType().getIcon() + " " + entry.getType().getLabel(), entry.getType().getColor()));
        sb.append(" ");
        
        // 描述
        sb.append(truncate(entry.getDescription(), 50));
        
        // 进度（如果进行中）
        if (entry.getStatus() == ActivityEntry.ActivityStatus.RUNNING && entry.getProgressPercent() > 0) {
            sb.append(" ").append(entry.getProgressPercent()).append("%");
        }
        
        // 时长（如果已完成）
        if (entry.getStatus() != ActivityEntry.ActivityStatus.RUNNING) {
            sb.append(" ").append(colorize("(" + entry.getFormattedDuration() + ")", CliLogger.GRAY));
        }
        
        return sb.toString();
    }
    
    private String formatActivityHeader(ActivityEntry entry, boolean isStart) {
        StringBuilder sb = new StringBuilder();
        
        String icon = entry.getType().getIcon();
        String label = entry.getType().getLabel();
        String color = entry.getType().getColor();
        
        sb.append(colorize("┌─ " + icon + " " + label, color));
        
        if (showTimestamp) {
            sb.append(colorize(" @ " + entry.getStartTime().format(TIME_FORMATTER), CliLogger.GRAY));
        }
        
        sb.append(colorize(" ─────", color));
        sb.append("\n");
        sb.append("│ ").append(entry.getDescription());
        
        return sb.toString();
    }
    
    private String formatActivityFooter(ActivityEntry entry, boolean success) {
        String color = success ? CliLogger.GREEN : CliLogger.RED;
        String icon = success ? "✓" : "✗";
        String status = success ? "成功" : "失败";
        
        StringBuilder sb = new StringBuilder();
        sb.append("│\n");
        sb.append(colorize("└─ " + icon + " " + status, color));
        sb.append(colorize(" (" + entry.getFormattedDuration() + ")", CliLogger.GRAY));
        
        return sb.toString();
    }
    
    private String formatProgressLine(ActivityEntry entry) {
        int percent = entry.getProgressPercent();
        int width = 20;
        int filled = (int) ((double) percent / 100 * width);
        
        StringBuilder bar = new StringBuilder();
        bar.append("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("]");
        
        String barColor = percent < 30 ? CliLogger.RED : 
                         percent < 70 ? CliLogger.YELLOW : CliLogger.GREEN;
        
        return colorize(entry.getType().getIcon() + " " + bar.toString() + " " + percent + "%", barColor);
    }
    
    // ============ 状态行管理 ============
    
    private void updateStatusLine(String line) {
        clearStatusLine();
        currentStatusLine = line;
        System.out.print("\r" + line);
        System.out.flush();
        isStatusLineDirty = true;
    }
    
    private void clearStatusLine() {
        if (isStatusLineDirty) {
            System.out.print("\r" + " ".repeat(Math.max(currentStatusLine.length(), 80)) + "\r");
            System.out.flush();
            isStatusLineDirty = false;
        }
    }
    
    // ============ 监听器管理 ============
    
    public void addActivityListener(Consumer<ActivityEntry> listener) {
        activityListeners.add(listener);
    }
    
    public void removeActivityListener(Consumer<ActivityEntry> listener) {
        activityListeners.remove(listener);
    }
    
    private void notifyListeners(ActivityEntry entry) {
        for (Consumer<ActivityEntry> listener : activityListeners) {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                // 忽略监听器错误
            }
        }
    }
    
    // ============ 查询方法 ============
    
    public List<ActivityEntry> getActivityHistory() {
        return new ArrayList<>(activityHistory);
    }
    
    public List<ActivityEntry> getActiveActivities() {
        return new ArrayList<>(activeActivities.values());
    }
    
    public List<ActivityEntry> getActivitiesByType(ActivityType type) {
        return activityHistory.stream()
            .filter(e -> e.getType() == type)
            .toList();
    }
    
    public ActivityEntry getActivity(String activityId) {
        // 先查活跃的
        ActivityEntry entry = activeActivities.get(activityId);
        if (entry != null) return entry;
        
        // 再查历史
        return activityHistory.stream()
            .filter(e -> e.getId().equals(activityId))
            .findFirst()
            .orElse(null);
    }
    
    public void clearHistory() {
        activityHistory.clear();
    }
    
    private void trimHistory() {
        while (activityHistory.size() > maxHistorySize) {
            activityHistory.remove(0);
        }
    }
    
    // ============ 统计信息 ============
    
    public ActivityStats getStats() {
        return new ActivityStats(activityHistory);
    }
    
    public static class ActivityStats {
        private final int totalActivities;
        private final int successfulActivities;
        private final int failedActivities;
        private final long totalDurationMs;
        
        public ActivityStats(List<ActivityEntry> history) {
            this.totalActivities = history.size();
            this.successfulActivities = (int) history.stream()
                .filter(e -> e.getStatus() == ActivityEntry.ActivityStatus.SUCCESS)
                .count();
            this.failedActivities = (int) history.stream()
                .filter(e -> e.getStatus() == ActivityEntry.ActivityStatus.FAILED)
                .count();
            this.totalDurationMs = history.stream()
                .mapToLong(ActivityEntry::getDurationMs)
                .sum();
        }
        
        public int getTotalActivities() { return totalActivities; }
        public int getSuccessfulActivities() { return successfulActivities; }
        public int getFailedActivities() { return failedActivities; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public double getSuccessRate() {
            return totalActivities > 0 ? (double) successfulActivities / totalActivities * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("活动统计: 总计=%d, 成功=%d, 失败=%d, 成功率=%.1f%%",
                totalActivities, successfulActivities, failedActivities, getSuccessRate());
        }
    }
    
    // ============ 工具方法 ============
    
    private String colorize(String text, String color) {
        if (!useColor) return text;
        return color + text + CliLogger.RESET;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    // ============ 静态便捷方法 ============
    
    public static void log(ActivityType type, String description) {
        getInstance().logActivity(type, description);
    }
    
    public static String start(ActivityType type, String description) {
        return getInstance().startActivity(type, description);
    }
    
    public static void complete(String activityId) {
        getInstance().completeActivity(activityId);
    }
    
    public static void complete(String activityId, String result) {
        getInstance().completeActivity(activityId, result);
    }
    
    public static void fail(String activityId, String error) {
        getInstance().failActivity(activityId, error);
    }
    
    public static void progress(String activityId, int percent) {
        getInstance().updateProgress(activityId, percent, null);
    }
}
