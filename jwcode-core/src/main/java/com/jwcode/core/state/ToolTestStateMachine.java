package com.jwcode.core.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 工具测试状态机
 * 
 * 管理测试任务的生命周期和状态转换
 */
public class ToolTestStateMachine {

    private static final Logger logger = LoggerFactory.getLogger(ToolTestStateMachine.class);

    private TestState currentState;
    private final List<StateTransition> transitions;
    private final List<Consumer<StateTransition>> listeners;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public ToolTestStateMachine() {
        this.currentState = TestState.PENDING;
        this.transitions = new ArrayList<>();
        this.listeners = new ArrayList<>();
    }

    /**
     * 转换到新状态
     */
    public boolean transitionTo(TestState newState) {
        if (!currentState.canTransitionTo(newState)) {
            logger.warn("状态转换无效: {} -> {}", currentState, newState);
            return false;
        }

        TestState oldState = currentState;
        this.currentState = newState;

        StateTransition transition = new StateTransition(oldState, newState, LocalDateTime.now());
        transitions.add(transition);

        // 记录时间
        if (newState == TestState.RUNNING && startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (newState.isFinal()) {
            endTime = LocalDateTime.now();
        }

        // 通知监听器
        notifyListeners(transition);

        logger.info("状态转换: {} {} -> {}", transition.getFrom().getIcon(), 
            oldState.getDescription(), newState.getDescription());

        return true;
    }

    /**
     * 开始执行
     */
    public boolean start() {
        return transitionTo(TestState.RUNNING);
    }

    /**
     * 标记为成功
     */
    public boolean success() {
        return transitionTo(TestState.SUCCESS);
    }

    /**
     * 标记为失败
     */
    public boolean failed() {
        return transitionTo(TestState.FAILED);
    }

    /**
     * 标记为跳过
     */
    public boolean skip() {
        return transitionTo(TestState.SKIPPED);
    }

    /**
     * 标记为部分成功
     */
    public boolean partial() {
        return transitionTo(TestState.PARTIAL);
    }

    /**
     * 终止流程
     */
    public boolean terminate() {
        return transitionTo(TestState.TERMINATED);
    }

    /**
     * 标记为错误
     */
    public boolean error() {
        return transitionTo(TestState.ERROR);
    }

    /**
     * 添加状态变更监听器
     */
    public void addListener(Consumer<StateTransition> listener) {
        listeners.add(listener);
    }

    /**
     * 移除状态变更监听器
     */
    public void removeListener(Consumer<StateTransition> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(StateTransition transition) {
        for (Consumer<StateTransition> listener : listeners) {
            try {
                listener.accept(transition);
            } catch (Exception e) {
                logger.error("状态监听器执行失败", e);
            }
        }
    }

    // Getters
    public TestState getCurrentState() { return currentState; }
    public boolean isRunning() { return currentState == TestState.RUNNING; }
    public boolean isCompleted() { return currentState.isFinal(); }
    public boolean isSuccess() { return currentState == TestState.SUCCESS; }
    public boolean isFailed() { return currentState == TestState.FAILED; }
    public boolean isSkipped() { return currentState == TestState.SKIPPED; }
    public boolean isPartial() { return currentState == TestState.PARTIAL; }

    public List<StateTransition> getTransitions() {
        return new ArrayList<>(transitions);
    }

    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    public long getDurationMs() {
        if (startTime == null) return 0;
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }

    /**
     * 获取状态摘要
     */
    public String getSummary() {
        return String.format("状态: %s %s | 耗时: %d ms | 转换次数: %d",
            currentState.getIcon(), currentState.getDescription(),
            getDurationMs(), transitions.size());
    }

    /**
     * 状态转换记录
     */
    public static class StateTransition {
        private final TestState from;
        private final TestState to;
        private final LocalDateTime timestamp;

        public StateTransition(TestState from, TestState to, LocalDateTime timestamp) {
            this.from = from;
            this.to = to;
            this.timestamp = timestamp;
        }

        public TestState getFrom() { return from; }
        public TestState getTo() { return to; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
