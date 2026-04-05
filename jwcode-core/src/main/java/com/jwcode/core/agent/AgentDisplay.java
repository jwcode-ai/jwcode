package com.jwcode.core.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentDisplay - Agent 显示系统
 * 
 * 功能说明：
 * 负责 Agent 的 UI 渲染和显示，包括状态指示器、进度显示等。
 * 
 * 显示元素：
 * - Agent 状态指示器
 * - 任务进度条
 * - 活动日志
 * - 输出格式化
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AgentDisplay {
    
    private final Map<String, AgentDisplayState> displayStates;
    private final AgentColorManager colorManager;
    private DisplayMode displayMode;
    private boolean showTimestamp;
    private boolean showAgentIcon;
    
    public AgentDisplay() {
        this.displayStates = new ConcurrentHashMap<>();
        this.colorManager = new AgentColorManager();
        this.displayMode = DisplayMode.NORMAL;
        this.showTimestamp = true;
        this.showAgentIcon = true;
    }
    
    /**
     * 注册 Agent 显示状态
     */
    public void registerAgent(String agentId) {
        displayStates.put(agentId, new AgentDisplayState(agentId));
    }
    
    /**
     * 移除 Agent 显示状态
     */
    public void removeAgent(String agentId) {
        displayStates.remove(agentId);
    }
    
    /**
     * 更新 Agent 状态
     */
    public void updateStatus(String agentId, String status) {
        AgentDisplayState state = displayStates.get(agentId);
        if (state != null) {
            state.status = status;
            state.lastUpdated = System.currentTimeMillis();
        }
    }
    
    /**
     * 设置进度
     */
    public void setProgress(String agentId, int progress) {
        AgentDisplayState state = displayStates.get(agentId);
        if (state != null) {
            state.progress = Math.max(0, Math.min(100, progress));
        }
    }
    
    /**
     * 添加日志行
     */
    public void addLog(String agentId, String message) {
        AgentDisplayState state = displayStates.get(agentId);
        if (state != null) {
            LogEntry entry = new LogEntry(message, state.log.size());
            state.log.add(entry);
            
            // 限制日志数量
            if (state.log.size() > 100) {
                state.log.remove(0);
            }
        }
    }
    
    /**
     * 获取显示内容
     */
    public String getDisplayContent(String agentId) {
        AgentDisplayState state = displayStates.get(agentId);
        if (state == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 根据显示模式生成内容
        switch (displayMode) {
            case COMPACT:
                sb.append(renderCompact(state));
                break;
            case DETAILED:
                sb.append(renderDetailed(state));
                break;
            case NORMAL:
            default:
                sb.append(renderNormal(state));
        }
        
        return sb.toString();
    }
    
    /**
     * 获取所有 Agent 的汇总显示
     */
    public String getAllAgentsDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔════════════════════════════════════════╗\n");
        sb.append("║         JWCode Agents 状态             ║\n");
        sb.append("╚════════════════════════════════════════╝\n\n");
        
        for (AgentDisplayState state : displayStates.values()) {
            sb.append(renderNormal(state));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 紧凑模式渲染
     */
    private String renderCompact(AgentDisplayState state) {
        String color = colorManager.getAgentColor(state.agentId);
        String icon = getAgentIcon(state.agentId);
        
        return String.format("%s %s [%d%%] %s", 
            icon, state.agentId, state.progress, state.status);
    }
    
    /**
     * 普通模式渲染
     */
    private String renderNormal(AgentDisplayState state) {
        StringBuilder sb = new StringBuilder();
        
        String icon = getAgentIcon(state.agentId);
        String timestamp = showTimestamp ? formatTimestamp(state.lastUpdated) : "";
        
        sb.append(String.format("%s %s %s\n", icon, state.agentId, timestamp));
        sb.append(String.format("   状态：%s\n", state.status));
        sb.append(String.format("   进度：%s\n", renderProgressBar(state.progress)));
        
        // 显示最近日志
        if (!state.log.isEmpty()) {
            sb.append("   最近活动:\n");
            int start = Math.max(0, state.log.size() - 3);
            for (int i = start; i < state.log.size(); i++) {
                sb.append(String.format("     - %s\n", state.log.get(i).message));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 详细模式渲染
     */
    private String renderDetailed(AgentDisplayState state) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("╔════════════════════════════════════════╗\n");
        sb.append(String.format("║ Agent: %-28s ║\n", state.agentId));
        sb.append("╠════════════════════════════════════════╣\n");
        sb.append(String.format("║ 状态：%-28s ║\n", state.status));
        sb.append(String.format("║ 进度：%-28s ║\n", renderProgressBar(state.progress)));
        sb.append(String.format("║ 更新：%-28s ║\n", formatTimestamp(state.lastUpdated)));
        sb.append("╠════════════════════════════════════════╣\n");
        sb.append("║ 活动日志：\n");
        
        int start = Math.max(0, state.log.size() - 5);
        for (int i = start; i < state.log.size(); i++) {
            String logMsg = truncate(state.log.get(i).message, 35);
            sb.append(String.format("║   %s\n", logMsg));
        }
        
        sb.append("╚════════════════════════════════════════╝");
        
        return sb.toString();
    }
    
    /**
     * 渲染进度条
     */
    private String renderProgressBar(int progress) {
        int width = 20;
        int filled = (progress * width) / 100;
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            if (i < filled) {
                sb.append("█");
            } else {
                sb.append("░");
            }
        }
        sb.append(String.format("] %d%%", progress));
        
        return sb.toString();
    }
    
    /**
     * 获取 Agent 图标
     */
    private String getAgentIcon(String agentId) {
        if (!showAgentIcon) {
            return "";
        }
        
        switch (agentId) {
            case "general": return "🤖";
            case "coding": return "💻";
            case "review": return "🔍";
            case "test": return "✅";
            case "debug": return "🐛";
            default: return "📌";
        }
    }
    
    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 1000) {
            return "刚刚";
        } else if (diff < 60000) {
            return (diff / 1000) + "秒前";
        } else if (diff < 3600000) {
            return (diff / 60000) + "分钟前";
        } else {
            return (diff / 3600000) + "小时前";
        }
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
    
    /**
     * 设置显示模式
     */
    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode;
    }
    
    /**
     * 设置是否显示时间戳
     */
    public void setShowTimestamp(boolean show) {
        this.showTimestamp = show;
    }
    
    /**
     * 设置是否显示 Agent 图标
     */
    public void setShowAgentIcon(boolean show) {
        this.showAgentIcon = show;
    }
    
    /**
     * 获取颜色管理器
     */
    public AgentColorManager getColorManager() {
        return colorManager;
    }
    
    /**
     * 显示模式枚举
     */
    public enum DisplayMode {
        COMPACT,    // 紧凑模式
        NORMAL,     // 普通模式
        DETAILED    // 详细模式
    }
    
    /**
     * Agent 显示状态类
     */
    public static class AgentDisplayState {
        public final String agentId;
        public String status;
        public int progress;
        public long lastUpdated;
        public final List<LogEntry> log;
        
        public AgentDisplayState(String agentId) {
            this.agentId = agentId;
            this.status = "待命";
            this.progress = 0;
            this.lastUpdated = System.currentTimeMillis();
            this.log = new ArrayList<>();
        }
    }
    
    /**
     * 日志条目类
     */
    public static class LogEntry {
        public final String message;
        public final int index;
        public final long timestamp;
        
        public LogEntry(String message, int index) {
            this.message = message;
            this.index = index;
            this.timestamp = System.currentTimeMillis();
        }
    }
}