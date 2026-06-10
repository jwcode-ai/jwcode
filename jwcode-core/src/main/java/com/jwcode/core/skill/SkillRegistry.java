package com.jwcode.core.skill;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 技能注册表
 * 
 * 管理所有技能的注册、查找和生命周期
 */
public class SkillRegistry {
    
    private static final Logger logger = Logger.getLogger(SkillRegistry.class.getName());
    
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final Map<Skill.Category, List<Skill>> skillsByCategory = new ConcurrentHashMap<>();
    private final List<Skill> builtinSkills = new ArrayList<>();
    private final List<SkillProvider> providers = new ArrayList<>();

    public SkillRegistry() {
        registerBuiltinSkills();
        // 初始化 SkillProvider 链
        providers.add(new SystemSkillProvider());
        providers.add(new UserSkillProvider());
        // 从所有 Provider 加载 .skill.md 文件
        loadFromProviders();
    }

    /**
     * 从所有 SkillProvider 加载技能定义。
     */
    public void loadFromProviders() {
        // 按优先级排序
        providers.sort(Comparator.comparingInt(SkillProvider::priority));

        for (SkillProvider provider : providers) {
            try {
                List<SkillDefinition> definitions = provider.discover();
                for (SkillDefinition def : definitions) {
                    if (!skills.containsKey(def.id())) {
                        register(def.toSkill());
                    }
                }
                logger.info("[SkillRegistry] 从 " + provider.getName()
                    + " 加载 " + definitions.size() + " 个技能");
            } catch (Exception e) {
                logger.warning("[SkillRegistry] 加载提供者失败: " + provider.getName()
                    + " — " + e.getMessage());
            }
        }
    }

    /**
     * 添加技能提供者。
     */
    public void addProvider(SkillProvider provider) {
        providers.add(provider);
        providers.sort(Comparator.comparingInt(SkillProvider::priority));
    }

    /**
     * 获取所有技能提供者。
     */
    public List<SkillProvider> getProviders() {
        return Collections.unmodifiableList(new ArrayList<>(providers));
    }
    
    /**
     * 注册技能
     */
    public void register(Skill skill) {
        if (skills.containsKey(skill.getId())) {
            logger.fine("[SkillRegistry] Skill already registered, skipping: " + skill.getId());
            return;
        }
        skills.put(skill.getId(), skill);
        skillsByCategory
            .computeIfAbsent(skill.getCategory(), k -> new ArrayList<>())
            .add(skill);
        logger.info("[SkillRegistry] 注册技能: " + skill.getId());
    }
    
    /**
     * 注销技能
     */
    public void unregister(String skillId) {
        Skill removed = skills.remove(skillId);
        if (removed != null) {
            skillsByCategory.getOrDefault(removed.getCategory(), new ArrayList<>())
                .remove(removed);
            logger.info("[SkillRegistry] 注销技能: " + skillId);
        }
    }
    
    /**
     * 注册来自插件的技能。
     */
    public void registerPluginSkills(com.jwcode.core.plugin.PluginManager pluginManager) {
        var skillPlugins = pluginManager.getPluginsWithCapability(
            com.jwcode.plugin.api.PluginCapability.SKILL);
        for (var plugin : skillPlugins) {
            logger.info("[SkillRegistry] 注册插件技能: " + plugin.getManifest().id());
            // 插件通过 PluginContext.registerSkill() 回调注册
        }
    }

    /**
     * 获取技能
     */
    public Optional<Skill> get(String skillId) {
        return Optional.ofNullable(skills.get(skillId));
    }
    
    /**
     * 根据输入匹配技能
     */
    public List<Skill> match(String input) {
        return skills.values().stream()
            .filter(skill -> skill.matches(input))
            .sorted(Comparator.comparingInt(s -> -matchScore(s, input)))
            .collect(Collectors.toList());
    }
    
    /**
     * 获取最佳匹配技能
     */
    public Optional<Skill> findBestMatch(String input) {
        return match(input).stream().findFirst();
    }
    
    /**
     * 根据分类获取技能
     */
    public List<Skill> getByCategory(Skill.Category category) {
        return skillsByCategory.getOrDefault(category, List.of());
    }
    
    /**
     * 获取所有技能
     */
    public List<Skill> getAll() {
        return new ArrayList<>(skills.values());
    }
    
    /**
     * 根据标签搜索
     */
    public List<Skill> searchByTag(String tag) {
        String lowerTag = tag.toLowerCase();
        return skills.values().stream()
            .filter(s -> s.getTags().stream()
                .anyMatch(t -> t.toLowerCase().contains(lowerTag)))
            .collect(Collectors.toList());
    }
    
    /**
     * 列出所有技能ID
     */
    public List<String> listIds() {
        return new ArrayList<>(skills.keySet());
    }
    
    /**
     * 检查技能是否存在
     */
    public boolean has(String skillId) {
        return skills.containsKey(skillId);
    }
    
    /**
     * 获取技能数量
     */
    public int size() {
        return skills.size();
    }
    
    /**
     * 清除所有技能（保留内置）
     */
    public void clear() {
        skills.clear();
        skillsByCategory.clear();
        // 重新注册内置技能
        for (Skill skill : builtinSkills) {
            register(skill);
        }
    }
    
    /**
     * 计算匹配分数
     */
    private int matchScore(Skill skill, String input) {
        String lowerInput = input.toLowerCase();
        int score = 0;
        
        // 名称完全匹配得分最高
        if (skill.getName().toLowerCase().equals(lowerInput)) {
            score += 100;
        } else if (skill.getName().toLowerCase().contains(lowerInput)) {
            score += 50;
        }
        
        // ID 匹配
        if (skill.getId().toLowerCase().contains(lowerInput)) {
            score += 30;
        }
        
        // 描述匹配
        if (skill.getDescription().toLowerCase().contains(lowerInput)) {
            score += 20;
        }
        
        // 标签匹配
        for (String tag : skill.getTags()) {
            if (lowerInput.contains(tag.toLowerCase())) {
                score += 25;
            }
        }
        
        return score;
    }
    
    /**
     * 注册内置技能
     */
    private void registerBuiltinSkills() {
        // 代码解释技能
        builtinSkills.add(Skill.builder()
            .id("explain-code")
            .name("解释代码")
            .description("解释代码的功能、逻辑和实现细节")
            .category(Skill.Category.CODE)
            .tags(List.of("explain", "code", "理解", "分析"))
            .systemPrompt("你是一个代码解释专家。请详细解释用户提供的代码，包括：\n" +
                "1. 代码的整体功能\n" +
                "2. 关键逻辑和算法\n" +
                "3. 输入输出说明\n" +
                "4. 潜在的问题或改进建议")
            .examples(List.of(
                Skill.Example.builder()
                    .input("解释这段代码")
                    .output("代码解释...")
                    .build()
            ))
            .build());
        
        // 代码重构技能
        builtinSkills.add(Skill.builder()
            .id("refactor-code")
            .name("重构代码")
            .description("重构代码以提高可读性、性能和可维护性")
            .category(Skill.Category.CODE)
            .tags(List.of("refactor", "optimize", "改进", "重构"))
            .systemPrompt("你是一个代码重构专家。请帮助用户重构代码，关注：\n" +
                "1. 提高代码可读性\n" +
                "2. 消除重复代码\n" +
                "3. 改进命名和结构\n" +
                "4. 保持功能不变")
            .examples(List.of(
                Skill.Example.builder()
                    .input("重构这个方法")
                    .output("重构后的代码...")
                    .build()
            ))
            .build());
        
        // 生成测试技能
        builtinSkills.add(Skill.builder()
            .id("generate-tests")
            .name("生成测试")
            .description("为代码生成单元测试和集成测试")
            .category(Skill.Category.TEST)
            .tags(List.of("test", "testing", "单元测试", "测试用例"))
            .systemPrompt("你是一个测试专家。请为用户的代码生成全面的测试用例，包括：\n" +
                "1. 正常路径测试\n" +
                "2. 边界条件测试\n" +
                "3. 异常处理测试\n" +
                "4. 使用合适的测试框架")
            .examples(List.of(
                Skill.Example.builder()
                    .input("为这个方法写测试")
                    .output("测试代码...")
                    .build()
            ))
            .build());
        
        // 调试技能
        builtinSkills.add(Skill.builder()
            .id("debug-code")
            .name("调试代码")
            .description("帮助定位和修复代码中的问题")
            .category(Skill.Category.CODE)
            .tags(List.of("debug", "fix", "bug", "修复", "调试"))
            .systemPrompt("你是一个调试专家。请帮助用户定位和修复代码问题：\n" +
                "1. 分析问题现象\n" +
                "2. 定位根本原因\n" +
                "3. 提出修复方案\n" +
                "4. 验证修复效果")
            .examples(List.of(
                Skill.Example.builder()
                    .input("这里有个 bug")
                    .output("问题分析和修复...")
                    .build()
            ))
            .build());
        
        // 生成文档技能
        builtinSkills.add(Skill.builder()
            .id("generate-docs")
            .name("生成文档")
            .description("为代码生成文档、注释或 README")
            .category(Skill.Category.DOCUMENT)
            .tags(List.of("doc", "documentation", "文档", "注释"))
            .systemPrompt("你是一个技术文档专家。请为用户的代码生成清晰的文档：\n" +
                "1. 函数/类文档注释\n" +
                "2. 使用说明\n" +
                "3. 参数和返回值说明\n" +
                "4. 示例代码")
            .examples(List.of(
                Skill.Example.builder()
                    .input("为这个项目写 README")
                    .output("README 内容...")
                    .build()
            ))
            .build());
        
        // 代码审查技能
        builtinSkills.add(Skill.builder()
            .id("code-review")
            .name("代码审查")
            .description("审查代码质量、安全性和最佳实践")
            .category(Skill.Category.ANALYSIS)
            .tags(List.of("review", "审查", "质量", "安全"))
            .systemPrompt("你是一个代码审查专家。请审查用户的代码，关注：\n" +
                "1. 代码质量和可读性\n" +
                "2. 潜在的安全漏洞\n" +
                "3. 性能问题\n" +
                "4. 最佳实践遵循情况")
            .examples(List.of(
                Skill.Example.builder()
                    .input("审查这段代码")
                    .output("审查报告...")
                    .build()
            ))
            .build());
        
        // 注册所有内置技能
        for (Skill skill : builtinSkills) {
            register(skill);
        }
        
        logger.info("[SkillRegistry] 已注册 " + builtinSkills.size() + " 个内置技能");
    }
}
