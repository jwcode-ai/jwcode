package com.jwcode.core.aicl;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AICLSerializer — 将 AICL 上下文结构序列化为 XML 字符串。
 *
 * <p>输出格式遵循 AICL v1.0 规范，生成符合 {@code http://aicl.org/v1}
 * 命名空间的 XML 文档。</p>
 *
 * <p>序列化规则：
 * <ul>
 *   <li>pinned 块完整输出，不省略任何内容</li>
 *   <li>archived 块只输出 label + abstract，不输出 content</li>
 *   <li>summarized 块输出 summary 替代 content</li>
 *   <li>deprecated 块不输出</li>
 *   <li>不超过 token 预算时不进行截断</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class AICLSerializer {

    private static final Logger logger = Logger.getLogger(AICLSerializer.class.getName());

    private static final String XMLNS = "http://aicl.org/v1";
    private static final String INDENT = "  ";

    /** 默认构造函数 */
    public AICLSerializer() {
    }

    /**
     * 将 AICLContext 序列化为 XML 字符串。
     *
     * @param context AICL 上下文容器
     * @return 完整的 AICL XML 文档
     */
    public String serialize(AICLContext context) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // <ctx:context>
        xml.append("<ctx:context xmlns:ctx=\"").append(XMLNS).append("\"")
           .append(" version=\"1.0\"")
           .append(" session=\"").append(escapeAttr(context.getSessionId())).append("\"")
           .append(" turn=\"").append(context.getCurrentTurn()).append("\"")
           .append(">\n\n");

        // <ctx:control>
        serializeControl(xml, context.getControl(), INDENT);
        xml.append("\n");

        // <ctx:blocks>
        xml.append(INDENT).append("<ctx:blocks>\n");
        for (ContextBlock block : context.getBlocks()) {
            if (block.getState() == BlockLifecycle.DEPRECATED) continue;
            serializeBlock(xml, block, INDENT + INDENT);
        }
        xml.append(INDENT).append("</ctx:blocks>\n\n");

        // <ctx:trace>
        xml.append(INDENT).append("<ctx:trace>\n");
        xml.append(INDENT).append(INDENT)
           .append("<ctx:integrity checksum=\"sha256:").append(context.getChecksum()).append("\"/>\n");
        xml.append(INDENT).append("</ctx:trace>\n\n");

        xml.append("</ctx:context>\n");

        logger.fine("[AICLSerializer] serialized " + context.getBlockCount() + " blocks, "
                + context.getTotalTokens() + " tokens");

        return xml.toString();
    }

    /**
     * 便捷方法：直接序列化块集合（使用默认控制配置）。
     */
    public String serializeBlocks(Collection<ContextBlock> blocks, ContextControl control,
                                   String sessionId, int turn) {
        AICLContext context = new AICLContext(sessionId, turn, control, blocks);
        return serialize(context);
    }

    // ==================== 私有序列化方法 ====================

    private void serializeControl(StringBuilder xml, ContextControl control, String indent) {
        ContextControl.ContextBudget budget = control.getBudget();
        ContextControl.EvictionConfig eviction = control.getEvictionConfig();
        ContextControl.LifecycleDefaults lifecycle = control.getLifecycleDefaults();

        xml.append(indent).append("<ctx:control>\n");

        // 预算
        xml.append(indent).append(INDENT)
           .append("<ctx:budget total=\"").append(budget.getTotal())
           .append("\" used=\"").append(budget.getUsed())
           .append("\" remaining=\"").append(budget.getRemaining()).append("\"/>\n");

        // 策略
        xml.append(indent).append(INDENT).append("<ctx:strategy>\n");
        xml.append(indent).append(INDENT).append(INDENT)
           .append("<ctx:eviction policy=\"").append(escapeAttr(eviction.getPolicy()))
           .append("\" threshold=\"").append(eviction.getThreshold()).append("\"/>\n");
        xml.append(indent).append(INDENT).append(INDENT)
           .append("<ctx:pin ids=\"").append(escapeAttr(String.join(",", control.getPinnedIds()))).append("\"/>\n");
        xml.append(indent).append(INDENT).append(INDENT)
           .append("<ctx:lifecycle default-ttl=\"").append(lifecycle.getDefaultTtl())
           .append("\" max-generation=\"").append(lifecycle.getMaxGeneration()).append("\"/>\n");
        xml.append(indent).append(INDENT).append("</ctx:strategy>\n");

        xml.append(indent).append("</ctx:control>\n");
    }

    private void serializeBlock(StringBuilder xml, ContextBlock block, String indent) {
        xml.append(indent).append("<ctx:block")
           .append(" id=\"").append(escapeAttr(block.getId())).append("\"")
           .append(" type=\"").append(escapeAttr(block.getType())).append("\"")
           .append(" role=\"").append(escapeAttr(block.getRole() != null ? block.getRole() : "default")).append("\"")
           .append(" priority=\"").append(block.getPriority().getName()).append("\"")
           .append(" format=\"").append(escapeAttr(block.getFormat() != null ? block.getFormat() : "markdown")).append("\"")
           .append(" state=\"").append(block.getState().getState()).append("\"");

        // 生命周期字段
        if (block.getTtl() != 0) {
            xml.append(" ttl=\"").append(block.getTtl()).append("\"");
        }
        if (block.getLastAccess() != null) {
            xml.append(" last-access=\"").append(block.getLastAccess().toEpochMilli()).append("\"");
        }
        if (block.getAccessCount() > 0) {
            xml.append(" access-count=\"").append(block.getAccessCount()).append("\"");
        }
        if (block.getGeneration() > 0) {
            xml.append(" generation=\"").append(block.getGeneration()).append("\"");
        }
        if (block.getEstimatedTokens() > 0) {
            xml.append(" tokens=\"").append(block.getEstimatedTokens()).append("\"");
        }

        xml.append(">\n");

        // label
        if (block.getLabel() != null && !block.getLabel().isEmpty()) {
            xml.append(indent).append(INDENT)
               .append("<ctx:label>").append(escapeContent(block.getLabel())).append("</ctx:label>\n");
        }

        // abstract
        if (block.getBlockAbstract() != null && !block.getBlockAbstract().isEmpty()) {
            xml.append(indent).append(INDENT)
               .append("<ctx:abstract>").append(escapeContent(block.getBlockAbstract())).append("</ctx:abstract>\n");
        }

        // content — 根据状态决定输出
        if (block.getState() == BlockLifecycle.SUMMARIZED) {
            // 摘要态：输出 summary 替代 content
            String summary = block.getSummary();
            if (summary != null && !summary.isEmpty()) {
                xml.append(indent).append(INDENT)
                   .append("<ctx:summary for=\"").append(escapeAttr(block.getId())).append("\"")
                   .append(" original-tokens=\"").append(block.getEstimatedTokens()).append("\"")
                   .append(" compressed-tokens=\"").append(estimateTokens(summary)).append("\">\n");
                xml.append(indent).append(INDENT).append(INDENT)
                   .append(escapeContent(summary)).append("\n");
                xml.append(indent).append(INDENT).append("</ctx:summary>\n");
            }
        } else if (block.getState() != BlockLifecycle.ARCHIVED && block.getState() != BlockLifecycle.DEPRECATED) {
            // 有内容的块
            String content = block.getContent();
            if (content != null && !content.isEmpty()) {
                xml.append(indent).append(INDENT).append("<ctx:boundary mark=\"begin\"/>\n");
                xml.append(indent).append(INDENT).append("<ctx:content>\n");
                xml.append("<![CDATA[\n").append(content).append("\n]]>\n");
                xml.append(indent).append(INDENT).append("</ctx:content>\n");
                xml.append(indent).append(INDENT).append("<ctx:boundary mark=\"end\"/>\n");
            }
        }
        // 归档态：只有 label + abstract，不输出 content

        // 扩展属性
        Map<String, String> attrs = block.getAttributes();
        if (attrs != null) {
            for (Map.Entry<String, String> entry : attrs.entrySet()) {
                xml.append(indent).append(INDENT)
                   .append("<ctx:attr name=\"").append(escapeAttr(entry.getKey()))
                   .append("\" value=\"").append(escapeAttr(entry.getValue())).append("\"/>\n");
            }
        }

        xml.append(indent).append("</ctx:block>\n");
    }

    // ==================== 工具方法 ====================

    private static String escapeAttr(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("'", "&apos;");
    }

    private static String escapeContent(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    private static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chinese = 0;
        int other = 0;
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                chinese++;
            } else {
                other++;
            }
        }
        return chinese + (other / 4);
    }
}
