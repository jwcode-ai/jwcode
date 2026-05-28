package com.jwcode.core.aicl;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AICLDeserializer — 将 AICL XML 字符串反序列化为 {@link AICLContext}。
 *
 * <p>支持从 AICL v1.0 协议格式的 XML 中提取上下文块、控制配置等信息。
 * 遵循"宽容解析、严格验证"原则——对格式错误尽量容错，但对关键字段缺失
 * 则抛出异常。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class AICLDeserializer {

    private static final Logger logger = Logger.getLogger(AICLDeserializer.class.getName());

    private static final Pattern CTX_CONTEXT = Pattern.compile(
            "<ctx:context[^>]*session\\s*=\\s*\"([^\"]*)\"[^>]*turn\\s*=\\s*\"([^\"]*)\"[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CTX_BLOCK = Pattern.compile(
            "<ctx:block([^>]*)>(.*?)</ctx:block>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CTX_LABEL = Pattern.compile(
            "<ctx:label[^>]*>(.*?)</ctx:label>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CTX_ABSTRACT = Pattern.compile(
            "<ctx:abstract[^>]*>(.*?)</ctx:abstract>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CTX_CONTENT = Pattern.compile(
            "<ctx:content[^>]*>(.*?)</ctx:content>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CTX_SUMMARY = Pattern.compile(
            "<ctx:summary[^>]*>(.*?)</ctx:summary>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CTX_BUDGET = Pattern.compile(
            "total\\s*=\\s*\"(\\d+)\".*?used\\s*=\\s*\"(\\d+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CTX_EVICTION = Pattern.compile(
            "policy\\s*=\\s*\"([^\"]*)\".*?threshold\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CTX_PIN = Pattern.compile(
            "ids\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CTX_LIFECYCLE = Pattern.compile(
            "default-ttl\\s*=\\s*\"(\\d+)\".*?max-generation\\s*=\\s*\"(\\d+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "(\\S+)\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CDATA_PATTERN = Pattern.compile(
            "<!\\[CDATA\\[(.*?)\\]\\]>",
            Pattern.DOTALL);

    /** 默认构造函数 */
    public AICLDeserializer() {
    }

    /**
     * 从 XML 字符串反序列化为 AICLContext。
     *
     * @param xml AICL XML 文档
     * @return 解析后的 AICLContext
     * @throws IllegalArgumentException 如果 XML 格式无效
     */
    public AICLContext deserialize(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            throw new IllegalArgumentException("XML content is null or empty");
        }

        // 提取上下文属性
        String sessionId = "unknown";
        int turn = 0;
        Matcher ctxMatcher = CTX_CONTEXT.matcher(xml);
        if (ctxMatcher.find()) {
            sessionId = ctxMatcher.group(1);
            try { turn = Integer.parseInt(ctxMatcher.group(2)); } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
        }

        // 解析控制层
        ContextControl control = parseControl(xml);

        // 解析块
        List<ContextBlock> blocks = parseBlocks(xml);

        logger.info("[AICLDeserializer] deserialized session=" + sessionId
                + ", turn=" + turn + ", blocks=" + blocks.size());

        return new AICLContext(sessionId, turn, control, blocks);
    }

    /**
     * 仅解析块列表（不解析 control）。
     */
    public List<ContextBlock> parseBlocks(String xml) {
        List<ContextBlock> blocks = new ArrayList<>();
        Matcher m = CTX_BLOCK.matcher(xml);

        while (m.find()) {
            String attrsStr = m.group(1);
            String body = m.group(2);

            Map<String, String> attrs = parseAttributes(attrsStr);

            String id = attrs.getOrDefault("id", "block_" + UUID.randomUUID().toString().substring(0, 8));

            ContextBlock.Builder builder = ContextBlock.builder(id);

            // 解析属性
            builder.type(attrs.getOrDefault("type", "general"))
                   .role(attrs.getOrDefault("role", "default"))
                   .priority(parsePriority(attrs.getOrDefault("priority", "medium")))
                   .format(attrs.getOrDefault("format", "markdown"))
                   .state(parseLifecycle(attrs.getOrDefault("state", "active")));

            // 生命周期字段
            if (attrs.containsKey("ttl")) {
                try { builder.ttl(Integer.parseInt(attrs.get("ttl"))); } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
            }
            if (attrs.containsKey("last-access")) {
                try { builder.lastAccess(Long.parseLong(attrs.get("last-access"))); } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
            }
            if (attrs.containsKey("access-count")) {
                try { builder.accessCount(Integer.parseInt(attrs.get("access-count"))); } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
            }
            if (attrs.containsKey("generation")) {
                try { builder.generation(Integer.parseInt(attrs.get("generation"))); } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
            }
            if (attrs.containsKey("tokens")) {
                try { builder.estimatedTokens(Long.parseLong(attrs.get("tokens"))); } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
            }

            // 解析子元素
            parseLabel(body).ifPresent(builder::label);
            parseAbstract(body).ifPresent(builder::blockAbstract);
            parseContent(body).ifPresent(builder::content);
            parseSummary(body).ifPresent(builder::summary);

            // 处理 CDATA
            ContextBlock block = builder.build();
            block.setContent(extractCDATA(block.getContent()));

            blocks.add(block);
        }

        return blocks;
    }

    // ==================== 私有解析方法 ====================

    private ContextControl parseControl(String xml) {
        ContextControl.ContextBudget budget = parseBudget(xml);
        ContextControl.EvictionConfig eviction = parseEvictionConfig(xml);
        Set<String> pinnedIds = parsePinnedIds(xml);
        ContextControl.LifecycleDefaults lifecycle = parseLifecycleDefaults(xml);

        return new ContextControl(
                budget != null ? budget : new ContextControl.ContextBudget(8000, 0),
                eviction != null ? eviction : ContextControl.EvictionConfig.DEFAULT,
                pinnedIds.isEmpty() ? Set.of("sys", "usr") : pinnedIds,
                lifecycle != null ? lifecycle : ContextControl.LifecycleDefaults.DEFAULT
        );
    }

    private ContextControl.ContextBudget parseBudget(String xml) {
        Matcher m = CTX_BUDGET.matcher(xml);
        if (m.find()) {
            try {
                long total = Long.parseLong(m.group(1));
                long used = Long.parseLong(m.group(2));
                return new ContextControl.ContextBudget(total, used);
            } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
        }
        return null;
    }

    private ContextControl.EvictionConfig parseEvictionConfig(String xml) {
        Matcher m = CTX_EVICTION.matcher(xml);
        if (m.find()) {
            try {
                String policy = m.group(1);
                double threshold = Double.parseDouble(m.group(2));
                return new ContextControl.EvictionConfig(policy, threshold);
            } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
        }
        return null;
    }

    private Set<String> parsePinnedIds(String xml) {
        Matcher m = CTX_PIN.matcher(xml);
        if (m.find()) {
            return new LinkedHashSet<>(Arrays.asList(m.group(1).split("\\s*,\\s*")));
        }
        return new LinkedHashSet<>();
    }

    private ContextControl.LifecycleDefaults parseLifecycleDefaults(String xml) {
        Matcher m = CTX_LIFECYCLE.matcher(xml);
        if (m.find()) {
            try {
                int ttl = Integer.parseInt(m.group(1));
                int maxGen = Integer.parseInt(m.group(2));
                return new ContextControl.LifecycleDefaults(ttl, maxGen);
            } catch (NumberFormatException ignored) { logger.fine("Parse int failed"); }
        }
        return null;
    }

    private Optional<String> parseLabel(String body) {
        Matcher m = CTX_LABEL.matcher(body);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    private Optional<String> parseAbstract(String body) {
        Matcher m = CTX_ABSTRACT.matcher(body);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    private Optional<String> parseContent(String body) {
        Matcher m = CTX_CONTENT.matcher(body);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    private Optional<String> parseSummary(String body) {
        Matcher m = CTX_SUMMARY.matcher(body);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    private Map<String, String> parseAttributes(String attrsStr) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = ATTR_PATTERN.matcher(attrsStr);
        while (m.find()) {
            map.put(m.group(1).trim(), m.group(2).trim());
        }
        return map;
    }

    private BlockPriority parsePriority(String name) {
        for (BlockPriority p : BlockPriority.values()) {
            if (p.getName().equalsIgnoreCase(name)
                    || p.name().equalsIgnoreCase(name)
                    || String.valueOf(p.getLevel()).equals(name)) {
                return p;
            }
        }
        return BlockPriority.MEDIUM;
    }

    private BlockLifecycle parseLifecycle(String state) {
        for (BlockLifecycle l : BlockLifecycle.values()) {
            if (l.getState().equalsIgnoreCase(state) || l.name().equalsIgnoreCase(state)) {
                return l;
            }
        }
        return BlockLifecycle.ACTIVE;
    }

    private String extractCDATA(String content) {
        if (content == null) return null;
        Matcher m = CDATA_PATTERN.matcher(content);
        return m.find() ? m.group(1).trim() : content.trim();
    }
}
