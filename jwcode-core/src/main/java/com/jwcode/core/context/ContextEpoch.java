package com.jwcode.core.context;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ContextEpoch — 上下文纪元。
 *
 * <p>一个 Epoch 对应 "在一次 agent / model 切换或 compaction 之后，
 * 上下文基线保持不变的连续时间段"。Epoch 是以下操作的锚点：
 * <ul>
 *   <li><b>基线文本</b> — 实际发给 LLM 的系统上下文片段</li>
 *   <li><b>快照</b> — 所有 source 的 JSON 序列化值，用于 {@link #reconcile(ContextRegistry)}</li>
 *   <li><b>修订号</b> — 乐观并发控制，每次 replace 递增</li>
 * </ul>
 */
public class ContextEpoch {

    private static final AtomicLong ID_GEN = new AtomicLong(0);

    private final long id;
    private final String agentId;
    private final Instant createdAt;
    /** 基线文本：实际发送给 LLM 的渲染结果 */
    private String baselineText;
    /** 源快照：epoch 创建时所有 source 的快照 */
    private Map<String, String> snapshot;
    /** 乐观锁修订号 */
    private long revision;
    /** 附加元数据 */
    private final Map<String, Object> metadata;

    public ContextEpoch(String agentId) {
        this.id = ID_GEN.incrementAndGet();
        this.agentId = Objects.requireNonNull(agentId);
        this.createdAt = Instant.now();
        this.revision = 0;
        this.metadata = new HashMap<>();
    }

    public long getId() { return id; }
    public String getAgentId() { return agentId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getBaselineText() { return baselineText; }
    public Map<String, String> getSnapshot() { return snapshot != null ? Collections.unmodifiableMap(snapshot) : Map.of(); }
    public long getRevision() { return revision; }
    public Map<String, Object> getMetadata() { return metadata; }

    /**
     * 初始化基线：加载所有 source 并渲染。
     *
     * @param registry 上下文源注册表
     * @return true 如果所有 source 都可用且成功初始化
     */
    public synchronized boolean initialize(ContextRegistry registry) {
        Map<String, ContextValue<?>> loaded = registry.loadAll();
        StringBuilder baseline = new StringBuilder();
        Map<String, String> snap = new LinkedHashMap<>();

        for (ContextSource<?> source : registry.getAllSources()) {
            ContextValue<?> cv = loaded.get(source.getKey());
            if (cv == null || cv.isUnavailable()) {
                return false;
            }
            if (cv.isError()) {
                continue; // 出错源跳过
            }
            String rendered = renderBaseline(source, cv.getValue());
            if (rendered != null && !rendered.isEmpty()) {
                if (!baseline.isEmpty()) baseline.append("\n\n");
                baseline.append(rendered);
            }
            snap.put(source.getKey(), Objects.toString(cv.getValue()));
        }

        this.baselineText = baseline.toString();
        this.snapshot = snap;
        this.revision = 0;
        return true;
    }

    /**
     * 替换基线：创建完整的新 baseline。
     * 调用方需保证已创建新 epoch，用于 agent 切换或 compaction 后。
     */
    public synchronized boolean replace(ContextRegistry registry) {
        return initialize(registry);
    }

    /**
     * 对比当前 source 值与快照，判断是否需要更新。
     *
     * @return ReconcileResult — Unchanged / Updated / Replace
     */
    public synchronized ReconcileResult reconcile(ContextRegistry registry) {
        if (snapshot == null) {
            return ReconcileResult.replace("no snapshot yet");
        }

        Map<String, ContextValue<?>> current = registry.loadAll();
        List<UpdatedSource> updates = new ArrayList<>();

        for (ContextSource<?> source : registry.getAllSources()) {
            ContextValue<?> cv = current.get(source.getKey());
            if (cv == null || !cv.isAvailable()) continue;

            String newStr = Objects.toString(cv.getValue());
            String oldStr = snapshot.get(source.getKey());

            if (!newStr.equals(oldStr)) {
                updates.add(new UpdatedSource(source.getKey(), oldStr, newStr, cv.getValue()));
            }
        }

        if (updates.isEmpty()) {
            return ReconcileResult.unchanged();
        }

        // 如果有 source 变更，生成增量渲染
        StringBuilder delta = new StringBuilder();
        for (UpdatedSource us : updates) {
            ContextSource<?> source = registry.getSource(us.key());
            if (source != null && source.getUpdateRenderer() != null) {
                String rendered = renderUpdate(source, us.newValue());
                if (rendered != null && !rendered.isEmpty()) {
                    if (!delta.isEmpty()) delta.append("\n\n");
                    delta.append(rendered);
                }
            }
            snapshot.put(us.key(), us.newStr());
        }

        return ReconcileResult.updated(delta.toString());
    }

    /**
     * 标记替换发生（版本号 +1）。
     */
    public synchronized long bumpRevision() {
        return ++revision;
    }

    // ==================== 内部渲染 ====================

    @SuppressWarnings("unchecked")
    private <A> String renderBaseline(ContextSource<A> source, Object value) {
        return source.getBaselineRenderer().render((A) value);
    }

    @SuppressWarnings("unchecked")
    private <A> String renderUpdate(ContextSource<A> source, Object value) {
        if (source.getUpdateRenderer() == null) return null;
        return source.getUpdateRenderer().render((A) value);
    }

    // ==================== 结果类型 ====================

    /**
     * Reconcile 的结果类型。
     */
    public enum ReconcileType { UNCHANGED, UPDATED, REPLACE }

    /**
     * Reconcile 的结果。
     */
    public static final class ReconcileResult {
        private final ReconcileType type;
        private final String deltaText;
        private final String reason;

        private ReconcileResult(ReconcileType type, String deltaText, String reason) {
            this.type = type;
            this.deltaText = deltaText;
            this.reason = reason;
        }

        public static ReconcileResult unchanged() {
            return new ReconcileResult(ReconcileType.UNCHANGED, null, null);
        }

        public static ReconcileResult updated(String deltaText) {
            return new ReconcileResult(ReconcileType.UPDATED, Objects.requireNonNull(deltaText), null);
        }

        public static ReconcileResult replace(String reason) {
            return new ReconcileResult(ReconcileType.REPLACE, null, Objects.requireNonNull(reason));
        }

        public boolean isUnchanged() { return type == ReconcileType.UNCHANGED; }
        public boolean isUpdated() { return type == ReconcileType.UPDATED; }
        public boolean isReplace() { return type == ReconcileType.REPLACE; }
        public ReconcileType getType() { return type; }
        public String getDeltaText() { return deltaText; }
        public String getReason() { return reason; }
    }

    /**
     * 发生变更的 source 记录。
     */
    public record UpdatedSource(String key, String oldStr, String newStr, Object newValue) {}

    // ==================== equals/hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextEpoch epoch)) return false;
        return id == epoch.id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }

    @Override
    public String toString() {
        return "ContextEpoch{id=" + id + ", agent='" + agentId + "', rev=" + revision + "}";
    }
}
