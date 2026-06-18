package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.channel.ChannelAdapter;
import com.jwcode.core.channel.ChannelConfig;
import com.jwcode.core.channel.ChannelRegistry;
import com.jwcode.core.channel.wechat.WechatChannelAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * 渠道管理 REST API。
 *
 * GET    /api/channels            — 列出所有渠道
 * POST   /api/channels            — 新建渠道
 * PUT    /api/channels/{id}       — 更新渠道
 * DELETE /api/channels/{id}       — 删除渠道
 * PATCH  /api/channels/{id}/toggle — 启用/停用
 * POST   /api/channels/{id}/test  — 测试连接
 * GET    /api/channels/{id}/wechat/qrcode        — 获取微信二维码
 * GET    /api/channels/{id}/wechat/qrcode/status — 轮询扫码状态
 */
public class ChannelsHandler implements HttpHandler {

    private static final Logger log = Logger.getLogger(ChannelsHandler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ChannelRegistry registry;

    public ChannelsHandler(ChannelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) { ex.sendResponseHeaders(200, -1); return; }

        try {
            if ("GET".equalsIgnoreCase(method))         handleGet(ex, path);
            else if ("POST".equalsIgnoreCase(method))   handlePost(ex, path);
            else if ("PUT".equalsIgnoreCase(method))    handlePut(ex, path);
            else if ("DELETE".equalsIgnoreCase(method)) handleDelete(ex, path);
            else if ("PATCH".equalsIgnoreCase(method))  handlePatch(ex, path);
            else sendError(ex, 405, "Method not allowed");
        } catch (Exception e) {
            log.severe("[ChannelsHandler] " + e.getMessage());
            sendError(ex, 500, e.getMessage());
        }
    }

    // ── GET ──────────────────────────────────────────────────

    private void handleGet(HttpExchange ex, String path) throws Exception {
        if (path.equals("/api/channels")) {
            // 列出所有渠道（脱敏 appSecret/token/encodingAESKey + 附加运行时连接状态）
            var list = registry.listAll().stream().map(c -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", c.id);
                m.put("name", c.name);
                m.put("type", c.type);
                m.put("appId", c.appId);
                m.put("appSecret", c.appSecret != null && !c.appSecret.isEmpty() ? "***" : "");
                m.put("token", c.token != null && !c.token.isEmpty() ? "***" : "");
                m.put("encodingAESKey", c.encodingAESKey != null && !c.encodingAESKey.isEmpty() ? "***" : "");
                m.put("enabled", c.enabled);
                m.put("extra", c.extra);
                m.put("connected", registry.getAdapter(c.id).map(ChannelAdapter::isConnected).orElse(false));
                return m;
            }).toList();
            sendSuccess(ex, list);
        } else if (path.matches("/api/channels/[^/]+/wechat/qrcode")) {
            String id = extractId(path, 3);
            getWechatQrCode(ex, id);
        } else if (path.matches("/api/channels/[^/]+/wechat/qrcode/status")) {
            String id = extractId(path, 3);
            String qrcode = queryParam(ex, "qrcode");
            String tempToken = queryParam(ex, "token");
            pollWechatQrStatus(ex, id, qrcode, tempToken);
        } else {
            sendError(ex, 404, "Not found");
        }
    }

    // ── POST ─────────────────────────────────────────────────

    private void handlePost(HttpExchange ex, String path) throws Exception {
        if (path.equals("/api/channels")) {
            ChannelConfig config = readBody(ex, ChannelConfig.class);
            if (config.name == null || config.name.isBlank()) { sendError(ex, 400, "name is required"); return; }
            if (config.type == null || config.type.isBlank()) { sendError(ex, 400, "type is required"); return; }
            ChannelConfig created = registry.add(config);
            sendSuccess(ex, created);
        } else if (path.matches("/api/channels/[^/]+/test")) {
            String id = extractId(path, 3);
            Optional<ChannelAdapter> adapter = registry.getAdapter(id);
            boolean ok = adapter.map(a -> a.isConnected()).orElse(false);
            sendSuccess(ex, Map.of("connected", ok));
        } else {
            sendError(ex, 404, "Not found");
        }
    }

    // ── PUT ──────────────────────────────────────────────────

    private void handlePut(HttpExchange ex, String path) throws Exception {
        String id = extractId(path, 2);
        if (id == null) { sendError(ex, 400, "Missing channel id"); return; }
        ChannelConfig config = readBody(ex, ChannelConfig.class);
        config.id = id;
        boolean ok = registry.update(config);
        if (ok) sendSuccess(ex, registry.getConfig(id).orElse(config));
        else sendError(ex, 404, "Channel not found: " + id);
    }

    // ── DELETE ───────────────────────────────────────────────

    private void handleDelete(HttpExchange ex, String path) throws Exception {
        String id = extractId(path, 2);
        if (id == null) { sendError(ex, 400, "Missing channel id"); return; }
        boolean ok = registry.remove(id);
        if (ok) sendSuccess(ex, Map.of("deleted", id));
        else sendError(ex, 404, "Channel not found: " + id);
    }

    // ── PATCH ────────────────────────────────────────────────

    private void handlePatch(HttpExchange ex, String path) throws Exception {
        if (path.matches("/api/channels/[^/]+/toggle")) {
            String id = extractId(path, 3);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = readBody(ex, Map.class);
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
            boolean ok = registry.toggle(id, enabled);
            if (ok) sendSuccess(ex, Map.of("id", id, "enabled", enabled));
            else sendError(ex, 404, "Channel not found: " + id);
        } else {
            sendError(ex, 404, "Not found");
        }
    }

    // ── WeChat QR ────────────────────────────────────────────

    private void getWechatQrCode(HttpExchange ex, String id) throws Exception {
        String tempToken = queryParam(ex, "token");
        var adapter = registry.getAdapter(id)
                .filter(a -> a instanceof WechatChannelAdapter)
                .map(a -> (WechatChannelAdapter) a);
        if (adapter.isEmpty()) { sendError(ex, 404, "Wechat channel not found or not started"); return; }
        sendSuccess(ex, adapter.get().getLoginQrCode(tempToken));
    }

    private void pollWechatQrStatus(HttpExchange ex, String id, String qrcode, String tempToken) throws Exception {
        var adapter = registry.getAdapter(id)
                .filter(a -> a instanceof WechatChannelAdapter)
                .map(a -> (WechatChannelAdapter) a);
        if (adapter.isEmpty()) { sendError(ex, 404, "Wechat channel not found"); return; }
        com.fasterxml.jackson.databind.JsonNode[] out = new com.fasterxml.jackson.databind.JsonNode[1];
        boolean confirmed = adapter.get().isConfirmedAfterPoll(tempToken, qrcode, out);
        if (confirmed) registry.persist(); // 扫码确认后落盘 bot_token
        sendSuccess(ex, out[0]);
    }

    // ── helpers ──────────────────────────────────────────────

    private <T> T readBody(HttpExchange ex, Class<T> cls) throws IOException {
        return MAPPER.readValue(ex.getRequestBody(), cls);
    }

    /** 从 /api/channels/{id}/... 提取第 n 个路径段（0-based after /api/channels/） */
    private String extractId(String path, int segmentCount) {
        String[] parts = path.split("/");
        // parts[0]="", [1]="api", [2]="channels", [3]=id
        if (parts.length > 3) return parts[3];
        return null;
    }

    private String queryParam(HttpExchange ex, String name) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String p : q.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) return kv[1];
        }
        return null;
    }

    private void sendSuccess(HttpExchange ex, Object data) throws IOException {
        ObjectNode res = MAPPER.createObjectNode();
        res.put("success", true);
        if (data != null) res.set("data", MAPPER.valueToTree(data));
        sendJson(ex, 200, res);
    }

    private void sendError(HttpExchange ex, int code, String msg) throws IOException {
        ObjectNode res = MAPPER.createObjectNode();
        res.put("success", false);
        res.put("error", msg);
        sendJson(ex, code, res);
    }

    private void sendJson(HttpExchange ex, int code, ObjectNode json) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(json);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
