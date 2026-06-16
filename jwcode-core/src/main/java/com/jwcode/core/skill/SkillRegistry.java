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
    private final List<SkillProvider> providers = new ArrayList<>();
    private SkillCommandScanner commandScanner;
    private SkillUsageTracker usageTracker;

    public SkillRegistry() {
        // 初始化 SkillProvider 链
        providers.add(new SystemSkillProvider());
        providers.add(new UserSkillProvider());
        // 从所有 Provider 加载 .skill.md 文件
        loadFromProviders();
    }

    /**
     * 绑定命令扫描器 — 注册技能时同时注册斜杠命令。
     */
    public void setCommandScanner(SkillCommandScanner scanner) {
        this.commandScanner = scanner;
    }

    /**
     * 绑定使用追踪器。
     */
    public void setUsageTracker(SkillUsageTracker tracker) {
        this.usageTracker = tracker;
    }

    /**
     * 获取使用追踪器。
     */
    public SkillUsageTracker getUsageTracker() {
        return usageTracker;
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
        // 同步注册斜杠命令
        if (commandScanner != null) {
            commandScanner.registerSkillCommand(skill);
        }
        // 记录使用
        if (usageTracker != null) {
            usageTracker.recordUse(skill.getId());
        }
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
        // 将本注册表暴露给 PluginManager，使插件通过 PluginContext.registerSkill() 注册
        pluginManager.setSkillRegistry(this);

        // 添加 PluginSkillProvider 到提供者链
        addProvider(new PluginSkillProvider(pluginManager));

        var skillPlugins = pluginManager.getPluginsWithCapability(
            com.jwcode.plugin.api.PluginCapability.SKILL);
        for (var plugin : skillPlugins) {
            logger.info("[SkillRegistry] 检测到插件技能: " + plugin.getManifest().id()
                + "（通过 PluginContext.registerSkill() 注册）");
        }
    }

    /**
     * 获取技能（记录使用）。
     */
    public Optional<Skill> get(String skillId) {
        Optional<Skill> result = Optional.ofNullable(skills.get(skillId));
        if (result.isPresent() && usageTracker != null) {
            usageTracker.recordUse(skillId);
        }
        return result;
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
     * 获取所有技能摘要（轻量级，不含 systemPrompt）。
     */
    public List<SkillSummary> listSummaries() {
        return skills.values().stream()
            .map(SkillSummary::from)
            .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取技能摘要。
     */
    public Optional<SkillSummary> getSummary(String skillId) {
        return Optional.ofNullable(skills.get(skillId)).map(SkillSummary::from);
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
     * 根据来源类型获取技能
     */
    public List<Skill> getByProvenance(Skill.Provenance provenance) {
        return skills.values().stream()
            .filter(s -> s.getProvenance() == provenance)
            .collect(Collectors.toList());
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
     * 清除所有技能
     */
    public void clear() {
        skills.clear();
        skillsByCategory.clear();
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
    
}
