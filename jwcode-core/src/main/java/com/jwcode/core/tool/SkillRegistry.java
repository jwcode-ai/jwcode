package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkillRegistry - 技能注册表
 * 
 * 功能说明：
 * 管理所有可用技能（Slash Commands）的注册、查找和枚举。
 * 技能是 JWCode 中预定义的命令，如 /commit, /review, /pdf 等。
 * 提供线程安全的技能注册和访问功能。
 * 
 * 上下文关系：
 * - 被 SkillManager 引用
 * - 被 SkillTool 用来查找要执行的技能
 * - 在应用启动时注册所有内置技能
 * - 支持远程技能加载
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SkillRegistry {
    
    /**
     * 技能映射表（名称 -> 技能定义）
     */
    private final Map<String, SkillDefinition> skillsByName;
    
    /**
     * 技能列表（保持注册顺序）
     */
    private final List<SkillDefinition> skills;
    
    /**
     * 技能类别映射表
     */
    private final Map<String, Set<String>> skillsByCategory;
    
    /**
     * 构造函数
     */
    public SkillRegistry() {
        this.skillsByName = new ConcurrentHashMap<>();
        this.skills = new ArrayList<>();
        this.skillsByCategory = new ConcurrentHashMap<>();
    }
    
    /**
     * 注册技能
     * 
     * @param skill 要注册的技能定义
     * @return this（用于链式调用）
     * @throws IllegalArgumentException 如果技能名称已存在
     */
    public SkillRegistry register(SkillDefinition skill) {
        Objects.requireNonNull(skill, "skill cannot be null");
        String name = skill.getName();
        if (skillsByName.containsKey(name)) {
            throw new IllegalArgumentException("Skill with name '" + name + "' is already registered");
        }
        
        skillsByName.put(name, skill);
        skills.add(skill);
        
        // 注册到类别
        String category = skill.getCategory();
        if (category != null && !category.isEmpty()) {
            skillsByCategory.computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet()).add(name);
        }
        
        // 注册别名
        for (String alias : skill.getAliases()) {
            skillsByName.put(alias, skill);
        }
        
        return this;
    }
    
    /**
     * 根据名称查找技能
     * 
     * @param name 技能名称
     * @return Optional 包含找到的技能，如果没有找到则为空
     */
    public Optional<SkillDefinition> findByName(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return Optional.ofNullable(skillsByName.get(name));
    }
    
    /**
     * 根据名称获取技能，如果不存在则抛出异常
     * 
     * @param name 技能名称
     * @return 找到的技能
     * @throws NoSuchElementException 如果技能不存在
     */
    public SkillDefinition getByName(String name) {
        return findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Skill not found: " + name));
    }
    
    /**
     * 检查是否包含指定名称的技能
     * 
     * @param name 技能名称
     * @return true 如果包含该技能
     */
    public boolean contains(String name) {
        return skillsByName.containsKey(name);
    }
    
    /**
     * 获取所有已注册的技能
     * 
     * @return 技能列表（不可变）
     */
    public List<SkillDefinition> getAllSkills() {
        return Collections.unmodifiableList(new ArrayList<>(skills));
    }
    
    /**
     * 获取指定类别的所有技能
     * 
     * @param category 类别名称
     * @return 技能列表（不可变）
     */
    public List<SkillDefinition> getSkillsByCategory(String category) {
        Set<String> skillNames = skillsByCategory.get(category);
        if (skillNames == null) {
            return Collections.emptyList();
        }
        List<SkillDefinition> result = new ArrayList<>();
        for (String name : skillNames) {
            Optional.ofNullable(skillsByName.get(name)).ifPresent(result::add);
        }
        return Collections.unmodifiableList(result);
    }
    
    /**
     * 获取所有技能类别
     * 
     * @return 类别集合（不可变）
     */
    public Set<String> getAllCategories() {
        return Collections.unmodifiableSet(new HashSet<>(skillsByCategory.keySet()));
    }
    
    /**
     * 获取所有技能名称
     * 
     * @return 技能名称集合（不可变）
     */
    public Set<String> getAllSkillNames() {
        return Collections.unmodifiableSet(new HashSet<>(skillsByName.keySet()));
    }
    
    /**
     * 获取已注册技能的数量
     * 
     * @return 技能数量
     */
    public int size() {
        return skills.size();
    }
    
    /**
     * 清空所有注册的技能
     */
    public void clear() {
        skillsByName.clear();
        skills.clear();
        skillsByCategory.clear();
    }
    
    /**
     * 移除指定名称的技能
     * 
     * @param name 技能名称
     * @return 被移除的技能，如果不存在则返回 null
     */
    public SkillDefinition unregister(String name) {
        SkillDefinition skill = skillsByName.remove(name);
        if (skill != null) {
            skills.remove(skill);
            // 从类别中移除
            String category = skill.getCategory();
            if (category != null && !category.isEmpty()) {
                Set<String> categorySkills = skillsByCategory.get(category);
                if (categorySkills != null) {
                    categorySkills.remove(name);
                    if (categorySkills.isEmpty()) {
                        skillsByCategory.remove(category);
                    }
                }
            }
            // 移除别名
            for (String alias : skill.getAliases()) {
                skillsByName.remove(alias);
            }
        }
        return skill;
    }
    
    /**
     * 创建默认的技能注册表（包含所有内置技能）
     * 
     * @return 包含所有内置技能的注册表
     */
    public static SkillRegistry createDefault() {
        SkillRegistry registry = new SkillRegistry();
        
        // 注册内置技能
        registry.register(SkillDefinition.builder()
                .name("commit")
                .description("提交代码变更")
                .category("git")
                .aliases(Arrays.asList("/c", "/git-commit"))
                .parameters(Map.of(
                        "message", Map.of("type", "string", "description", "提交消息", "required", true),
                        "files", Map.of("type", "array", "description", "要提交的文件", "required", false)
                ))
                .build());
        
        registry.register(SkillDefinition.builder()
                .name("review")
                .description("代码审查")
                .category("code")
                .aliases(Arrays.asList("/r", "/code-review"))
                .parameters(Map.of(
                        "files", Map.of("type", "array", "description", "要审查的文件", "required", false),
                        "focus", Map.of("type", "string", "description", "审查重点", "required", false)
                ))
                .build());
        
        registry.register(SkillDefinition.builder()
                .name("pdf")
                .description("处理 PDF 文件")
                .category("file")
                .aliases(Arrays.asList("/read-pdf"))
                .parameters(Map.of(
                        "path", Map.of("type", "string", "description", "PDF 文件路径", "required", true),
                        "action", Map.of("type", "string", "description", "操作类型：read, summarize, extract", "required", false)
                ))
                .build());
        
        registry.register(SkillDefinition.builder()
                .name("test")
                .description("运行测试")
                .category("testing")
                .aliases(Arrays.asList("/run-test", "/t"))
                .parameters(Map.of(
                        "files", Map.of("type", "array", "description", "要运行的测试文件", "required", false),
                        "pattern", Map.of("type", "string", "description", "测试文件匹配模式", "required", false)
                ))
                .build());
        
        registry.register(SkillDefinition.builder()
                .name("explain")
                .description("解释代码")
                .category("code")
                .aliases(Arrays.asList("/exp", "/x"))
                .parameters(Map.of(
                        "file", Map.of("type", "string", "description", "要解释的文件", "required", true),
                        "lineRange", Map.of("type", "string", "description", "行范围", "required", false)
                ))
                .build());
        
        registry.register(SkillDefinition.builder()
                .name("fix")
                .description("修复代码问题")
                .category("code")
                .aliases(Arrays.asList("/f", "/repair"))
                .parameters(Map.of(
                        "file", Map.of("type", "string", "description", "要修复的文件", "required", true),
                        "issue", Map.of("type", "string", "description", "问题描述", "required", false)
                ))
                .build());
        
        registry.register(SkillDefinition.builder()
                .name("refactor")
                .description("重构代码")
                .category("code")
                .aliases(Arrays.asList("/ref"))
                .parameters(Map.of(
                        "file", Map.of("type", "string", "description", "要重构的文件", "required", true),
                        "goal", Map.of("type", "string", "description", "重构目标", "required", false)
                ))
                .build());
        
        registry.register(SkillDefinition.builder()
                .name("search")
                .description("搜索代码或文件")
                .category("search")
                .aliases(Arrays.asList("/s", "/find"))
                .parameters(Map.of(
                        "query", Map.of("type", "string", "description", "搜索查询", "required", true),
                        "type", Map.of("type", "string", "description", "搜索类型：code, file, text", "required", false)
                ))
                .build());
        
        return registry;
    }
    
    /**
     * 技能定义类
     */
    public static class SkillDefinition {
        private final String name;
        private final String description;
        private final String category;
        private final List<String> aliases;
        private final Map<String, Object> parameters;
        private final boolean requiresConfirmation;
        private final String remoteUrl;
        
        /**
         * 构造函数
         */
        private SkillDefinition(String name, String description, String category,
                                List<String> aliases, Map<String, Object> parameters,
                                boolean requiresConfirmation, String remoteUrl) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.aliases = aliases != null ? aliases : Collections.emptyList();
            this.parameters = parameters != null ? parameters : Collections.emptyMap();
            this.requiresConfirmation = requiresConfirmation;
            this.remoteUrl = remoteUrl;
        }
        
        /**
         * 获取技能名称
         */
        public String getName() {
            return name;
        }
        
        /**
         * 获取技能描述
         */
        public String getDescription() {
            return description;
        }
        
        /**
         * 获取技能类别
         */
        public String getCategory() {
            return category;
        }
        
        /**
         * 获取技能别名
         */
        public List<String> getAliases() {
            return aliases;
        }
        
        /**
         * 获取参数定义
         */
        public Map<String, Object> getParameters() {
            return parameters;
        }
        
        /**
         * 检查是否需要确认
         */
        public boolean requiresConfirmation() {
            return requiresConfirmation;
        }
        
        /**
         * 获取远程 URL
         */
        public String getRemoteUrl() {
            return remoteUrl;
        }
        
        /**
         * 构建器
         */
        public static Builder builder() {
            return new Builder();
        }
        
        /**
         * 构建器类
         */
        public static class Builder {
            private String name;
            private String description;
            private String category = "default";
            private List<String> aliases = new ArrayList<>();
            private Map<String, Object> parameters = new HashMap<>();
            private boolean requiresConfirmation = false;
            private String remoteUrl;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder description(String description) {
                this.description = description;
                return this;
            }
            
            public Builder category(String category) {
                this.category = category;
                return this;
            }
            
            public Builder aliases(List<String> aliases) {
                this.aliases = aliases;
                return this;
            }
            
            public Builder parameters(Map<String, Object> parameters) {
                this.parameters = parameters;
                return this;
            }
            
            public Builder requiresConfirmation(boolean requiresConfirmation) {
                this.requiresConfirmation = requiresConfirmation;
                return this;
            }
            
            public Builder remoteUrl(String remoteUrl) {
                this.remoteUrl = remoteUrl;
                return this;
            }
            
            public SkillDefinition build() {
                Objects.requireNonNull(name, "name cannot be null");
                Objects.requireNonNull(description, "description cannot be null");
                return new SkillDefinition(name, description, category, aliases, parameters, 
                                          requiresConfirmation, remoteUrl);
            }
        }
    }
}