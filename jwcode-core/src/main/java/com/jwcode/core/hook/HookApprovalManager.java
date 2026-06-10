package com.jwcode.core.hook;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
 * <p>v3.0 特性：审批指纹缓存。已批准的脚本指纹自动放行，
 * 避免 AI Agent 循环中重复的只读命令反复打断用户。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // ToolExecutor 中：
 * HookApprovalManager mgr = HookApprovalManager.getInstance();
 * mgr.setWebSocketBroadcaster(sessionId -> wsServer.broadcastToSession(...));
 * CompletableFuture<Boolean> approval = mgr.requestApproval(
 *     toolName, askPayload, -1);
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
        public final String fingerprint;
        public final long createdAt;
        public final CompletableFuture<Boolean> future;
        public final String sessionId;

        ApprovalRequest(String id, String toolName, String askPayload,
                        String fingerprint, CompletableFuture<Boolean> future,
                        String sessionId) {
            this.id = id;
            this.toolName = toolName;
            this.askPayload = askPayload;
            this.fingerprint = fingerprint;
            this.createdAt = System.currentTimeMillis();
            this.future = future;
            this.sessionId = sessionId;
        }
    }

    /** pending 审批映射：approvalId → ApprovalRequest */
    private final Map<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();
    
    /** 会话级审批门闩（sessionId -> latch），防止被新请求覆盖 */
    private final Map<String, CountDownLatch> sessionLatches = new ConcurrentHashMap<>();
    
    /** 门闩超时队列（latch -> 等待线程），用于并发控制 */
    private final Object latchLock = new Object();

    /** 审批指纹缓存：fingerprint → 过期时间戳(epochMs)，60分钟TTL */
    private final Map<String, Long> fingerprintCache = new ConcurrentHashMap<>();

    /** 指纹缓存 TTL（毫秒） */
    private static final long FINGERPRINT_TTL_MS = 60 * 60 * 1000;

    /** 会话级审批门闩：sessionId → latch，有审批等待时阻止同会话其他工具执行 */
    private final Map<String, CountDownLatch> sessionApprovalGates = new ConcurrentHashMap<>();

    /** WebSocket 广播回调 */
    private volatile Consumer<ApprovalRequest> wsBroadcaster;

    /** 默认审批超时（毫秒），≤0 表示不超时一直等待 */
    private volatile long defaultTimeoutMs = -1;

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
     * 设置默认审批超时。≤0 表示不超时（一直等待用户响应）。
     */
    public void setDefaultTimeoutMs(long timeoutMs) {
        this.defaultTimeoutMs = timeoutMs;
    }

    // ==================== 指纹缓存 ====================

    /**
     * 检查某个命令行指纹是否在60分钟TTL内被批准缓存。
     */
    public boolean isFingerprintCached(String fingerprint) {
        if (fingerprint == null) return false;
        Long expiresAt = fingerprintCache.get(fingerprint);
        if (expiresAt == null) return false;
        if (System.currentTimeMillis() > expiresAt) {
            fingerprintCache.remove(fingerprint);
            return false;
        }
        return true;
    }

    /**
     * 直接缓存一个审批指纹，60分钟TTL（外部调用场景）。
     */
    public void cacheFingerprint(String fingerprint) {
        if (fingerprint != null && !fingerprint.isEmpty()) {
            fingerprintCache.put(fingerprint, System.currentTimeMillis() + FINGERPRINT_TTL_MS);
            logger.info("[HookApproval] Fingerprint cached (60min TTL): " + truncate(fingerprint, 80));
        }
    }

    /**
     * 清除所有缓存的审批指纹。
     */
    public void clearFingerprintCache() {
        int size = fingerprintCache.size();
        fingerprintCache.clear();
        logger.info("[HookApproval] Cleared " + size + " cached fingerprints");
    }

    /**
     * 标准化字符串为指纹：去首尾空白、压缩连续空白、转小写。
     */
    public static String normalizeFingerprint(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * 从 askPayload 中提取指纹，或为所有 Hook 类型生成兜底指纹。
     *
     * <p>提取优先级：
     * <ol>
     *   <li>若 askPayload 包含 {@code "\n---\n"} 分隔符，取分隔符之前的部分（兼容 BashSafetyHook 格式）</li>
     *   <li>兜底生成 {@code "toolName:normalized(askPayload)"}，确保非 bash 钩子也有指纹可缓存</li>
     * </ol>
     *
     * @param askPayload 钩子返回的 ASK 载荷
     * @param toolName   工具名称
     * @return 指纹字符串，不可能为 null
     */
    public static String extractFingerprint(String askPayload, String toolName) {
        if (askPayload != null && !askPayload.isEmpty()) {
            int separatorIdx = askPayload.indexOf("\n---\n");
            if (separatorIdx > 0) {
                return askPayload.substring(0, separatorIdx);
            }
        }
        // 兜底：生成 toolName:normalized(askPayload) 格式指纹
        String prefix = toolName != null ? toolName : "unknown";
        return prefix + ":" + normalizeFingerprint(askPayload != null ? askPayload : "");
    }

    /**
     * 等待同会话中已有的审批完成。如果当前会话没有挂起的审批，立即返回。
     * 工具执行前调用此方法，确保同一会话中不会同时有多个工具等待审批。
     */
    public void waitForPendingApproval(String sessionId) {
        if (sessionId == null) return;
        CountDownLatch gate = sessionApprovalGates.get(sessionId);
        if (gate != null) {
            logger.info("[HookApproval] Waiting for pending approval in session=" + sessionId);
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("[HookApproval] Interrupted while waiting for approval gate: " + e.getMessage());
            }
        }
    }

    // ==================== 审批流程 ====================

    /**
     * 发起审批请求。
     *
     * @param toolName   工具名称
     * @param askPayload ASK 载荷（展示给用户的提示信息）
     * @param timeoutMs  超时（毫秒），≤0 表示不超时一直等待
     * @return true=允许, false=拒绝
     */
    public CompletableFuture<Boolean> requestApproval(String toolName, String askPayload, long timeoutMs) {
        return requestApproval(toolName, askPayload, timeoutMs, null);
    }

    /**
     * 发起审批请求（带 sessionId 关联，用于连接断开时批量清理）。
     */
    public CompletableFuture<Boolean> requestApproval(String toolName, String askPayload, long timeoutMs, String sessionId) {
        String fingerprint = extractFingerprint(askPayload, toolName);
        return requestApproval(toolName, askPayload, fingerprint, timeoutMs, sessionId);
    }

    /**
     * 发起审批请求（完整参数：带显式 fingerprint）。
     *
     * @param toolName   工具名称
     * @param askPayload ASK 载荷（展示给用户的提示信息）
     * @param fingerprint 审批指纹（用于 60 分钟自动免审）；null 或空字符串表示不缓存
     * @param timeoutMs  超时（毫秒），≤0 表示不超时一直等待
     * @param sessionId  会话 ID，用于连接断开时批量清理
     * @return true=允许, false=拒绝
     */
    public CompletableFuture<Boolean> requestApproval(String toolName, String askPayload,
        String fingerprint, long timeoutMs, String sessionId) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        // Store the wrapped future (with timeout) to ensure timeout rejection works with approve/deny
        CompletableFuture<Boolean> wrappedFuture = future;
        ApprovalRequest request = new ApprovalRequest(id, toolName, askPayload, fingerprint, wrappedFuture, sessionId);

        // 创建会话级审批门闩，阻止同会话其他工具并发执行
        if (sessionId != null) {
            sessionApprovalGates.put(sessionId, new CountDownLatch(1));
        }

        // 如果设置了有效超时（>0），挂载超时自动拒绝
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        if (effectiveTimeout > 0) {
            future.orTimeout(effectiveTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    logger.warning("[HookApproval] Timeout for " + id + " (" + toolName + "), auto-deny");
                    // Do NOT remove from pendingApprovals: user may still respond after timeout
                    // Just complete the future with false; approve/deny will check completion status
                    return false;
                });
        }
        // effectiveTimeout ≤ 0: 不设超时，一直等待用户（由 session 断开等外部机制兜底）

        pendingApprovals.put(id, request);
        logger.info("[HookApproval] Pending: " + id + " | " + toolName
            + (sessionId != null ? " | session=" + sessionId : "")
            + (effectiveTimeout > 0 ? " | timeout=" + effectiveTimeout + "ms" : " | no-timeout"));

        // 通过 WebSocket 通知前端（Web 模式）
        if (wsBroadcaster != null) {
            try {
                wsBroadcaster.accept(request);
            } catch (Exception e) {
                logger.warning("[HookApproval] Broadcast failed: " + e.getMessage());
            }
            return future;
        }

        // CLI fallback：无 WebSocket 前端时，通过 stdin/stdout 交互
        logger.warning("[HookApproval] No WebSocket broadcaster, using CLI prompt");
        System.out.println("\n========== Hook Approval Required ==========");
        System.out.println("Tool: " + toolName);
        System.out.println("Payload: " + askPayload);
        System.out.print("Approve? (y/N): ");
        System.out.flush();
        try {
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            String input = scanner.nextLine();
            boolean approved = "y".equalsIgnoreCase(input.trim());
            System.out.println(approved ? "Approved." : "Denied.");
            future.complete(approved);
        } catch (Exception e) {
            logger.warning("[HookApproval] CLI prompt failed: " + e.getMessage());
            future.complete(false);
        }
        return future;
    }

    /**
     * 用户批准 — 同时将命令指纹加入60分钟TTL缓存。
     */
    public void approve(String approvalId) {
        ApprovalRequest request = pendingApprovals.remove(approvalId);
        if (request != null) {
            // 使用 ApprovalRequest 中存储的 fingerprint 直接缓存（适用所有 Hook 类型）
            if (request.fingerprint != null && !request.fingerprint.isEmpty()) {
                cacheFingerprint(request.fingerprint);
                logger.info("[HookApproval] APPROVED: " + approvalId + " (" + request.toolName
                    + ") | fingerprint cached (60min TTL): " + truncate(request.fingerprint, 80));
            } else {
                logger.info("[HookApproval] APPROVED: " + approvalId + " (" + request.toolName
                    + ") | no fingerprint to cache");
            }
            if (!request.future.isDone()) {
                request.future.complete(true);
            } else {
                logger.info("XXApproval already resolved (timed out): " + approvalId);
            }
            releaseSessionGate(request.sessionId);
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
            if (!request.future.isDone()) {
                request.future.complete(false);
            } else {
                logger.info("XXDenial arrived after resolution: " + approvalId);
            }
            releaseSessionGate(request.sessionId);
        } else {
            logger.fine("[HookApproval] Unknown approval ID: " + approvalId);
        }
    }

    /**
     * 拒绝某个 session 的所有待审批请求（连接断开时调用）。
     */
    public void denyAllForSession(String sessionId) {
        if (sessionId == null) return;
        int count = 0;
        for (var entry : pendingApprovals.entrySet()) {
            ApprovalRequest request = entry.getValue();
            if (sessionId.equals(request.sessionId)) {
                pendingApprovals.remove(entry.getKey());
                request.future.complete(false);
                count++;
                logger.info("[HookApproval] Auto-deny on disconnect: " + entry.getKey()
                    + " (" + request.toolName + ") session=" + sessionId);
            }
        }
        if (count > 0) {
            logger.info("[HookApproval] Denied " + count + " pending approvals for session=" + sessionId);
        }
        releaseSessionGate(sessionId);
    }

    /**
     * 获取待审批数量。
     */
    public int getPendingCount() {
        return pendingApprovals.size();
    }

    // ==================== 内部方法 ====================

    /**
     * 释放会话级审批门闩，唤醒等待中的同会话其他工具。
     */
    private void releaseSessionGate(String sessionId) {
        if (sessionId == null) return;
        CountDownLatch gate = sessionApprovalGates.remove(sessionId);
        if (gate != null) {
            gate.countDown();
            logger.fine("[HookApproval] Released approval gate for session=" + sessionId);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
