package com.jwcode.core.llm.fragment;

import com.jwcode.core.config.ConfigManager;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * 片段注册表 — 管理所有 ContextualFragment 的注册、排序和构建。
 *
 * <p>片段按 FragmentCategory 的 ordinal 排序，同类片段按注册顺序。
 * 每个片段可通过 ConfigManager 独立启用/禁用（key: fragment.{id}.enabled）。
 */
public class FragmentRegistry {
    private static final Logger logger = Logger.getLogger(FragmentRegistry.class.getName());

    private final CopyOnWriteArrayList<ContextualFragment> fragments = new CopyOnWriteArrayList<>();
    private final FragmentAuditLog auditLog = new FragmentAuditLog();
    private final ConfigManager configManager;

    private static volatile FragmentRegistry instance;

    private FragmentRegistry() {
        this.configManager = ConfigManager.getInstance();
    }

    public static FragmentRegistry getInstance() {
        if (instance == null) {
            synchronized (FragmentRegistry.class) {
                if (instance == null) {
                    instance = new FragmentRegistry();
                }
            }
        }
        return instance;
    }

    /** 注册一个片段 */
    public void register(ContextualFragment fragment) {
        fragments.add(fragment);
        fragments.sort(Comparator.comparingInt(f -> f.getCategory().ordinal()));
        logger.fine("[FragmentRegistry] 注册片段: " + fragment.getId()
            + " (category=" + fragment.getCategory() + ")");
    }

    /** 批量注册 */
    public void registerAll(Collection<ContextualFragment> newFragments) {
        fragments.addAll(newFragments);
        fragments.sort(Comparator.comparingInt(f -> f.getCategory().ordinal()));
        logger.info("[FragmentRegistry] 批量注册 " + newFragments.size() + " 个片段，总计 "
            + fragments.size() + " 个");
    }

    /** 移除片段 */
    public void unregister(String fragmentId) {
        fragments.removeIf(f -> f.getId().equals(fragmentId));
    }

    /** 获取所有已注册片段（按注入顺序） */
    public List<ContextualFragment> getAllSorted() {
        return Collections.unmodifiableList(new ArrayList<>(fragments));
    }

    /**
     * 构建所有启用的片段，注入到 session 中。
     *
     * @param ctx 构建上下文
     * @param session 目标会话
     * @return 注入的片段结果列表
     */
    public List<FragmentResult> buildAndInject(FragmentContext ctx,
                                                com.jwcode.core.session.Session session) {
        List<FragmentResult> results = new ArrayList<>();
        String sessionId = session.getId();

        for (ContextualFragment fragment : fragments) {
            // 检查配置是否启用
            if (!isFragmentEnabled(fragment)) {
                logger.fine("[FragmentRegistry] 片段已禁用: " + fragment.getId());
                continue;
            }

            // 去重检查：优先使用 session 的 injectedFragmentIds
            if (session.isFragmentInjected(fragment.getId())) {
                logger.fine("[FragmentRegistry] 片段已注入（session 级别），跳过: " + fragment.getId());
                continue;
            }

            // 去重检查：通过内容标记扫描最近消息
            String marker = fragment.getDedupMarker();
            if (marker != null && hasRecentSystemPrompt(session, marker)) {
                logger.fine("[FragmentRegistry] 片段已存在（标记匹配），跳过: " + fragment.getId());
                continue;
            }

            try {
                String content = fragment.build(ctx);
                if (content == null || content.isBlank()) {
                    continue;
                }

                // Token 预算检查
                int tokenEstimate = fragment.getTokenEstimate(ctx);
                if (tokenEstimate > ContextualFragment.MAX_TOKENS_PER_FRAGMENT) {
                    logger.warning("[FragmentRegistry] 片段超过 token 上限: " + fragment.getId()
                        + " estimated=" + tokenEstimate
                        + " max=" + ContextualFragment.MAX_TOKENS_PER_FRAGMENT
                        + " — 截断");
                    content = truncateToTokenBudget(content, ContextualFragment.MAX_TOKENS_PER_FRAGMENT);
                    tokenEstimate = ContextualFragment.MAX_TOKENS_PER_FRAGMENT;
                }

                session.addMessage(
                    com.jwcode.core.model.Message.createSystemMessage(content));
                session.markFragmentInjected(fragment.getId());

                FragmentResult result = new FragmentResult(
                    fragment.getId(), fragment.getCategory(), tokenEstimate, content);
                results.add(result);

                auditLog.record(fragment.getId(), fragment.getCategory(), tokenEstimate, sessionId);
                logger.fine("[FragmentRegistry] 已注入: " + fragment.getId()
                    + " | tokens=" + tokenEstimate);

            } catch (Exception e) {
                logger.warning("[FragmentRegistry] 片段构建失败: " + fragment.getId()
                    + " — " + e.getMessage());
            }
        }

        return results;
    }

    /** 获取审计日志 */
    public FragmentAuditLog getAuditLog() {
        return auditLog;
    }

    /** 清空注册表（用于测试或重载） */
    public void clear() {
        fragments.clear();
    }

    // ─── 私有方法 ───

    private boolean isFragmentEnabled(ContextualFragment fragment) {
        String key = "fragment." + fragment.getId() + ".enabled";
        String value = configManager.get(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return fragment.isEnabledByDefault();
    }

    private boolean hasRecentSystemPrompt(com.jwcode.core.session.Session session, String marker) {
        return session.getMessages().stream()
            .filter(m -> m.getRole() == com.jwcode.core.model.Message.Role.SYSTEM)
            .anyMatch(m -> {
                String content = m.getTextContent();
                return content != null && content.contains(marker);
            });
    }

    private String truncateToTokenBudget(String content, int maxTokens) {
        int maxChars = maxTokens * 4;
        if (content.length() <= maxChars) return content;
        return content.substring(0, maxChars - 20) + "\n\n... [截断，已达 token 上限]";
    }
}
