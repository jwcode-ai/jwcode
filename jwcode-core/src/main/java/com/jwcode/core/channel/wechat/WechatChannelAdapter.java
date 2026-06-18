package com.jwcode.core.channel.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.channel.ChannelAdapter;
import com.jwcode.core.channel.ChannelConfig;
import com.jwcode.core.channel.InboundChannelMessage;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 企业微信渠道适配器。
 * 通过 iLink Bot API 长轮询接收消息，REST API 发送消息。
 */
public class WechatChannelAdapter implements ChannelAdapter {

    private static final Logger log = Logger.getLogger(WechatChannelAdapter.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SEND_LEN = 2000;

    private ChannelConfig config;
    private WechatApiClient apiClient;
    private final BlockingQueue<InboundChannelMessage> queue = new LinkedBlockingQueue<>(5000);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private ExecutorService poller;
    private volatile String syncBuf;
    // 账号 token（QR 扫码后存入 config.extra["bot_token"]）
    private volatile String botToken;

    @Override
    public String getChannelType() { return "wechat"; }

    @Override
    public void initialize(ChannelConfig config) {
        this.config = config;
        this.apiClient = new WechatApiClient(config.extra.getOrDefault("baseUrl", null));
        this.botToken = config.extra.getOrDefault("bot_token", config.token);
        this.syncBuf = config.extra.getOrDefault("sync_buf", null);
        if (botToken != null && !botToken.isBlank()) startPoller();
    }

    @Override
    public void shutdown() {
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
        if (botToken == null) return;
        String ctxToken = config.extra.getOrDefault("ctx_" + recipientId, null);
        try {
            // 自动分段
            int i = 0;
            while (i < text.length()) {
                String chunk = text.substring(i, Math.min(i + MAX_SEND_LEN, text.length()));
                apiClient.sendText(botToken, recipientId, chunk, ctxToken);
                i += MAX_SEND_LEN;
            }
        } catch (Exception e) {
            log.warning("[Wechat] Send failed to " + recipientId + ": " + e.getMessage());
        }
    }

    @Override
    public InboundChannelMessage poll() {
        return queue.poll();
    }

    // ── QR 码登录 ─────────────────────────────────────────────

    /** 获取登录二维码（无需 token） */
    public JsonNode getLoginQrCode(String tempToken) throws Exception {
        return apiClient.getQrCode(tempToken);
    }

    /** 轮询扫码状态；确认后更新 botToken 并启动轮询。返回 true 表示已确认（调用方应持久化配置） */
    public boolean isConfirmedAfterPoll(String tempToken, String qrcode, JsonNode[] out) throws Exception {
        JsonNode node = apiClient.getQrCodeStatus(tempToken, qrcode);
        if (out != null && out.length > 0) out[0] = node;
        String status = node.path("data").path("status").asText();
        int ret = node.path("data").path("ret").asInt(-1);
        // iLink 确认后有时 status="confirmed"，有时保持 "scaned" 但 ret=0
        boolean isConfirmed = "confirmed".equals(status) || ("scaned".equals(status) && ret == 0);
        if (isConfirmed) {
            String newToken = node.path("data").path("bot_token").asText();
            if (!newToken.isBlank()) {
                botToken = newToken;
                config.extra.put("bot_token", newToken);
                // 保存 bot/app ID 为 appId
                String botId = node.path("data").path("bot_id").asText(null);
                if (botId != null && !botId.isBlank() && (config.appId == null || config.appId.isBlank())) {
                    config.appId = botId;
                }
                String newBase = node.path("data").path("baseurl").asText();
                if (!newBase.isBlank()) config.extra.put("baseUrl", newBase);
                startPoller();
                return true;
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
        if (poller != null && !poller.isShutdown()) return;
        connected.set(true);
        poller = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wechat-poller-" + config.id);
            t.setDaemon(true);
            return t;
        });
        poller.submit(this::pollingLoop);
    }

    private void pollingLoop() {
        int failures = 0;
        while (connected.get()) {
            try {
                JsonNode resp = apiClient.getUpdates(botToken, syncBuf, 30);
                int errcode = resp.path("errcode").asInt(0);
                if (errcode != 0) {
                    log.warning("[Wechat] getUpdates errcode=" + errcode);
                    failures++;
                    if (failures >= 3) { Thread.sleep(30_000); failures = 0; }
                    continue;
                }
                failures = 0;
                String newBuf = resp.path("data").path("get_updates_buf").asText(null);
                if (newBuf != null) {
                    syncBuf = newBuf;
                    config.extra.put("sync_buf", syncBuf); // 便于重启后续接
                }
                JsonNode msgList = resp.path("data").path("msg_list");
                if (msgList.isArray()) {
                    for (JsonNode m : msgList) processMessage(m);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failures++;
                log.log(Level.WARNING, "[Wechat] Poll error", e);
                try { Thread.sleep(Math.min(2000L * failures, 30_000)); } catch (InterruptedException ie) { break; }
            }
        }
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
        queue.offer(msg);
    }
}
