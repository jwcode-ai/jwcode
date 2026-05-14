package com.jwcode.core.planner;

import com.jwcode.core.planner.IntentAnalyzer.Complexity;
import com.jwcode.core.planner.IntentAnalyzer.TaskType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * IntentAnalysisPrompt — LLM 意图分类的 Prompt 模板构建器。
 *
 * <p>构建 system prompt（含 TaskType/Complexity 枚举定义）和 user prompt，
 * 要求 LLM 返回固定 JSON schema 的意图分析结果。</p>
 *
 * <p>支持注入 MemoryAgent 项目上下文，提升分类准确率。</p>
 */
public class IntentAnalysisPrompt {

    // ==================== JSON Schema ====================

    /**
     * 要求 LLM 返回的 JSON 结构
     */
    public static final String JSON_SCHEMA = """
        {
          "taskType": "FEATURE|BUGFIX|REFACTOR|TEST|DOC|ANALYZE|DEBUG|REVIEW|CHAT|GENERAL",
          "complexity": "SIMPLE|MEDIUM|COMPLEX",
          "modulesInvolved": ["module1", "module2"],
          "techStack": "Java, Spring",
          "summary": "一句话任务摘要",
          "confidence": 0.95,
          "isInterruption": false
        }
        """;

    // ==================== System Prompt ====================

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        # 意图分类器

        你是 JWCode 的任务意图分类器。根据用户的自然语言输入，判断其意图类型。

        ## 任务类型定义

        - **FEATURE**（新功能开发）：添加、新增、实现、开发、创建新功能/模块/接口/组件
        - **BUGFIX**（Bug修复）：修复、解决、修正 bug/缺陷/错误/异常/崩溃/NPE
        - **REFACTOR**（代码重构）：重构、优化、重写、提取、合并、拆分、清理代码
        - **TEST**（测试）：编写/运行测试、单元测试、集成测试、提升覆盖率
        - **DOC**（文档）：编写/更新文档、README、API文档、注释
        - **ANALYZE**（分析）：分析、调研、研究、评估代码结构/依赖/技术债务
        - **DEBUG**（调试）：调试、排查、定位、追踪、诊断问题
        - **REVIEW**（代码审查）：审查、评审代码质量/安全/风格
        - **CHAT**（闲聊）：问候、感谢、自我介绍、能力询问、与编码无关的对话
        - **GENERAL**（通用任务）：无法明确归类的情况

        ## 复杂度定义

        - **SIMPLE**：单一文件修改、简单配置变更、一行代码修复
        - **MEDIUM**：涉及 2-5 个文件的修改，有明确方案
        - **COMPLEX**：跨模块/跨服务、大规模重构、需分阶段执行

        ## 中断检测

        用户输入斜杠命令（/stop、/pause、/cancel、/resume 等）或明确表示暂停/切换任务时，
        isInterruption 应为 true。

        ## 项目上下文

        %s

        ## 输出要求

        - 只返回纯 JSON，不要包含 markdown 代码块标记
        - 不要包含任何解释性文字
        - confidence 为 0.0-1.0 的置信度
        - modulesInvolved 从已知模块中选择，不确定则为空数组
        """;

    // ==================== 构建方法 ====================

    /**
     * 构建 system prompt
     *
     * @param projectContext MemoryAgent 提供的项目上下文（可为空字符串）
     * @param knownModules   已知模块列表（可为空）
     */
    public static String buildSystemPrompt(String projectContext, List<String> knownModules) {
        StringBuilder ctx = new StringBuilder();

        if (knownModules != null && !knownModules.isEmpty()) {
            ctx.append("- **已知模块**: ")
                .append(knownModules.stream()
                    .map(m -> "`" + m + "`")
                    .collect(Collectors.joining(", ")))
                .append("\n");
        }

        if (projectContext != null && !projectContext.isBlank()) {
            ctx.append("- **项目信息**: ").append(projectContext).append("\n");
        }

        if (ctx.length() == 0) {
            ctx.append("（无额外项目上下文）");
        }

        return String.format(SYSTEM_PROMPT_TEMPLATE, ctx.toString());
    }

    /**
     * 构建 system prompt（无额外上下文）
     */
    public static String buildSystemPrompt() {
        return buildSystemPrompt(null, null);
    }

    /**
     * 构建 user prompt
     *
     * @param userInput 用户的原始输入
     */
    public static String buildUserPrompt(String userInput) {
        return "请分析以下用户输入的意图：\n\n" + userInput;
    }

    // ==================== 解析方法 ====================

    /**
     * 从 LLM 响应中提取 JSON 内容（去除可能的 markdown 代码块标记）
     *
     * @param rawResponse LLM 原始响应文本
     * @return 清理后的 JSON 字符串
     */
    public static String extractJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String text = rawResponse.trim();

        // 去除 markdown 代码块
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }

        // 找到第一个 { 和最后一个 }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 获取所有 TaskType 的名称列表（用于 prompt 验证）
     */
    public static List<String> getTaskTypeNames() {
        return List.of(
            "FEATURE", "BUGFIX", "REFACTOR", "TEST", "DOC",
            "ANALYZE", "DEBUG", "REVIEW", "CHAT", "GENERAL"
        );
    }

    /**
     * 获取所有 Complexity 的名称列表（用于 prompt 验证）
     */
    public static List<String> getComplexityNames() {
        return List.of("SIMPLE", "MEDIUM", "COMPLEX");
    }
}
