package com.jwcode.core.skill;

import com.jwcode.core.plugin.PluginManager;
import com.jwcode.plugin.api.PluginCapability;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 插件技能提供者 — 将插件注册的技能以 SkillProvider 形式暴露给注册表链。
 *
 * <p>优先使用 PluginManager 的插件清单声明，回退到已注册的技能。
 * 优先级 20（低于 system=0 和 user=10，确保系统/用户技能优先）。
 */
public class PluginSkillProvider implements SkillProvider {

    private static final Logger logger = Logger.getLogger(PluginSkillProvider.class.getName());

    private final PluginManager pluginManager;

    public PluginSkillProvider(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public String getName() {
        return "plugin";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public List<SkillDefinition> discover() {
        List<SkillDefinition> definitions = new ArrayList<>();
        var skillPlugins = pluginManager.getPluginsWithCapability(PluginCapability.SKILL);
        for (var plugin : skillPlugins) {
            var manifest = plugin.getManifest();
            String pluginId = manifest.id();
            for (var cap : manifest.capabilities()) {
                if (cap == PluginCapability.SKILL) {
                    SkillDefinition def = new SkillDefinition(
                        "plugin:" + pluginId + ":" + pluginId,
                        manifest.name() + " (Plugin)",
                        manifest.description() != null ? manifest.description() : "",
                        "", // triggerPrompt
                        "此技能由插件 " + manifest.name() + " 提供。\n\n插件 ID: " + pluginId,
                        List.of(), // requiredTools
                        List.of("plugin", pluginId), // tags
                        SkillDefinition.InjectionStrategy.LAZY,
                        "plugin:" + pluginId, // source
                        Skill.Provenance.PLUGIN
                    );
                    definitions.add(def);
                    logger.fine("[PluginSkillProvider] 发现插件技能: " + def.id());
                }
            }
        }
        logger.info("[PluginSkillProvider] 发现 " + definitions.size() + " 个插件技能");
        return definitions;
    }
}
