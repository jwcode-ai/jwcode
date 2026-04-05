package com.jwcode.core.agent;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentColorManager - Agent 颜色管理
 * 
 * 功能说明：
 * 为不同的 Agent 分配和管理颜色，确保每个 Agent 有独特的视觉标识。
 * 
 * 颜色系统：
 * - ANSI 颜色代码
 * - RGB 颜色值
 * - 主题支持（亮色/暗色）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AgentColorManager {
    
    private static final String[] DEFAULT_COLORS = {
        "\u001B[31m", // 红色
        "\u001B[32m", // 绿色
        "\u001B[33m", // 黄色
        "\u001B[34m", // 蓝色
        "\u001B[35m", // 紫色
        "\u001B[36m", // 青色
        "\u001B[91m", // 亮红色
        "\u001B[92m", // 亮绿色
        "\u001B[93m", // 亮黄色
        "\u001B[94m", // 亮蓝色
        "\u001B[95m", // 亮紫色
        "\u001B[96m"  // 亮青色
    };
    
    private static final String RESET = "\u001B[0m";
    
    private final Map<String, String> agentColors;
    private final Map<String, Integer> agentColorIndices;
    private final Map<String, RgbColor> agentRgbColors;
    private ColorMode colorMode;
    private int nextColorIndex;
    private boolean useBrightColors;
    
    public AgentColorManager() {
        this.agentColors = new ConcurrentHashMap<>();
        this.agentColorIndices = new ConcurrentHashMap<>();
        this.agentRgbColors = new ConcurrentHashMap<>();
        this.colorMode = ColorMode.ANSI;
        this.nextColorIndex = 0;
        this.useBrightColors = false;
        
        // 为内置 Agent 预分配颜色
        initializeBuiltInAgentColors();
    }
    
    /**
     * 初始化内置 Agent 颜色
     */
    private void initializeBuiltInAgentColors() {
        assignColor("general", DEFAULT_COLORS[0]);   // 红色
        assignColor("coding", DEFAULT_COLORS[1]);    // 绿色
        assignColor("review", DEFAULT_COLORS[2]);    // 黄色
        assignColor("test", DEFAULT_COLORS[3]);      // 蓝色
        assignColor("debug", DEFAULT_COLORS[4]);     // 紫色
    }
    
    /**
     * 为 Agent 分配 ANSI 颜色
     */
    public void assignColor(String agentId, String ansiColor) {
        agentColors.put(agentId, ansiColor);
        agentColorIndices.put(agentId, -1); // 自定义颜色
    }
    
    /**
     * 为 Agent 分配 RGB 颜色
     */
    public void assignRgbColor(String agentId, int r, int g, int b) {
        agentRgbColors.put(agentId, new RgbColor(r, g, b));
    }
    
    /**
     * 自动为 Agent 分配颜色
     */
    public void autoAssignColor(String agentId) {
        if (agentColors.containsKey(agentId)) {
            return; // 已有颜色
        }
        
        int index = nextColorIndex % DEFAULT_COLORS.length;
        String color = DEFAULT_COLORS[index];
        
        agentColors.put(agentId, color);
        agentColorIndices.put(agentId, index);
        nextColorIndex++;
    }
    
    /**
     * 获取 Agent 的 ANSI 颜色代码
     */
    public String getAgentColor(String agentId) {
        String color = agentColors.get(agentId);
        if (color == null) {
            autoAssignColor(agentId);
            color = agentColors.get(agentId);
        }
        return color != null ? color : "";
    }
    
    /**
     * 获取 Agent 的 RGB 颜色
     */
    public RgbColor getAgentRgbColor(String agentId) {
        return agentRgbColors.get(agentId);
    }
    
    /**
     * 使用颜色格式化文本
     */
    public String colorize(String agentId, String text) {
        String color = getAgentColor(agentId);
        return color + text + RESET;
    }
    
    /**
     * 使用颜色格式化文本（带前缀）
     */
    public String colorize(String agentId, String prefix, String text) {
        String color = getAgentColor(agentId);
        return color + prefix + text + RESET;
    }
    
    /**
     * 移除文本中的颜色代码
     */
    public String decolorize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\u001B\\[[0-9;]*m", "");
    }
    
    /**
     * 重置 Agent 颜色
     */
    public void resetColor(String agentId) {
        agentColors.remove(agentId);
        agentColorIndices.remove(agentId);
        agentRgbColors.remove(agentId);
    }
    
    /**
     * 重置所有颜色
     */
    public void resetAllColors() {
        agentColors.clear();
        agentColorIndices.clear();
        agentRgbColors.clear();
        nextColorIndex = 0;
        initializeBuiltInAgentColors();
    }
    
    /**
     * 设置颜色模式
     */
    public void setColorMode(ColorMode mode) {
        this.colorMode = mode;
    }
    
    /**
     * 设置是否使用亮色
     */
    public void setUseBrightColors(boolean useBright) {
        this.useBrightColors = useBright;
        if (useBright) {
            // 重新分配亮色
            for (Map.Entry<String, Integer> entry : agentColorIndices.entrySet()) {
                int index = entry.getValue();
                if (index >= 0 && index < 6) {
                    agentColors.put(entry.getKey(), DEFAULT_COLORS[index + 6]);
                }
            }
        }
    }
    
    /**
     * 获取颜色索引
     */
    public int getColorIndex(String agentId) {
        return agentColorIndices.getOrDefault(agentId, -1);
    }
    
    /**
     * 检查 Agent 是否有颜色
     */
    public boolean hasColor(String agentId) {
        return agentColors.containsKey(agentId) || agentRgbColors.containsKey(agentId);
    }
    
    /**
     * 获取所有 Agent 颜色映射
     */
    public Map<String, String> getAllColors() {
        return new HashMap<>(agentColors);
    }
    
    /**
     * 生成颜色预览
     */
    public String generateColorPreview() {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent 颜色预览：\n\n");
        
        for (Map.Entry<String, String> entry : agentColors.entrySet()) {
            String preview = colorize(entry.getKey(), "■ " + entry.getKey());
            sb.append(preview).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 颜色模式枚举
     */
    public enum ColorMode {
        ANSI,       // ANSI 颜色
        RGB,        // RGB 颜色
        MONOCHROME  // 单色（无颜色）
    }
    
    /**
     * RGB 颜色类
     */
    public static class RgbColor {
        public final int r;
        public final int g;
        public final int b;
        
        public RgbColor(int r, int g, int b) {
            this.r = Math.max(0, Math.min(255, r));
            this.g = Math.max(0, Math.min(255, g));
            this.b = Math.max(0, Math.min(255, b));
        }
        
        /**
         * 转换为 ANSI 256 色代码
         */
        public String toAnsi256() {
            // 简化转换
            int code = 16 + (r / 51) * 36 + (g / 51) * 6 + (b / 51);
            return "\u001B[38;5;" + code + "m";
        }
        
        /**
         * 转换为十六进制字符串
         */
        public String toHex() {
            return String.format("#%02X%02X%02X", r, g, b);
        }
        
        @Override
        public String toString() {
            return toHex();
        }
    }
}