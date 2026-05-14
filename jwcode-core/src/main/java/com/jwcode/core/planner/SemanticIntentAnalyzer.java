package com.jwcode.core.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.planner.IntentAnalyzer.AnalysisResult;
import com.jwcode.core.planner.IntentAnalyzer.Complexity;
import com.jwcode.core.planner.IntentAnalyzer.TaskType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * SemanticIntentAnalyzer — LLM 驱动的意图分析器。
 *
 * <p>替代纯正则的 {@link IntentAnalyzer}，采用三级策略：
 * <ol>
 *   <li><b>快路径</b>：斜杠命令、明显闲聊 → 正则匹配（&lt;1ms）</li>
 *   <li><b>LLM 分类</b>：发送结构化 prompt → 解析 JSON 响应（~500ms）</li>
 *   <li><b>降级</b>：LLM 不可用/超时 → 回退到 {@link IntentAnalyzer} 正则匹配</li>
 * </ol>
 * </p>
 *
 * <p>线程安全。缓存相同输入的分类结果（60 秒 TTL）。</p>
 */
public class SemanticIntentAnalyzer {

    private static final Logger logger = Logger.getLogger(SemanticIntentAnalyzer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** LLM 分类超时时间（秒） */
    private static final long LLM_TIMEOUT_SECONDS = 3;

    /** 缓存条目 TTL（秒） */
    private static final long CACHE_TTL_SECONDS = 60;

    /** 缓存最大条目数 */
    private static final int MAX_CACHE_SIZE = 200;

    /** LLM 服务（可为 null，此时直接走 fallback） */
    private final LLMService llmService;

    /** 正则 fallback */
    private final IntentAnalyzer fallbackAnalyzer;

    /** 项目上下文（由 MemoryAgent 提供，可为空） */
    private volatile String projectContext;

    /** 已知模块列表 */
    private volatile List<String> knownModules;

    /** 分类结果缓存 */
    private final Map<String, CacheEntry> cache;

    /** 是否启用 LLM 分类 */
    private volatile boolean llmEnabled;

    /**
     * @param llmService LLM 服务（可为 null，此时始终走 fallback）
     */
    public SemanticIntentAnalyzer(LLMService llmService) {
        this.llmService = llmService;
        this.fallbackAnalyzer = new IntentAnalyzer();
        this.cache = new ConcurrentHashMap<>();
        this.knownModules = Collections.emptyList();
        this.llmEnabled = llmService != null;
    }

    /**
     * @param llmService      LLM 服务
     * @param fallbackAnalyzer 自定义 fallback（可为 null，使用默认 IntentAnalyzer）
     */
    public SemanticIntentAnalyzer(LLMService llmService, IntentAnalyzer fallbackAnalyzer) {
        this.llmService = llmService;
        this.fallbackAnalyzer = fallbackAnalyzer != null ? fallbackAnalyzer : new IntentAnalyzer();
        this.cache = new ConcurrentHashMap<>();
        this.knownModules = Collections.emptyList();
        this.llmEnabled = llmService != null;
    }

    // ==================== 配置方法 ====================

    /**
     * 设置项目上下文（由 MemoryAgent 提供）
     */
    public void setProjectContext(String projectContext) {
        this.projectContext = projectContext;
    }

    /**
     * 设置已知模块列表
     */
    public void setKnownModules(List<String> knownModules) {
        this.knownModules = knownModules != null ? new ArrayList<>(knownModules) : Collections.emptyList();
    }

    /**
     * 启用/禁用 LLM 分类
     */
    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled && llmService != null;
        logger.info("LLM intent classification: " + (this.llmEnabled ? "enabled" : "disabled"));
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cache.clear();
        logger.fine("Intent classification cache cleared");
    }

    // ==================== 核心分析方法 ====================

    /**
     * 分析用户输入
     *
     * @param userInput     用户输入
     * @param currentTaskId 当前任务 ID（用于中断检测，可为 null）
     * @return 分析结果
     */
    public AnalysisResult analyze(String userInput, String currentTaskId) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return new AnalysisResult(TaskType.CHAT, Complexity.SIMPLE,
                Collections.emptyList(), "", "Empty input", false, null);
        }

        String input = userInput.trim();

        // ① 快路径：斜杠命令和明显闲聊
        AnalysisResult quickResult = quickPath(input, currentTaskId);
        if (quickResult != null) {
            return quickResult;
        }

        // ② LLM 分类
        if (llmEnabled && llmService != null) {
            // 检查缓存
            CacheEntry cached = cache.get(input);
            if (cached != null && !cached.isExpired()) {
                logger.fine("Intent cache hit for: " + truncate(input, 50));
                return cached.result;
            }

            try {
                AnalysisResult llmResult = classifyWithLLM(input, currentTaskId);
                if (llmResult != null) {
                    // 缓存结果
                    evictCacheIfNeeded();
                    cache.put(input, new CacheEntry(llmResult));
                    return llmResult;
                }
            } catch (Exception e) {
                logger.warning("LLM intent classification failed: " + e.getMessage()
                    + ". Falling back to regex.");
            }
        }

        // ③ 降级：正则 fallback
        logger.fine("Using regex fallback for intent classification");
        return fallbackAnalyzer.analyze(input, currentTaskId);
    }

    /**
     * 快速判断是否为闲聊
     */
    public boolean isChat(String userInput) {
        AnalysisResult result = analyze(userInput, null);
        return result.getTaskType() == TaskType.CHAT;
    }

    /**
     * 快速判断是否为中断
     */
    public boolean isInterruption(String userInput) {
        // 优先用 fallback 的正则（更可靠）
        return fallbackAnalyzer.isInterruption(userInput);
    }

    // ==================== 私有方法 ====================

    /**
     * 快路径：正则匹配斜杠命令和明显闲聊
     *
     * @return AnalysisResult 如果匹配成功，否则 null
     */
    private AnalysisResult quickPath(String input, String currentTaskId) {
        // 斜杠命令
        if (input.startsWith("/")) {
            return fallbackAnalyzer.analyze(input, currentTaskId);
        }

        // 明显闲聊（正则快速判断）
        if (fallbackAnalyzer.isChat(input)) {
            return fallbackAnalyzer.analyze(input, currentTaskId);
        }

        return null;
    }

    /**
     * 使用 LLM 进行意图分类
     */
    private AnalysisResult classifyWithLLM(String userInput, String currentTaskId) {
        // 构建 messages
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(
            IntentAnalysisPrompt.buildSystemPrompt(projectContext, knownModules)));
        messages.add(LLMMessage.user(
            IntentAnalysisPrompt.buildUserPrompt(userInput)));

        logger.fine("Sending intent classification request to LLM...");

        try {
            LLMResponse response = llmService.chat(messages)
                .get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (response == null || response.getErrorMessage() != null) {
                String err = response != null ? response.getErrorMessage() : "null response";
                logger.warning("LLM intent classification returned error: " + err);
                return null;
            }

            String jsonStr = IntentAnalysisPrompt.extractJson(response.getContent());
            if (jsonStr == null) {
                logger.warning("Failed to extract JSON from LLM response: "
                    + truncate(response.getContent(), 100));
                return null;
            }

            return parseAnalysisResult(jsonStr, userInput, currentTaskId);

        } catch (TimeoutException e) {
            logger.warning("LLM intent classification timed out after " + LLM_TIMEOUT_SECONDS + "s");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("LLM intent classification interrupted");
            return null;
        } catch (Exception e) {
            logger.warning("LLM intent classification exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析 JSON 响应为 AnalysisResult
     */
    @SuppressWarnings("unchecked")
    private AnalysisResult parseAnalysisResult(String jsonStr, String userInput, String currentTaskId) {
        try {
            Map<String, Object> map = MAPPER.readValue(jsonStr,
                new TypeReference<Map<String, Object>>() {});

            // 解析 taskType（容错：无法识别时回退 GENERAL）
            TaskType taskType = parseTaskType(map.get("taskType"));

            // 解析 complexity（容错：默认 MEDIUM）
            Complexity complexity = parseComplexity(map.get("complexity"));

            // 解析 modulesInvolved
            List<String> modules = new ArrayList<>();
            Object modulesObj = map.get("modulesInvolved");
            if (modulesObj instanceof List) {
                for (Object m : (List<?>) modulesObj) {
                    if (m != null) {
                        modules.add(m.toString());
                    }
                }
            }

            // 解析 techStack
            String techStack = map.get("techStack") != null
                ? map.get("techStack").toString() : "";

            // 解析 summary
            String summary = map.get("summary") != null
                ? map.get("summary").toString()
                : "[" + taskType.getDisplayName() + "] " + truncate(userInput, 80);

            // 解析 isInterruption
            boolean isInterruption = Boolean.TRUE.equals(map.get("isInterruption"));

            logger.fine("LLM classified intent: type=" + taskType
                + ", complexity=" + complexity
                + ", modules=" + modules
                + ", confidence=" + map.get("confidence"));

            return new AnalysisResult(
                taskType, complexity, modules, techStack,
                summary, isInterruption,
                isInterruption ? currentTaskId : null
            );

        } catch (Exception e) {
            logger.warning("Failed to parse LLM intent JSON: " + e.getMessage()
                + ". Raw: " + truncate(jsonStr, 200));
            return null;
        }
    }

    /**
     * 安全解析 TaskType
     */
    private TaskType parseTaskType(Object value) {
        if (value == null) return TaskType.GENERAL;
        try {
            return TaskType.valueOf(value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试模糊匹配
            String v = value.toString().toLowerCase();
            if (v.contains("feature") || v.contains("功能")) return TaskType.FEATURE;
            if (v.contains("bug") || v.contains("fix") || v.contains("修复")) return TaskType.BUGFIX;
            if (v.contains("refactor") || v.contains("重构")) return TaskType.REFACTOR;
            if (v.contains("test") || v.contains("测试")) return TaskType.TEST;
            if (v.contains("doc") || v.contains("文档")) return TaskType.DOC;
            if (v.contains("analyz") || v.contains("分析")) return TaskType.ANALYZE;
            if (v.contains("debug") || v.contains("调试")) return TaskType.DEBUG;
            if (v.contains("review") || v.contains("审查")) return TaskType.REVIEW;
            if (v.contains("chat") || v.contains("闲聊")) return TaskType.CHAT;
            logger.fine("Unknown taskType from LLM: '" + value + "', falling back to GENERAL");
            return TaskType.GENERAL;
        }
    }

    /**
     * 安全解析 Complexity
     */
    private Complexity parseComplexity(Object value) {
        if (value == null) return Complexity.MEDIUM;
        try {
            return Complexity.valueOf(value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            String v = value.toString().toLowerCase();
            if (v.contains("simple") || v.contains("简单")) return Complexity.SIMPLE;
            if (v.contains("complex") || v.contains("复杂")) return Complexity.COMPLEX;
            return Complexity.MEDIUM;
        }
    }

    /**
     * 缓存逐出（LRU 简化版：超过上限时清空一半）
     */
    private void evictCacheIfNeeded() {
        if (cache.size() >= MAX_CACHE_SIZE) {
            // 移除一半的过期/最旧条目
            int toRemove = cache.size() / 2;
            Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
            int removed = 0;
            while (it.hasNext() && removed < toRemove) {
                it.next();
                it.remove();
                removed++;
            }
            logger.fine("Cache eviction: removed " + removed + " entries");
        }
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }

    // ==================== 内部类 ====================

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final AnalysisResult result;
        final Instant createdAt;

        CacheEntry(AnalysisResult result) {
            this.result = result;
            this.createdAt = Instant.now();
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(CACHE_TTL_SECONDS));
        }
    }
}
