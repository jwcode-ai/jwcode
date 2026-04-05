package com.jwcode.core.skill;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 技能定义
 * 
 * Skill 是可复用的能力单元，类似于 Kimi Code 的 Skill 系统
 * 每个 Skill 封装特定的任务处理能力
 */
@Data
@Builder
public class Skill {
    
    /**
     * 技能ID（唯一标识）
     */
    private String id;
    
    /**
     * 技能名称
     */
    private String name;
    
    /**
     * 技能描述
     */
    private String description;
    
    /**
     * 技能版本
     */
    @Builder.Default
    private String version = "1.0.0";
    
    /**
     * 作者
     */
    private String author;
    
    /**
     * 技能分类
     */
    private Category category;
    
    /**
     * 标签
     */
    @Builder.Default
    private List<String> tags = List.of();
    
    /**
     * 入口提示词（定义技能的行为模式）
     */
    private String systemPrompt;
    
    /**
     * 示例用法
     */
    @Builder.Default
    private List<Example> examples = List.of();
    
    /**
     * 所需工具列表
     */
    @Builder.Default
    private List<String> requiredTools = List.of();
    
    /**
     * 配置参数
     */
    @Builder.Default
    private Map<String, Object> config = Map.of();
    
    /**
     * 技能来源（本地路径或远程URL）
     */
    private String source;
    
    /**
     * 加载状态
     */
    @Builder.Default
    private LoadStatus status = LoadStatus.UNLOADED;
    
    /**
     * 加载时间
     */
    private long loadedAt;
    
    /**
     * 执行处理器（运行时动态设置）
     */
    private SkillExecutor executor;
    
    public enum Category {
        CODE,       // 代码相关
        ANALYSIS,   // 分析相关
        DOCUMENT,   // 文档相关
        TEST,       // 测试相关
        DEVOPS,     // 运维相关
        CUSTOM      // 自定义
    }
    
    public enum LoadStatus {
        UNLOADED,   // 未加载
        LOADING,    // 加载中
        LOADED,     // 已加载
        ERROR       // 加载失败
    }
    
    @Data
    @Builder
    public static class Example {
        private String input;
        private String output;
        private String description;
    }
    
    /**
     * 执行技能
     */
    public SkillResult execute(SkillContext context) {
        if (executor == null) {
            return SkillResult.error("Skill 未配置执行器: " + id);
        }
        return executor.execute(this, context);
    }
    
    /**
     * 检查是否匹配输入
     */
    public boolean matches(String input) {
        // 基于关键词匹配
        String lowerInput = input.toLowerCase();
        
        // 检查名称匹配
        if (lowerInput.contains(name.toLowerCase())) {
            return true;
        }
        
        // 检查标签匹配
        for (String tag : tags) {
            if (lowerInput.contains(tag.toLowerCase())) {
                return true;
            }
        }
        
        // 检查示例匹配
        for (Example example : examples) {
            if (lowerInput.contains(example.getInput().toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 格式化技能信息
     */
    public String formatInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(name).append("】").append(" v").append(version).append("\n");
        sb.append("ID: ").append(id).append("\n");
        sb.append("分类: ").append(category).append("\n");
        sb.append("描述: ").append(description).append("\n");
        
        if (!tags.isEmpty()) {
            sb.append("标签: ").append(String.join(", ", tags)).append("\n");
        }
        
        if (!examples.isEmpty()) {
            sb.append("示例:\n");
            for (int i = 0; i < Math.min(2, examples.size()); i++) {
                Example ex = examples.get(i);
                sb.append("  • ").append(ex.getInput());
                if (ex.getDescription() != null) {
                    sb.append(" (").append(ex.getDescription()).append(")");
                }
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
}
