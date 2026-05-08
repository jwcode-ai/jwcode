package com.jwcode.core.planner.checkpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * SharedContextBus — 子任务间共享中间成果的上下文总线。
 *
 * <p>上游任务的输出作为下游任务的上下文注入。
 * 支持按任务ID存取中间结果。</p>
 */
public class SharedContextBus {

    private static final Logger logger = Logger.getLogger(SharedContextBus.class.getName());

    private final Map<String, Object> bus = new ConcurrentHashMap<>();

    /**
     * 存入共享数据
     */
    public void put(String key, Object value) {
        bus.put(key, value);
        logger.fine("SharedContextBus: put " + key + " (" + 
            (value != null ? value.getClass().getSimpleName() : "null") + ")");
    }

    /**
     * 取出共享数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) bus.get(key);
    }

    /**
     * 取出共享数据，带默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) bus.getOrDefault(key, defaultValue);
    }

    /**
     * 检查是否存在
     */
    public boolean containsKey(String key) {
        return bus.containsKey(key);
    }

    /**
     * 移除共享数据
     */
    public Object remove(String key) {
        return bus.remove(key);
    }

    /**
     * 清空总线
     */
    public void clear() {
        bus.clear();
        logger.fine("SharedContextBus: cleared");
    }

    /**
     * 获取所有键
     */
    public Iterable<String> keys() {
        return bus.keySet();
    }

    /**
     * 获取大小
     */
    public int size() {
        return bus.size();
    }

    /**
     * 导出为 JSON 字符串（用于 Checkpoint 保存）
     */
    public String exportToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : bus.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"");
            sb.append(escapeJson(String.valueOf(entry.getValue()))).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
