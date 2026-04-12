package com.jwcode.cli.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 活动条目
 * 记录单个活动的详细信息
 */
public class ActivityEntry {
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    private final String id;
    private final ActivityType type;
    private final String description;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private ActivityStatus status;
    private String result;
    private String errorMessage;
    private final Map<String, Object> metadata;
    private int progressPercent;
    
    public enum ActivityStatus {
        PENDING("⏳", "等待中", CliLogger.GRAY),
        RUNNING("▶️", "执行中", CliLogger.YELLOW),
        SUCCESS("✓", "成功", CliLogger.GREEN),
        FAILED("✗", "失败", CliLogger.RED),
        CANCELLED("⏹️", "已取消", CliLogger.GRAY);
        
        private final String icon;
        private final String label;
        private final String color;
        
        ActivityStatus(String icon, String label, String color) {
            this.icon = icon;
            this.label = label;
            this.color = color;
        }
        
        public String getIcon() { return icon; }
        public String getLabel() { return label; }
        public String getColor() { return color; }
    }
    
    public ActivityEntry(ActivityType type, String description) {
        this.id = generateId();
        this.type = type;
        this.description = description;
        this.startTime = LocalDateTime.now();
        this.status = ActivityStatus.RUNNING;
        this.metadata = new HashMap<>();
        this.progressPercent = 0;
    }
    
    private String generateId() {
        return "act-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }
    
    // ============ Getter 方法 ============
    
    public String getId() { return id; }
    public ActivityType getType() { return type; }
    public String getDescription() { return description; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public ActivityStatus getStatus() { return status; }
    public String getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, Object> getMetadata() { return metadata; }
    public int getProgressPercent() { return progressPercent; }
    
    // ============ Setter 方法 ============
    
    public void setStatus(ActivityStatus status) {
        this.status = status;
        if (status == ActivityStatus.SUCCESS || 
            status == ActivityStatus.FAILED || 
            status == ActivityStatus.CANCELLED) {
            this.endTime = LocalDateTime.now();
        }
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = ActivityStatus.FAILED;
        this.endTime = LocalDateTime.now();
    }
    
    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.max(0, Math.min(100, progressPercent));
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    // ============ 便捷方法 ============
    
    public void complete(String result) {
        this.result = result;
        setStatus(ActivityStatus.SUCCESS);
    }
    
    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        setStatus(ActivityStatus.FAILED);
    }
    
    public void cancel() {
        setStatus(ActivityStatus.CANCELLED);
    }
    
    /**
     * 获取执行时长（毫秒）
     */
    public long getDurationMs() {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }
    
    /**
     * 获取格式化的执行时长
     */
    public String getFormattedDuration() {
        long ms = getDurationMs();
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format("%.1fs", ms / 1000.0);
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    // ============ 显示方法 ============
    
    /**
     * 获取单行显示文本（适合紧凑模式）
     */
    public String toCompactString(boolean useColor) {
        StringBuilder sb = new StringBuilder();
        
        // 时间戳
        sb.append("[").append(startTime.format(TIME_FORMATTER)).append("] ");
        
        // 状态图标
        if (useColor) {
            sb.append(status.color).append(status.icon).append(CliLogger.RESET);
        } else {
            sb.append(status.icon);
        }
        sb.append(" ");
        
        // 活动类型
        if (useColor) {
            sb.append(type.getColor()).append(type.getIcon()).append(CliLogger.RESET);
        } else {
            sb.append(type.getIcon());
        }
        sb.append(" ");
        
        // 描述
        sb.append(truncate(description, 50));
        
        // 进度（如果进行中）
        if (status == ActivityStatus.RUNNING && progressPercent > 0) {
            sb.append(" ").append(progressPercent).append("%");
        }
        
        // 时长（如果已完成）
        if (status != ActivityStatus.RUNNING && status != ActivityStatus.PENDING) {
            sb.append(" (").append(getFormattedDuration()).append(")");
        }
        
        return sb.toString();
    }
    
    /**
     * 获取详细显示文本
     */
    public String toDetailedString(boolean useColor) {
        StringBuilder sb = new StringBuilder();
        
        // 头部
        sb.append("┌─ ");
        if (useColor) {
            sb.append(type.getColor())
              .append(type.getIcon())
              .append(" ")
              .append(type.getLabel())
              .append(CliLogger.RESET);
        } else {
            sb.append(type.getIcon()).append(" ").append(type.getLabel());
        }
        sb.append(" ").append(formatStatus(useColor));
        sb.append("\n");
        
        // 描述
        sb.append("│ ").append(description).append("\n");
        
        // 时间信息
        sb.append("│ 开始: ").append(startTime.format(TIME_FORMATTER));
        if (endTime != null) {
            sb.append(" | 结束: ").append(endTime.format(TIME_FORMATTER));
        }
        sb.append(" | 耗时: ").append(getFormattedDuration()).append("\n");
        
        // 进度条（如果进行中）
        if (status == ActivityStatus.RUNNING && progressPercent > 0) {
            sb.append("│ ").append(renderProgressBar(progressPercent, useColor)).append("\n");
        }
        
        // 元数据
        if (!metadata.isEmpty()) {
            sb.append("│ 详情:\n");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                sb.append("│   ").append(entry.getKey()).append(": ")
                  .append(truncate(entry.getValue().toString(), 60)).append("\n");
            }
        }
        
        // 结果或错误
        if (result != null && !result.isEmpty()) {
            sb.append("│ 结果: ").append(truncate(result, 100)).append("\n");
        }
        if (errorMessage != null) {
            if (useColor) {
                sb.append("│ ").append(CliLogger.RED).append("错误: ")
                  .append(errorMessage).append(CliLogger.RESET).append("\n");
            } else {
                sb.append("│ 错误: ").append(errorMessage).append("\n");
            }
        }
        
        sb.append("└");
        return sb.toString();
    }
    
    private String formatStatus(boolean useColor) {
        if (useColor) {
            return status.color + "[" + status.label + "]" + CliLogger.RESET;
        }
        return "[" + status.label + "]";
    }
    
    private String renderProgressBar(int percent, boolean useColor) {
        int width = 20;
        int filled = (int) ((double) percent / 100 * width);
        
        StringBuilder bar = new StringBuilder();
        bar.append("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("] ").append(percent).append("%");
        
        if (useColor) {
            String color = percent < 30 ? CliLogger.RED : 
                          percent < 70 ? CliLogger.YELLOW : CliLogger.GREEN;
            return color + bar.toString() + CliLogger.RESET;
        }
        return bar.toString();
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    @Override
    public String toString() {
        return toCompactString(true);
    }
}
