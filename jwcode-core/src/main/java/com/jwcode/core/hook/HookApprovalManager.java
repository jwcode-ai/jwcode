package com.jwcode.core.hook;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * HookApprovalManager — Hook ASK 审批管理器。
 *
 * <p>当 Hook 返回 {@link HookDecision#ASK} 时，工具执行被挂起等待用户审批。
 * 审批管理器维护一个 pending 审批映射，前端通过 WebSocket 发送
 * {@code hook_allow}/{@code hook_deny} 来驱动审批决策。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // ToolExecutor 中：
 * HookApprovalManager mgr = HookApprovalManager.getInstance();
 * mgr.setWebSocketBroadcaster(sessionId -> wsServer.broadcastToSession(...));
 * CompletableFuture<Boolean> approval = mgr.requestApproval(
 *     toolName, askPayload, 60_000);
 * boolean approved = approval.get();
 * }</pre>
 *
 * @author JWCode Team
 * @since 2.1.1
 */
public class HookApprovalManager {

    private static final Logger logger = Logger.getLogger(HookApprovalManager.class.getName());
    private static final HookApprovalManager INSTANCE = new HookApprovalManager();

    /** 审批条目 */
    public static class ApprovalRequest {
        public final String id;
        public final String toolName;
        public final String askPayload;
        public final long createdAt;
        public final CompletableFuture<Boolean> future;

        ApprovalRequest(String id, String toolName, String askPayload,
                        CompletableFuture<Boolean> future) {
            this.id = id;
            this.toolName = toolName;
            this.askPayload = askPayload;
            this.createdAt = System.currentTimeMillis();
            this.future = future;
        }
    }

    /** pending 审批映射：approvalId → ApprovalRequest */
    private final Map<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();

    /** WebSocket 广播回调 */
    private volatile Consumer<ApprovalRequest> wsBroadcaster;

    /** 默认审批超时（毫秒），超时后自动拒绝 */
    private volatile long defaultTimeoutMs = 60_000;

    private HookApprovalManager() {}

    public static HookApprovalManager getInstance() {
        return INSTANCE;
    }

    /**
     * 设置 WebSocket 广播回调。
     * 当有新的 ASK 请求时，通过此回调通知前端。
     */
    public void setWebSocketBroadcaster(Consumer<ApprovalRequest> broadcaster) {
        this.wsBroadcaster = broadcaster;
    }

    /**
     * 设置默认审批超时。
     */
    public void setDefaultTimeoutMs(long timeoutMs) {
        this.defaultTimeoutMs = timeoutMs;
    }

    /**
     * 发起审批请求，阻塞等待用户决策。
     *
     * @param toolName   工具名称
     * @param askPayload ASK 载荷（展示给用户的提示信息）
     * @param timeoutMs  超时（毫秒），≤0 使用默认值
     * @return true=允许, false=拒绝
     */
    public CompletableFuture<Boolean> requestApproval(String toolName, String askPayload, long timeoutMs) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ApprovalRequest request = new ApprovalRequest(id, toolName, askPayload, future);

        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;

        // 设置超时自动拒绝
        future.orTimeout(effectiveTimeout, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                logger.warning("[HookApproval] Timeout for " + id + " (" + toolName + "), auto-deny");
                pendingApprovals.remove(id);
                return false;
            });

        pendingApprovals.put(id, request);
        logger.info("[HookApproval] Pending: " + id + " | " + toolName);

        // 通过 WebSocket 通知前端
        if (wsBroadcaster != null) {
            try {
                wsBroadcaster.accept(request);
            } catch (Exception e) {
                logger.warning("[HookApproval] Broadcast failed: " + e.getMessage());
            }
        }

        return future;
    }

    /**
     * 用户批准。
     */
    public void approve(String approvalId) {
        ApprovalRequest request = pendingApprovals.remove(approvalId);
        if (request != null) {
            logger.info("[HookApproval] APPROVED: " + approvalId + " (" + request.toolName + ")");
            request.future.complete(true);
        } else {
            logger.fine("[HookApproval] Unknown approval ID: " + approvalId);
        }
    }

    /**
     * 用户拒绝。
     */
    public void deny(String approvalId) {
        ApprovalRequest request = pendingApprovals.remove(approvalId);
        if (request != null) {
            logger.info("[HookApproval] DENIED: " + approvalId + " (" + request.toolName + ")");
            request.future.complete(false);
        } else {
            logger.fine("[HookApproval] Unknown approval ID: " + approvalId);
        }
    }

    /**
     * 获取待审批数量。
     */
    public int getPendingCount() {
        return pendingApprovals.size();
    }
}
