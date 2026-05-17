package com.jwcode.core.aicl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AIContextManager - AI 上下文管理器
 *
 * <p>管理 AI 上下文的创建、查询和销毁生命周期。
 * 与 ContextBuilder 配合使用，构建和管理 AI 推理所需的上下文信息。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class AIContextManager {

    private final Map<String, String> contexts;

    /**
     * 构造函数
     */
    public AIContextManager() {
        this.contexts = new ConcurrentHashMap<>();
    }

    /**
     * 初始化上下文管理器
     */
    public void initialize() {
        // 初始化操作
    }

    /**
     * 创建新的上下文
     *
     * @param name 上下文名称
     * @return 上下文 ID
     */
    public String createContext(String name) {
        String contextId = "ctx-" + System.currentTimeMillis() + "-" + name;
        contexts.put(contextId, name);
        return contextId;
    }

    /**
     * 检查上下文是否存在
     *
     * @param contextId 上下文 ID
     * @return true 如果存在
     */
    public boolean hasContext(String contextId) {
        return contexts.containsKey(contextId);
    }

    /**
     * 销毁上下文
     *
     * @param contextId 上下文 ID
     */
    public void destroyContext(String contextId) {
        contexts.remove(contextId);
    }

    /**
     * 获取上下文数量
     *
     * @return 上下文数量
     */
    public int getContextCount() {
        return contexts.size();
    }
}
