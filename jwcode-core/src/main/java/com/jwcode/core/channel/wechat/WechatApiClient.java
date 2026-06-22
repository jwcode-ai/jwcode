package com.jwcode.core.channel.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 企业微信 iLink Bot API 客户端。
 */
public class WechatApiClient {

    private static final Logger log = Logger.getLogger(WechatApiClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 强制 HTTP/1.1 — iLink sendmessage 在 HTTP/2 下返回空响应 {} */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final Random RNG = new Random();
    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";

    private final String baseUrl;

    public WechatApiClient(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        log.fine("[WechatApi] baseUrl=" + this.baseUrl);
    }

    /** 长轮询获取新消息；syncBuf 为上次返回值，首次传 null/空串 */
    public JsonNode getUpdates(String token, String syncBuf, int timeoutSec) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        // get_updates_buf 首次必须传空字符串，不可为 null
        body.put("get_updates_buf", syncBuf != null ? syncBuf : "");
        body.put("timeout", timeoutSec);
        // iLink API 通常要求 base_info 在顶层
        body.putObject("base_info").put("channel_version", "1.0.3");
        log.fine("[WechatApi] getUpdates syncBuf=" + (syncBuf != null && !syncBuf.isEmpty() ? syncBuf.substring(0, Math.min(20, syncBuf.length())) + "..." : "(empty)")
                + " timeout=" + timeoutSec);
        JsonNode resp = postWithHeaders(token, "/ilink/bot/getupdates", body,
                "iLink-App-ClientVersion", "1");
        int errcode = resp.path("errcode").asInt(resp.path("ret").asInt(0));
        if (errcode != 0) {
            log.warning("[WechatApi] getUpdates failed: errcode=" + errcode
                    + " errmsg=" + resp.path("errmsg").asText()
                    + " raw=" + resp.toString());
        } else {
            // 兼容两种响应格式: root.msgs 或 data.msg_list
            JsonNode msgs = resp.path("msgs");
            int msgCount = msgs.isArray() ? msgs.size() : resp.path("data").path("msg_list").size();
            log.fine("[WechatApi] getUpdates response: errcode=" + errcode
                    + " msg_count=" + msgCount);
        }
        return resp;
    }

    /** 发文本消息 — 严格遵循 iLink Bot API 协议 */
    public JsonNode sendText(String token, String toUserId, String text, String contextToken) throws Exception {
        // client_id 必须唯一，API 用于去重
        String clientId = "jwc-" + Long.toHexString(System.nanoTime()) + "-" + RNG.nextInt(99999);

        ObjectNode item = MAPPER.createObjectNode();
        item.put("type", 1);                                           // TEXT
        item.putObject("text_item").put("text", text);

        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("from_user_id", "");                                   // 身份由 token 决定，传空串
        msg.put("to_user_id", toUserId);
        msg.put("client_id", clientId);
        msg.put("message_type", 2);                                    // BOT 发出
        msg.put("message_state", 2);                                   // FINISH（完整消息）
        if (contextToken != null) msg.put("context_token", contextToken);
        msg.putArray("item_list").add(item);
        msg.putObject("base_info").put("channel_version", "1.0.0");    // ⚡ 放 msg 内部

        ObjectNode body = MAPPER.createObjectNode();
        body.set("msg", msg);

        log.info("[WechatApi] sendText to=" + toUserId + " len=" + text.length()
                + " ctx=" + (contextToken != null ? "yes" : "no"));
        JsonNode resp = postWithHeaders(token, "/ilink/bot/sendmessage", body);
        // iLink sendmessage 成功返回 {} (空对象), 失败才返回 {errcode, errmsg}
        int errcode = resp.path("errcode").asInt(0);
        int ret = resp.path("ret").asInt(0);
        if (errcode != 0 || ret != 0) {
            String errmsg = resp.path("errmsg").asText("");
            log.warning("[WechatApi] sendText failed: errcode=" + errcode
                    + " errmsg=" + errmsg + " ret=" + ret + " raw=" + resp);
            throw new IOException("iLink sendmessage failed: errcode=" + errcode
                    + ", ret=" + ret + ", errmsg=" + errmsg + ", raw=" + resp);
        }
        log.info("[WechatApi] sendText success to=" + toUserId);
        return resp;
    }

    /** 获取登录二维码（无需 token） */
    public JsonNode getQrCode(String token) throws Exception {
        log.fine("[WechatApi] getQrCode");
        JsonNode resp = get(token, "/ilink/bot/get_bot_qrcode?bot_type=3");
        // iLink API 返回扁平 JSON: { errcode, qrcode, qrcode_img_content, ... }
        boolean hasImg = resp.path("qrcode_img_content").asText("").length() > 0;
        log.fine("[WechatApi] getQrCode response: has_qrcode=" + hasImg
                + " errcode=" + resp.path("errcode").asText());
        log.info("[WechatApi] getQrCode raw=" + resp.toString());
        return resp;
    }

    /** 轮询二维码扫描状态（无需 token）
     *  iLink API 返回扁平 JSON: { status, bot_token, bot_id, baseurl, ilink_bot_id, ... } */
    public JsonNode getQrCodeStatus(String token, String qrcode) throws Exception {
        log.fine("[WechatApi] getQrCodeStatus qrcode=" + qrcode.substring(0, Math.min(32, qrcode.length())) + "...");
        // 注意: qrcode 直接拼接 URL，不做 URLEncoder.encode（iLink API 要求原始值）
        JsonNode resp = getWithHeaders(token, "/ilink/bot/get_qrcode_status?qrcode=" + qrcode,
                "iLink-App-ClientVersion", "1");
        String status = resp.path("status").asText("wait");
        log.fine("[WechatApi] getQrCodeStatus response: status=" + status);
        log.info("[WechatApi] getQrCodeStatus raw=" + resp.toString());
        return resp;
    }

    // ── helpers ──────────────────────────────────────────────

    /** 通用 POST（只发固定套路头：AuthorizationType + X-WECHAT-UIN + Content-Type + Authorization） */
    private JsonNode post(String token, String path, Object body) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("AuthorizationType", "ilink_bot_token")
                .header("X-WECHAT-UIN", randomUin())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .timeout(Duration.ofSeconds(40));
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int sc = resp.statusCode();
        if (sc < 200 || sc >= 300) {
            log.warning("[WechatApi] POST " + path + " returned HTTP " + sc
                    + ": " + truncate(resp.body(), 200));
        }
        JsonNode json = MAPPER.readTree(resp.body());
        int errcode = json.path("errcode").asInt(json.path("ret").asInt(0));
        if (errcode != 0) {
            log.warning("[WechatApi] POST " + path + " failed: HTTP " + sc
                    + " errcode=" + errcode
                    + " errmsg=" + json.path("errmsg").asText() + " raw=" + json.toString());
        }
        return json;
    }

    /** POST 请求，支持额外请求头（key1, value1, key2, value2, ...） */
    private JsonNode postWithHeaders(String token, String path, Object body, String... extraHeaders) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("AuthorizationType", "ilink_bot_token")
                .header("X-WECHAT-UIN", randomUin())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .timeout(Duration.ofSeconds(40));
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            b.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            log.warning("[WechatApi] POST " + path + " returned " + resp.statusCode()
                    + ": " + truncate(resp.body(), 200));
        }
        String responseBody = resp.body();
        if (responseBody == null || responseBody.isBlank()) {
            throw new IOException("iLink " + path + " returned empty body, http=" + resp.statusCode());
        }
        return MAPPER.readTree(responseBody);
    }

    private JsonNode get(String token, String path) throws Exception {
        return getWithHeaders(token, path);
    }

    /** GET 请求，支持额外请求头（如 iLink-App-ClientVersion） */
    private JsonNode getWithHeaders(String token, String path, String... extraHeaders) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-WECHAT-UIN", randomUin())
                .GET()
                .timeout(Duration.ofSeconds(15));
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        // 添加额外请求头（key1, value1, key2, value2, ...）
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            b.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            log.warning("[WechatApi] GET " + path + " returned " + resp.statusCode()
                    + ": " + truncate(resp.body(), 200));
        }
        return MAPPER.readTree(resp.body());
    }

    /** X-WECHAT-UIN: base64(String(randomUint32()))，每次请求随机，防重放 */
    private String randomUin() {
        long val = RNG.nextLong() & 0xFFFFFFFFL; // 无符号 32 位
        return Base64.getEncoder().encodeToString(Long.toString(val).getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return DEFAULT_BASE_URL;
        String trimmed = baseUrl.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return DEFAULT_BASE_URL;
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
