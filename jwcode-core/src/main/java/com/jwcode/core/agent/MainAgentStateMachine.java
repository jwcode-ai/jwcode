package com.jwcode.core.agent;

import com.jwcode.core.hook.HookChain;
import com.jwcode.core.hook.HookContext;
import com.jwcode.core.hook.HookDecision;
import com.jwcode.core.hook.HookResult;
import com.jwcode.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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

    // ASK 待确认状态转换（pendingTransition）
    private volatile PendingTransition pendingTransition;

    /** ASK 超时时间（毫秒），逾期未确认则自动取消 */
    private static final long ASK_TIMEOUT_MS = 120_000; // 2 分钟

    /**
     * 待确认的转换记录。
     */
    public record PendingTransition(
        State from,
        State to,
        String reason,
        HookResult hookResult,
        Instant createdAt
    ) {
        public boolean isExpired(long timeoutMs) {
            return Duration.between(createdAt, Instant.now()).toMillis() > timeoutMs;
        }
    }

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
            // 检查 ASK 是否已过期
            if (pendingTransition != null && pendingTransition.isExpired(ASK_TIMEOUT_MS)) {
                logger.info("[StateMachine] ASK expired, clearing pending transition: {} -> {}",
                    pendingTransition.from(), pendingTransition.to());
                pendingTransition = null;
            }

            HookResult guardResult = checkTransitionGuard(oldState, newState, reason);
            if (guardResult != null) {
                if (guardResult.getDecision() == HookDecision.DENY
                    || guardResult.getDecision() == HookDecision.VOID) {
                    logger.warn("[StateMachine] TransitionGuard {}: {} -> {} blocked | reason={}",
                        guardResult.getDecision(), oldState, newState, guardResult.getReason());
                    return oldState;
                }
                if (guardResult.getDecision() == HookDecision.ASK) {
                    logger.info("[StateMachine] TransitionGuard ASK: {} -> {} needs confirmation",
                        oldState, newState);
                    this.pendingTransition = new PendingTransition(
                        oldState, newState, reason, guardResult, Instant.now());
                    this.currentState = State.WAITING_INPUT;
                    this.lastStateChangeAt = Instant.now();
                    stateHistory.add(new StateTransition(
                        sessionId, oldState, State.WAITING_INPUT,
                        "ASK: pending " + oldState + " -> " + newState + " (" + reason + ")",
                        Instant.now()));
                    notifyStateChange(new StateTransition(
                        sessionId, oldState, State.WAITING_INPUT,
                        "ASK: waiting confirmation for " + oldState + " -> " + newState,
                        Instant.now()));
                    return oldState;
                }
                if (guardResult.getDecision() == HookDecision.MODIFY) {
                    logger.info("[StateMachine] TransitionGuard MODIFY: {} -> {} (proceeding with modified context)",
                        oldState, newState);
                    // MODIFY: 继续执行转换，但记录修改
                }
                if (guardResult.getDecision() == HookDecision.DEFER) {
                    logger.info("[StateMachine] TransitionGuard DEFER: {} -> {} deferred",
                        oldState, newState);
                    // DEFER: 推迟但不阻止，记录推迟事件后继续
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
     * 获取待确认的转换（ASK 决策后记录）。
     *
     * @return pendingTransition，如果没有则为 null
     */
    public PendingTransition getPendingTransition() {
        return pendingTransition;
    }

    /**
     * 确认或拒绝待确认的转换。
     *
     * @param approved true 确认转换，false 拒绝转换
     * @return 如果 approved=true，执行目标状态转换并返回 true；
     *         如果 approved=false，清除 pendingTransition 并返回 false
     */
    public synchronized boolean confirmPendingTransition(boolean approved) {
        PendingTransition pt = this.pendingTransition;
        if (pt == null) {
            logger.warn("[StateMachine] 没有待确认的转换");
            return false;
        }

        this.pendingTransition = null;

        if (approved) {
            logger.info("[StateMachine] ASK 已确认: {} -> {}", pt.from(), pt.to());
            // 直接执行状态转换（跳过 TransitionGuard 检查，避免循环 ASK）
            this.currentState = pt.to();
            this.lastStateChangeAt = Instant.now();
            stateHistory.add(new StateTransition(
                sessionId, pt.from(), pt.to(),
                "ASK confirmed: " + pt.reason(), Instant.now()));
            notifyStateChange(new StateTransition(
                sessionId, pt.from(), pt.to(),
                "ASK confirmed: " + pt.reason(), Instant.now()));
            return true;
        } else {
            logger.info("[StateMachine] ASK 已拒绝: 保持在 {}", pt.from());
            // 回到原始状态
            this.currentState = pt.from();
            this.lastStateChangeAt = Instant.now();
            stateHistory.add(new StateTransition(
                sessionId, State.WAITING_INPUT, pt.from(),
                "ASK denied: " + pt.reason(), Instant.now()));
            notifyStateChange(new StateTransition(
                sessionId, State.WAITING_INPUT, pt.from(),
                "ASK denied: " + pt.reason(), Instant.now()));
            return false;
        }
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
            // fail-closed: hook 异常时阻止转换，避免安全策略被绕过
            logger.warn("[StateMachine] TransitionGuard hook error: {} (fail-closed, blocking transition)", e.getMessage());
            return HookResult.errorFailClosed("TransitionGuard", e.getMessage());
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