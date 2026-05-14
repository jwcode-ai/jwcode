package com.jwcode.core.aicl;

/**
 * AICLPromptBuilder — 生成 AI 解析 AICL 协议所需的系统提示规则。
 *
 * <p>负责构建注入到 LLM 系统提示中的 AICL 解析说明，告知 AI 如何理解
 * 优先级、生命周期状态、TTL 等字段的含义。</p>
 *
 * <p>生成规则的原则：
 * <ul>
 *   <li><b>先看骨架</b>：每个 block 的 priority 和 state 决定内容重要性与完整度</li>
 *   <li><b>优先级处理</b>：从 pinned 到 optional 的 6 级处理策略</li>
 *   <li><b>生命周期判断</b>：根据 state 调整阅读策略</li>
 *   <li><b>时间感知</b>：last-access 越久越可能被裁剪</li>
 *   <li><b>代际保护</b>：generation 防止信息失真</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class AICLPromptBuilder {

    /** AICL 命名空间 URI */
    public static final String XMLNS = "http://aicl.org/v1";

    /**
     * 构建完整的 AICL 解析规则 Prompt（Markdown 格式），
     * 直接注入到系统指令中。
     */
    public static String buildSystemPrompt() {
        return """
## AICL 上下文解析规则

你接收到的输入被包裹在 `<ctx:context>` 中，遵循以下规则：

### 1. 先看骨架
每个 `<ctx:block>` 的 `priority` 和 `state` 属性决定这块内容的重要性与完整度。

### 2. 优先级处理
- `priority="pinned"`: 必须完整理解，永不被裁剪
- `priority="critical"`: 核心内容，不可丢失语义
- `priority="high"`: 重要，但可能经过压缩（删了空行/注释）
- `priority="medium"`: 可能已被摘要，如需细节可请求展开
- `priority="low"`: 可能已归档，仅保留概要
- `priority="optional"`: 辅助信息，Token 紧张时可能已删除

### 3. 生命周期判断
- `state="active"`: 完整内容，正常解析
- `state="compressed"`: 已删除冗余格式，注意可能缺少换行/注释
- `state="summarized"`: 仅保留核心结论，细节已压缩
- `state="archived"`: 仅保留元数据，内容已清空
- `state="deprecated"`: 即将删除，可忽略

### 4. 时间感知
`last-access` 越旧的内容越可能被裁剪，`ttl` 为剩余存活轮数。

### 5. 代际保护
`generation` 表示被摘要次数，超过上限后不再压缩，防止信息失真。
""";
    }

    /**
     * 构建简版规则（适合 Token 紧张时使用）。
     */
    public static String buildCompactPrompt() {
        return """
## AICL 规则
- 每个 `<ctx:block>` 看 `priority` 和 `state` 判断重要性和完整度
- priority: pinned(永固) > critical(关键) > high(高) > medium(中) > low(低) > optional(可选)
- state: active(完整) > compressed(已压缩) > summarized(已摘要) > archived(已归档) > deprecated(废弃)
- last-access 越久越可能被裁剪，ttl 为剩余存活轮数
""";
    }

    /**
     * 构建 AICL 命名空间声明（用于 XML 根元素）。
     */
    public static String buildXmlnsDeclaration() {
        return "xmlns:ctx=\"" + XMLNS + "\"";
    }

    /**
     * 获取当前版本号。
     */
    public static String getVersion() {
        return "1.0";
    }
}
