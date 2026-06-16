package com.jwcode.core.event;

import java.util.List;
import java.util.logging.Logger;

/**
 * EventSystemFactory — 事件系统工厂。
 *
 * <p>一键创建并连接 {@link EventBus} + {@link EventStore} + {@link SessionProjector}。
 * 类似 {@code HookSystemInitializer} 的职责。
 */
public class EventSystemFactory {

    private static final Logger logger = Logger.getLogger(EventSystemFactory.class.getName());

    private EventSystemFactory() {}

    /**
     * 为指定 session 创建完整事件系统。
     *
     * @param sessionId 会话 ID
     * @return 事件系统容器
     */
    public static EventSystem create(String sessionId) {
        EventBus bus = new EventBus();
        EventStore store = new EventStore(sessionId);
        SessionProjector projector = new SessionProjector(sessionId, bus);

        // 从 store 重放已有事件恢复投影状态
        List<SessionEvent> storedEvents = store.getEvents(sessionId);
        if (!storedEvents.isEmpty()) {
            projector.replay(storedEvents);
        }

        logger.info("[EventSystemFactory] Event system initialized: session=" + sessionId);
        return new EventSystem(bus, store, projector);
    }

    /**
     * 事件系统容器。
     */
    public record EventSystem(EventBus bus, EventStore store, SessionProjector projector) {}
}
