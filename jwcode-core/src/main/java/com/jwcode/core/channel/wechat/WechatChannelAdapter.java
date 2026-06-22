package com.jwcode.core.channel.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.channel.ChannelAdapter;
import com.jwcode.core.channel.ChannelConfig;
import com.jwcode.core.channel.InboundChannelMessage;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 企业微信渠道适配器。
 * 通过 iLink Bot API 长轮询接收消息，REST API 发送消息。
 *
 * 内置健康状态跟踪：发送连续失败 N 次后标记为不健康并熔断，
 * 避免每步工具执行都产生一次失败的发送尝试和完整堆栈日志。
 */
public class WechatChannelAdapter implements ChannelAdapter {

    private static final Logger log = Logger.getLogger(WechatChannelAdapter.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SEND_LEN = 2000;
    // 连续失败 N 次后熔断（标记为不健康）
    private static final int HEALTH_FAILURE_THRESHOLD = 5;
    // 熔断后尝试恢复的间隔（毫秒）
    private static final long HEALTH_RECOVERY_DELAY_MS = 60_000L;

    private ChannelConfig config;
    private WechatApiClient apiClient;
    private final BlockingQueue<InboundChannelMessage> queue = new LinkedBlockingQueue<>(5000);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    /** 健康状态：false 表示熔断中，不会发送消息 */
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    /** 连续发送失败的次数 */
    private final AtomicInteger consecutiveSendFailures = new AtomicInteger(0);
    /** 上次健康检查/尝试恢复的时间戳 */
    private volatile long lastHealthCheckTime = 0L;
    /** 最近一次失败的日志抑制标记（避免重复堆栈刷屏） */
    private volatile boolean suppressedLogged = false;

    private ExecutorService poller;
    private volatile String syncBuf;
    // 账号 token（QR 扫码后存入 config.extra["bot_token"]）
    private volatile String botToken;
    private volatile String botId;

    @Override
    public String getChannelType() { return "wechat"; }

    @Override
    public void initialize(ChannelConfig config) {
        this.config = config;
        this.apiClient = new WechatApiClient(config.extra.getOrDefault("baseUrl", null));
        this.botToken = config.extra.getOrDefault("bot_token", config.token);
        this.botId = config.appId;
        this.syncBuf = config.extra.getOrDefault("sync_buf", null);
        boolean hasToken = botToken != null && !botToken.isBlank();
        log.info("[Wechat] Initialized channel=" + config.name
                + " has_bot_token=" + hasToken
                + " has_sync_buf=" + (syncBuf != null && !syncBuf.isEmpty()));
        if (hasToken) startPoller();
    }

    @Override
    public void shutdown() {
        log.info("[Wechat] Shutting down channel=" + (config != null ? config.name : "?"));
        connected.set(false);
        if (poller != null) { poller.shutdownNow(); poller = null; }
        queue.clear();
    }

    @Override
    public boolean isConnected() { return connected.get(); }

    @Override
    public ChannelConfig getConfig() { return config; }

    @Override
    public void send(String recipientId, String text) {
        if (botToken == null) {
            log.fine("[Wechat] Send skipped: botToken is null, to=" + recipientId);
            return;
        }

        // 熔断检查：如果不健康，每 HEALTH_RECOVERY_DELAY_MS 尝试恢复一次
        if (!healthy.get()) {
            long now = System.currentTimeMillis();
            if (now - lastHealthCheckTime < HEALTH_RECOVERY_DELAY_MS) {
                // 熔断中，静默跳过
                return;
            }
            // 尝试恢复
            lastHealthCheckTime = now;
            log.info("[Wechat] Health check: attempting recovery send to " + recipientId);
        }

        String ctxToken = config.extra.getOrDefault("ctx_" + recipientId, null);
        int maxRetries = 2;
        int attempt = 0;
        Exception lastException = null;
        boolean anySuccess = false;
        while (attempt <= maxRetries) {
            try {
                // 自动分段
                int segments = 0;
                int i = 0;
                while (i < text.length()) {
                    String chunk = text.substring(i, Math.min(i + MAX_SEND_LEN, text.length()));
                    apiClient.sendText(botToken, recipientId, chunk, ctxToken);
                    i += MAX_SEND_LEN;
                    segments++;
                }
                anySuccess = true;
                if (attempt > 0) {
                    log.info("[Wechat] Send recovered after " + attempt + " retries to " + recipientId);
                }
                log.fine("[Wechat] Sent " + text.length() + " chars in " + segments + " segments to " + recipientId);
                break; // exit retry loop
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt <= maxRetries) {
                    long delay = 5000L * attempt; // 5s, 10s backoff
                    log.log(Level.FINE, "[Wechat] Send attempt " + attempt + "/" + maxRetries
                            + " failed to " + recipientId + ", retrying in " + delay + "ms", e);
                    try { Thread.sleep(delay); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (anySuccess) {
            // 恢复成功 → 重置健康状态
            consecutiveSendFailures.set(0);
            if (!healthy.get()) {
                healthy.set(true);
                suppressedLogged = false;
                log.info("[Wechat] Channel recovered, marked healthy again");
            }
            return;
        }

        // 发送失败：更新失败计数器，触发熔断逻辑
        int failures = consecutiveSendFailures.incrementAndGet();
        if (failures >= HEALTH_FAILURE_THRESHOLD) {
            healthy.set(false);
            lastHealthCheckTime = System.currentTimeMillis();
            if (!suppressedLogged) {
                log.log(Level.WARNING, "[Wechat] Channel marked UNHEALTHY after " + failures
                        + " consecutive failures, suppressing further send logs for "
                        + (HEALTH_RECOVERY_DELAY_MS / 1000) + "s", lastException);
                suppressedLogged = true;
            } else {
                log.log(Level.FINE, "[Wechat] Send failed (suppressed), consecutive=" + failures);
            }
        } else {
            log.log(Level.WARNING, "[Wechat] Send failed after " + (maxRetries + 1) + " attempts to "
                    + recipientId + " (consecutive=" + failures + "/" + HEALTH_FAILURE_THRESHOLD + ")", lastException);
        }
        // 不抛 RuntimeException 避免拖垮 ObservationPipeline
    }

    @Override
    public InboundChannelMessage poll() {
        return queue.poll();
    }

    // ── QR 码登录 ─────────────────────────────────────────────

    /** 获取登录二维码（无需 token） */
    public JsonNode getLoginQrCode(String tempToken) throws Exception {
        log.fine("[Wechat] Getting login QR code");
        return apiClient.getQrCode(tempToken);
    }

    /** 轮询扫码状态；确认后更新 botToken 并启动轮询。返回 true 表示已确认（调用方应持久化配置） */
    public boolean isConfirmedAfterPoll(String tempToken, String qrcode, JsonNode[] out) throws Exception {
        JsonNode node = apiClient.getQrCodeStatus(tempToken, qrcode);
        if (out != null && out.length > 0) out[0] = node;
        // iLink API 返回扁平 JSON（非 {data:{...}} 包裹），字段在根级别
        String status = node.path("status").asText("wait");
        String newToken = node.path("bot_token").asText();
        String botId = node.path("ilink_bot_id").asText();
        if (botId == null || botId.isBlank()) botId = node.path("bot_id").asText(null);

        log.info("[Wechat] QR poll status=" + status
                + " has_bot_token=" + (newToken != null && !newToken.isBlank())
                + " bot_id=" + (botId != null ? botId : "null"));

        // 参考 jwclaw: scaned 或 confirmed 均视为已确认
        boolean isConfirmed = "scaned".equals(status) || "confirmed".equals(status);
        if (isConfirmed) {
            if (newToken != null && !newToken.isBlank()) {
                botToken = newToken;
                config.extra.put("bot_token", newToken);
                // 保存 bot/app ID 为 appId
                if (botId != null && !botId.isBlank() && (config.appId == null || config.appId.isBlank())) {
                    config.appId = botId;
                    this.botId = botId;
                }
                String newBase = node.path("baseurl").asText();
                if (newBase != null && !newBase.isBlank() && !"null".equalsIgnoreCase(newBase)) {
                    config.extra.put("baseUrl", newBase);
                }

                log.info("[Wechat] QR confirmed! bot_token saved"
                        + " bot_id=" + (botId != null ? botId : "null")
                        + " baseurl=" + (newBase == null || newBase.isBlank() ? "(none)" : newBase));
                startPoller();
                return true;
            } else {
                log.warning("[Wechat] QR status confirmed but bot_token was empty/blank");
            }
        }
        return false;
    }

    /** 轮询扫码状态；确认后更新 botToken 并启动轮询 */
    public JsonNode pollQrStatus(String tempToken, String qrcode) throws Exception {
        JsonNode[] out = new JsonNode[1];
        isConfirmedAfterPoll(tempToken, qrcode, out);
        return out[0];
    }

    // ── 轮询线程 ──────────────────────────────────────────────

    private void startPoller() {
        if (poller != null && !poller.isShutdown()) {
            log.fine("[Wechat] Poller already running, skipped");
            return;
        }
        connected.set(true);
        poller = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wechat-poller-" + config.id);
            t.setDaemon(true);
            return t;
        });
        poller.submit(this::pollingLoop);
        log.info("[Wechat] Poller started for channel=" + config.name);
    }

    private void pollingLoop() {
        log.info("[Wechat] Polling loop entered");
        int failures = 0;
        while (connected.get()) {
            try {
                JsonNode resp = apiClient.getUpdates(botToken, syncBuf, 30);
                int errcode = resp.path("errcode").asInt(resp.path("ret").asInt(0));
                if (errcode != 0) {
                    log.warning("[Wechat] getUpdates errcode=" + errcode
                            + " (attempt " + (failures + 1) + ")");
                    if (errcode == -14) {
                        // 会话过期，清空游标，暂停更久
                        syncBuf = null;
                        config.extra.remove("sync_buf");
                        log.warning("[Wechat] Session expired (errcode=-14), cleared syncBuf, pausing 60s");
                        Thread.sleep(60_000);
                    } else {
                        failures++;
                        if (failures >= 3) {
                            log.warning("[Wechat] Too many failures, pausing 30s");
                            Thread.sleep(30_000);
                            failures = 0;
                        }
                    }
                    continue;
                }
                if (failures > 0) {
                    log.info("[Wechat] getUpdates recovered after " + failures + " failures");
                }
                failures = 0;
                // 轮询恢复 → 重置发送健康状态，允许下次发送尝试
                if (!healthy.get()) {
                    healthy.set(true);
                    consecutiveSendFailures.set(0);
                    log.info("[Wechat] Polling recovered, send health reset");
                }
                // 兼容两种响应格式: root.get_updates_buf 或 data.get_updates_buf
                String newBuf = resp.path("get_updates_buf").asText(null);
                if (newBuf == null) {
                    newBuf = resp.path("data").path("get_updates_buf").asText(null);
                }
                if (newBuf != null && !newBuf.isEmpty()) {
                    syncBuf = newBuf;
                    config.extra.put("sync_buf", syncBuf); // 便于重启后续接
                }
                // 兼容两种响应格式: root.msgs 或 data.msg_list
                JsonNode msgList = resp.path("msgs");
                if (!msgList.isArray()) {
                    msgList = resp.path("data").path("msg_list");
                }
                int msgCount = 0;
                if (msgList.isArray()) {
                    for (JsonNode m : msgList) {
                        processMessage(m);
                        msgCount++;
                    }
                }
                if (msgCount > 0) {
                    log.info("[Wechat] Polled " + msgCount + " messages");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[Wechat] Polling loop interrupted, exiting");
                break;
            } catch (Exception e) {
                failures++;
                log.log(Level.WARNING, "[Wechat] Poll error (attempt " + failures + ")", e);
                try { Thread.sleep(Math.min(2000L * failures, 30_000)); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("[Wechat] Polling loop exited for channel=" + config.name);
    }

    private void processMessage(JsonNode m) {
        // 取文本消息（type=1）
        JsonNode items = m.path("item_list");
        if (!items.isArray()) return;
        StringBuilder text = new StringBuilder();
        for (JsonNode item : items) {
            if (item.path("type").asInt() == 1) {
                String t = item.path("text_item").path("text").asText(null);
                if (t != null) text.append(t);
            }
        }
        if (text.isEmpty()) return;

        String senderId = m.path("from_user_id").asText(null);
        if (senderId == null) return;

        // 保存 context_token 供回复使用
        String ctxToken = m.path("context_token").asText(null);
        if (ctxToken != null) config.extra.put("ctx_" + senderId, ctxToken);

        InboundChannelMessage msg = new InboundChannelMessage();
        msg.channelId = config.id;
        msg.channelType = "wechat";
        msg.senderId = senderId;
        msg.text = text.toString().trim();

        boolean offered = queue.offer(msg);
        if (!offered) {
            log.warning("[Wechat] Message queue full, dropping message from " + senderId);
        } else {
            log.info("[Wechat] Queued message from " + senderId + ": "
                    + msg.text.substring(0, Math.min(50, msg.text.length())));
        }
    }
}
