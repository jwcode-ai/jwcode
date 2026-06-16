package com.jwcode.core.event;

import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SessionProjector — 事件投影器。
 *
 * <p>订阅 {@link EventBus} 上的事件流，维护物化视图：
 * <ul>
 *   <li>会话消息列表 (从 TEXT_STARTED/DELTA/ENDED + TOOL_CALLED/SUCCESS/FAILED 构建)</li>
 *   <li>会话上下文纪元列表</li>
 *   <li>工具调用历史</li>
 *   <li>Token 用量汇总</li>
 * </ul>
 *
 * <p>设计为状态机：events in → state out。可从 {@link EventStore} 重放恢复状态。
 */
public class SessionProjector {

    private static final Logger logger = Logger.getLogger(SessionProjector.class.getName());

    private final String sessionId;
    private final EventBus eventBus;
    private final List<Runnable> cleanupTasks;

    /** 当前会话版本（事件计数） */
    private final AtomicLong version = new AtomicLong(0);

    /** 投影：消息列表 */
    private final List<String> messageIds = new ArrayList<>();
    private final Map<String, Message> messagesById = new ConcurrentHashMap<>();

    /** 投影：上下文纪元 */
    private final List<Long> epochIds = new ArrayList<>();

    /** 投影：Token 用量 */
    private long totalPromptTokens = 0;
    private long totalCompletionTokens = 0;

    /** 流式缓冲：正在构建的消息 */
    private final Map<String, StringBuilder> pendingText = new ConcurrentHashMap<>();

    /** 当前流式消息的事件 ID（TEXT_STARTED 设置，TEXT_ENDED 清除） */
    private String currentTextEventId;

    public SessionProjector(String sessionId, EventBus eventBus) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.cleanupTasks = new ArrayList<>();
        subscribe();
    }

    // ==================== 订阅事件 ====================

    private void subscribe() {
        // TEXT_STARTED → 新建消息
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.TEXT_STARTED, this::onTextStarted));
        // TEXT_DELTA → 追加文本
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.TEXT_DELTA, this::onTextDelta));
        // TEXT_ENDED → 完成消息
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.TEXT_ENDED, this::onTextEnded));
        // TOOL events
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.TOOL_CALLED, this::onToolCalled));
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.TOOL_SUCCESS, this::onToolSuccess));
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.TOOL_FAILED, this::onToolFailed));
        // AGENT/MODEL switch
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.AGENT_SWITCHED, this::onAgentSwitched));
        // COMPACTION
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.COMPACTION_ENDED, this::onCompactionEnded));
        // ERROR
        cleanupTasks.add(eventBus.subscribe(SessionEvent.EventType.ERROR_OCCURRED, this::onError));
    }

    // ==================== 事件处理 ====================

    private void onTextStarted(SessionEvent event) {
        version.incrementAndGet();
        currentTextEventId = event.getEventId();
        pendingText.put(currentTextEventId, new StringBuilder());
    }

    private void onTextDelta(SessionEvent event) {
        if (currentTextEventId == null) return;
        StringBuilder sb = pendingText.get(currentTextEventId);
        if (sb != null && event.getData() != null) {
            sb.append(event.getData());
        }
    }

    private void onTextEnded(SessionEvent event) {
        if (currentTextEventId == null) return;
        StringBuilder sb = pendingText.remove(currentTextEventId);
        currentTextEventId = null;
        if (sb != null) {
            Message msg = Message.createUserMessage(sb.toString());
            messagesById.put(msg.getId(), msg);
            messageIds.add(msg.getId());
        }
    }

    private void onToolCalled(SessionEvent event) {
        version.incrementAndGet();
    }

    private void onToolSuccess(SessionEvent event) {
        version.incrementAndGet();
    }

    private void onToolFailed(SessionEvent event) {
        version.incrementAndGet();
    }

    private void onAgentSwitched(SessionEvent event) {
        version.incrementAndGet();
    }

    private void onCompactionEnded(SessionEvent event) {
        version.incrementAndGet();
    }

    private void onError(SessionEvent event) {
        version.incrementAndGet();
    }

    // ==================== 投影查询 ====================

    /**
     * 获取当前投影的消息列表（按事件发生顺序）。
     */
    public List<Message> getMessages() {
        return messageIds.stream()
            .map(messagesById::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 获取当前版本号（用于增量读取）。
     */
    public long getVersion() { return version.get(); }

    /**
     * 获取 Token 用量汇总。
     */
    public Map<String, Long> getTokenUsage() {
        return Map.of("prompt", totalPromptTokens, "completion", totalCompletionTokens);
    }

    // ==================== 重放支持 ====================

    /**
     * 批量重放事件重建投影状态。
     */
    public void replay(List<SessionEvent> events) {
        for (SessionEvent event : events) {
            // 直接调用对应的处理器恢复状态
            switch (event.getType()) {
                case TEXT_STARTED -> {
                    currentTextEventId = event.getEventId();
                    pendingText.putIfAbsent(currentTextEventId, new StringBuilder());
                }
                case TEXT_DELTA -> {
                    if (currentTextEventId == null) continue;
                    StringBuilder sb = pendingText.get(currentTextEventId);
                    if (sb == null) {
                        sb = new StringBuilder();
                        pendingText.put(currentTextEventId, sb);
                    }
                    if (event.getData() != null) {
                        sb.append(event.getData());
                    }
                }
                case TEXT_ENDED -> {
                    if (currentTextEventId == null) continue;
                    StringBuilder sb = pendingText.remove(currentTextEventId);
                    currentTextEventId = null;
                    if (sb != null) {
                        Message msg = Message.createUserMessage(sb.toString());
                        messagesById.put(msg.getId(), msg);
                        messageIds.add(msg.getId());
                    }
                }
                case TOOL_CALLED, TOOL_SUCCESS, TOOL_FAILED -> version.incrementAndGet();
                case AGENT_SWITCHED, COMPACTION_ENDED -> version.incrementAndGet();
            }
        }
        logger.info("[SessionProjector] Replay: " + events.size() + " events → "
            + messageIds.size() + " messages");
    }

    // ==================== 集成 ====================

    /**
     * 将投影状态同步到 Session 对象。
     */
    public void syncToSession(Session session) {
        List<Message> projectedMessages = getMessages();
        if (!projectedMessages.isEmpty()) {
            session.setMessages(projectedMessages);
        }
    }

    /**
     * 重置投影（用于恢复或清空）。
     */
    public void reset() {
        messageIds.clear();
        messagesById.clear();
        epochIds.clear();
        pendingText.clear();
        version.set(0);
        totalPromptTokens = 0;
        totalCompletionTokens = 0;
    }

    /**
     * 清理订阅。
     */
    public void close() {
        cleanupTasks.forEach(Runnable::run);
        cleanupTasks.clear();
    }

    // ==================== 工具 ====================
    // findParentEventId 已移除，改为在 onTextStarted/Ended 中维护 currentTextEventId 字段
}
