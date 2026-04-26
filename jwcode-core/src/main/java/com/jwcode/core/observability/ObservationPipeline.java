package com.jwcode.core.observability;

import java.util.List;

/**
 * 可观测管道 — 事件源驱动的执行引擎可观测层。
 *
 * <p>取代传统的 {@code StepCallback} 单回调模式，支持多订阅者、
 * 独立失败隔离、动态订阅/取消订阅。</p>
 */
public interface ObservationPipeline {

    /**
     * 发布事件到所有订阅者
     */
    void publish(ObservationEvent event);

    /**
     * 订阅事件
     */
    void subscribe(Observer observer);

    /**
     * 取消订阅
     */
    void unsubscribe(Observer observer);

    /**
     * 获取当前所有订阅者（用于调试）
     */
    List<Observer> getObservers();

    /**
     * 事件观察者
     */
    interface Observer {
        void onEvent(ObservationEvent event);

        default String getObserverName() {
            return getClass().getSimpleName();
        }

        /**
         * 是否对某类事件感兴趣 — 默认全部接收，子类可覆盖做过滤
         */
        default boolean isInterestedIn(Class<? extends ObservationEvent> eventType) {
            return true;
        }
    }
}
