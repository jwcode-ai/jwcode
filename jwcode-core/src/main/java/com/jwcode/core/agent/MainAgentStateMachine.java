package com.jwcode.core.agent;

import com.jwcode.core.hook.HookChain;
import com.jwcode.core.hook.HookContext;
import com.jwcode.core.hook.HookDecision;
import com.jwcode.core.hook.HookResult;
import com.jwcode.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 主控 Agent 状态机
 * 
 * <p>实现 KimiCode 三层架构中的主控 Agent（前景任务）状态管理：
 * - 状态流转：Idle → Planning → Executing → Reviewing → Idle
 * - Turn 边界标记：TurnBegin / TurnEnd
 * - 状态转换事件发布
 * 
 * <p>核心职责：
 * - 维护全局会话状态和人类交互
 * - 状态机驱动 AI 执行流程
 * - 预算上限检测，超限时触发压缩或子任务拆分
 * 
 * @author JWCode Team
 * @since 2.0.0
 */
public class MainAgentStateMachine {

    private static final Logger logger = LoggerFactory.getLogger(MainAgentStateMachine.class);

    // ==================== 状态定义 ====================

    /**
     * 主控 Agent 状态枚举
     */
    public enum State {
        /** 空闲 - 等待用户输入 */
        IDLE,
        
        /** 规划中 - 分析用户意图，分解任务 */
        PLANNING,
        
        /** 执行中 - 工具调用，代码生成 */
        EXECUTING,
        
        /** 回顾中 - 检查结果，等待确认 */
        REVIEWING,
        
        /** 等待输入 - 需要用户补充信息 */
        WAITING_INPUT
    }

    /**
     * 状态转换事件
     */
    public record StateTransition(
        String sessionId,
        State from,
        State to,
        String reason,
        Instant timestamp
    ) {}

    /**
     * Turn 边界事件
     */
    public record TurnBoundary(
        String sessionId,
        TurnType type,
        Instant timestamp,
        Map<String, Object> context
    ) {
        public enum TurnType {
            TURN_BEGIN,
            TURN_END,
            BTW_BEGIN,  // 跨客户端侧问开始
            BTW_END     // 跨客户端侧问结束
        }
    }

    // ==================== 实例状态 ====================

    private final String sessionId;
    private volatile State currentState;
    private final Instant createdAt;
    private Instant lastStateChangeAt;
    
    // 状态历史
    private final CopyOnWriteArrayList<StateTransition> stateHistory;
    
    // 事件监听器
    private final CopyOnWriteArrayList<StateChangeListener> stateListeners;
    private final CopyOnWriteArrayList<TurnBoundaryListener> turnListeners;
    
    // 预算状态
    private volatile boolean budgetLow = false;
    private volatile boolean budgetCritical = false;
    private volatile boolean budgetExhausted = false;

    // Hook 链（TransitionGuard）
    private volatile HookChain hookChain;

    // ==================== 构造函数 ====================

    public MainAgentStateMachine(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.currentState = State.IDLE;
        this.createdAt = Instant.now();
        this.lastStateChangeAt = Instant.now();
        this.stateHistory = new CopyOnWriteArrayList<>();
        this.stateListeners = new CopyOnWriteArrayList<>();
        this.turnListeners = new CopyOnWriteArrayList<>();
    }

    // ==================== 核心方法 ====================

    /**
     * 状态转换（含 TransitionGuard Hook 检查）。
     *
     * <p>转换前触发 {@code STATE_TRANSITION} Hook：
     * <ul>
     *   <li>{@code ALLOW} — 正常转换</li>
     *   <li>{@code DENY} — 阻止转换，保持原状态</li>
     *   <li>{@code VOID} — 取消转换并回退</li>
     *   <li>{@code ASK} — 需要用户确认</li>
     * </ul>
     * </p>
     */
    public synchronized State transitionTo(State newState, String reason) {
        State oldState = this.currentState;
        
        if (oldState == newState) {
            logger.debug("[StateMachine] 状态未变化: {} -> {}", oldState, newState);
            return oldState;
        }

        // ──────── TransitionGuard Hook ────────
        if (hookChain != null) {
            HookResult guardResult = checkTransitionGuard(oldState, newState, reason);
            if (guardResult != null) {
                if (guardResult.getDecision() == HookDecision.DENY
                    || guardResult.getDecision() == HookDecision.VOID) {
                    logger.warn("[StateMachine] TransitionGuard {}: {} -> {} blocked | reason={}",
                        guardResult.getDecision(), oldState, newState, guardResult.getReason());
                    // 触发 STATE_ENTERED 通知（进入"被阻止"状态 = 保持原状态）
                    return oldState;
                }
                if (guardResult.getDecision() == HookDecision.ASK) {
                    logger.info("[StateMachine] TransitionGuard ASK: {} -> {} needs confirmation",
                        oldState, newState);
                    // ASK: 转换为 WAITING_INPUT 等待用户确认
                    // 记录 pendingTransition 以便确认后恢复
                }
            }
        }

        logger.info("[StateMachine] 状态转换: {} -> {} | 原因: {}", oldState, newState, reason);
        
        this.currentState = newState;
        this.lastStateChangeAt = Instant.now();

        // 记录历史
        StateTransition transition = new StateTransition(
            sessionId, oldState, newState, reason, Instant.now()
        );
        stateHistory.add(transition);

        // 通知监听器
        notifyStateChange(transition);

        return oldState;
    }

    /**
     * 获取当前状态
     */
    public State getCurrentState() {
        return currentState;
    }

    /**
     * 检查 TransitionGuard Hook。
     */
    private HookResult checkTransitionGuard(State from, State to, String reason) {
        try {
            HookContext ctx = HookContext.forStateTransition(
                sessionId, "Orchestrator",
                from.name(), to.name(), reason);
            return hookChain.execute(ctx);
        } catch (Exception e) {
            logger.warn("[StateMachine] TransitionGuard hook error: {} (fail-open)", e.getMessage());
            return null; // fail-open: allow transition
        }
    }

    /**
     * 设置 Hook 链（用于 TransitionGuard）。
     */
    public void setHookChain(HookChain hookChain) {
        this.hookChain = hookChain;
    }

    /**
     * 获取 Hook 链。
     */
    public HookChain getHookChain() {
        return hookChain;
    }

    /**
     * 获取会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取最后状态变更时间
     */
    public Instant getLastStateChangeAt() {
        return lastStateChangeAt;
    }

    /**
     * 获取状态历史
     */
    public List<StateTransition> getStateHistory() {
        return Collections.unmodifiableList(stateHistory);
    }

    // ==================== Turn 边界标记 ====================

    /**
     * 标记 Turn 开始
     */
    public void markTurnBegin(Map<String, Object> context) {
        TurnBoundary event = new TurnBoundary(
            sessionId, 
            TurnBoundary.TurnType.TURN_BEGIN, 
            Instant.now(),
            context != null ? new HashMap<>(context) : new HashMap<>()
        );
        
        logger.debug("[StateMachine] Turn Begin: sessionId={}", sessionId);
        
        for (TurnBoundaryListener listener : turnListeners) {
            try {
                listener.onTurnBegin(event);
            } catch (Exception e) {
                logger.warn("[StateMachine] TurnBegin 监听器异常", e);
            }
        }
    }

    /**
     * 标记 Turn 结束
     */
    public void markTurnEnd(Map<String, Object> context) {
        TurnBoundary event = new TurnBoundary(
            sessionId, 
            TurnBoundary.TurnType.TURN_END, 
            Instant.now(),
            context != null ? new HashMap<>(context) : new HashMap<>()
        );
        
        logger.debug("[StateMachine] Turn End: sessionId={}", sessionId);
        
        for (TurnBoundaryListener listener : turnListeners) {
            try {
                listener.onTurnEnd(event);
            } catch (Exception e) {
                logger.warn("[StateMachine] TurnEnd 监听器异常", e);
            }
        }
    }

    // ==================== 跨客户端侧问 (Btw) 边界标记 ====================

    /**
     * 标记跨客户端侧问开始（BtwBegin）
     * 用于支持多客户端/多标签页间的上下文侧问
     */
    public void markBtwBegin(Map<String, Object> context) {
        TurnBoundary event = new TurnBoundary(
            sessionId,
            TurnBoundary.TurnType.BTW_BEGIN,
            Instant.now(),
            context != null ? new HashMap<>(context) : new HashMap<>()
        );

        logger.debug("[StateMachine] Btw Begin: sessionId={}", sessionId);

        for (TurnBoundaryListener listener : turnListeners) {
            try {
                listener.onTurnBegin(event); // 复用 TurnBegin 监听器处理 BtwBegin
            } catch (Exception e) {
                logger.warn("[StateMachine] BtwBegin 监听器异常", e);
            }
        }
    }

    /**
     * 标记跨客户端侧问结束（BtwEnd）
     */
    public void markBtwEnd(Map<String, Object> context) {
        TurnBoundary event = new TurnBoundary(
            sessionId,
            TurnBoundary.TurnType.BTW_END,
            Instant.now(),
            context != null ? new HashMap<>(context) : new HashMap<>()
        );

        logger.debug("[StateMachine] Btw End: sessionId={}", sessionId);

        for (TurnBoundaryListener listener : turnListeners) {
            try {
                listener.onTurnEnd(event); // 复用 TurnEnd 监听器处理 BtwEnd
            } catch (Exception e) {
                logger.warn("[StateMachine] BtwEnd 监听器异常", e);
            }
        }
    }

    // ==================== 预算状态更新 ====================

    /**
     * 更新预算状态
     */
    public void updateBudgetStatus(double usageRatio) {
        boolean previousLow = this.budgetLow;
        boolean previousCritical = this.budgetCritical;
        boolean previousExhausted = this.budgetExhausted;

        this.budgetLow = usageRatio >= 0.7;
        this.budgetCritical = usageRatio >= 0.85;
        this.budgetExhausted = usageRatio >= 0.95;

        if (previousExhausted != this.budgetExhausted) {
            logger.warn("[StateMachine] 预算耗尽状态变化: {} -> {}", previousExhausted, this.budgetExhausted);
        }
    }

    /**
     * 是否预算过低（需要提示）
     */
    public boolean isBudgetLow() {
        return budgetLow;
    }

    /**
     * 是否预算紧张（需要压缩）
     */
    public boolean isBudgetCritical() {
        return budgetCritical;
    }

    /**
     * 是否预算耗尽（需要终止或拆分）
     */
    public boolean isBudgetExhausted() {
        return budgetExhausted;
    }

    // ==================== 状态便捷方法 ====================

    public boolean isIdle() { return currentState == State.IDLE; }
    public boolean isPlanning() { return currentState == State.PLANNING; }
    public boolean isExecuting() { return currentState == State.EXECUTING; }
    public boolean isReviewing() { return currentState == State.REVIEWING; }
    public boolean isWaitingInput() { return currentState == State.WAITING_INPUT; }
    public boolean isActive() { 
        return currentState == State.PLANNING || 
               currentState == State.EXECUTING || 
               currentState == State.REVIEWING;
    }

    // ==================== 监听器管理 ====================

    public void addStateChangeListener(StateChangeListener listener) {
        if (listener != null) {
            stateListeners.add(listener);
        }
    }

    public void removeStateChangeListener(StateChangeListener listener) {
        stateListeners.remove(listener);
    }

    public void addTurnBoundaryListener(TurnBoundaryListener listener) {
        if (listener != null) {
            turnListeners.add(listener);
        }
    }

    public void removeTurnBoundaryListener(TurnBoundaryListener listener) {
        turnListeners.remove(listener);
    }

    private void notifyStateChange(StateTransition transition) {
        for (StateChangeListener listener : stateListeners) {
            try {
                listener.onStateChanged(transition);
            } catch (Exception e) {
                logger.warn("[StateMachine] 状态变更监听器异常", e);
            }
        }
    }

    // ==================== 监听器接口 ====================

    @FunctionalInterface
    public interface StateChangeListener {
        void onStateChanged(StateTransition transition);
    }

    @FunctionalInterface
    public interface TurnBoundaryListener {
        void onTurnBegin(TurnBoundary event);
        default void onTurnEnd(TurnBoundary event) {}
    }

    // ==================== 状态机工厂 ====================

    /**
     * 为会话创建状态机
     */
    public static MainAgentStateMachine createForSession(Session session) {
        return new MainAgentStateMachine(session.getId());
    }

    // ==================== 便捷状态操作 ====================

    /**
     * 开始规划（IDLE → PLANNING）
     */
    public State beginPlanning(String reason) {
        return transitionTo(State.PLANNING, reason);
    }

    /**
     * 开始执行（PLANNING → EXECUTING）
     */
    public State beginExecution(String reason) {
        return transitionTo(State.EXECUTING, reason);
    }

    /**
     * 开始回顾（EXECUTING → REVIEWING）
     */
    public State beginReview(String reason) {
        return transitionTo(State.REVIEWING, reason);
    }

    /**
     * 等待输入（任意状态 → WAITING_INPUT）
     */
    public State waitForInput(String reason) {
        return transitionTo(State.WAITING_INPUT, reason);
    }

    /**
     * 完成或返回空闲（任意状态 → IDLE）
     */
    public State complete(String reason) {
        return transitionTo(State.IDLE, reason);
    }

    // ==================== 诊断信息 ====================

    /**
     * 生成状态机快照
     */
    public StateSnapshot snapshot() {
        return new StateSnapshot(
            sessionId,
            currentState,
            createdAt,
            lastStateChangeAt,
            stateHistory.size(),
            budgetLow,
            budgetCritical,
            budgetExhausted
        );
    }

    /**
     * 状态机快照
     */
    public record StateSnapshot(
        String sessionId,
        State state,
        Instant createdAt,
        Instant lastStateChangeAt,
        int historySize,
        boolean budgetLow,
        boolean budgetCritical,
        boolean budgetExhausted
    ) {}

    @Override
    public String toString() {
        return String.format("MainAgentStateMachine{sessionId='%s', state=%s, lastChange=%s}",
            sessionId, currentState, lastStateChangeAt);
    }
}