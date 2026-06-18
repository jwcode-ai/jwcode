package com.jwcode.core.channel.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;

/**
 * 企业微信 iLink Bot API 客户端。
 */
public class WechatApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final Random RNG = new Random();

    private final String baseUrl;

    public WechatApiClient(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://ilinkai.weixin.qq.com";
    }

    /** 长轮询获取新消息；syncBuf 为上次返回值，首次传 null */
    public JsonNode getUpdates(String token, String syncBuf, int timeoutSec) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        if (syncBuf != null) body.put("get_updates_buf", syncBuf);
        body.put("timeout", timeoutSec);
        return post(token, "/ilink/bot/getupdates", body);
    }

    /** 发文本消息 */
    public JsonNode sendText(String token, String toUserId, String text, String contextToken) throws Exception {
        ObjectNode item = MAPPER.createObjectNode();
        item.put("type", 1);
        item.putObject("text_item").put("text", text);

        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("from_user_id", "");
        msg.put("to_user_id", toUserId);
        msg.put("message_type", 2);
        msg.put("message_state", 2);
        if (contextToken != null) msg.put("context_token", contextToken);
        msg.putArray("item_list").add(item);

        ObjectNode body = MAPPER.createObjectNode();
        body.set("msg", msg);
        return post(token, "/ilink/bot/sendmessage", body);
    }

    /** 获取登录二维码 */
    /** 获取登录二维码（无需 token） */
    public JsonNode getQrCode(String token) throws Exception {
        return get(token, "/ilink/bot/get_bot_qrcode?bot_type=3");
    }

    /** 轮询二维码扫描状态（无需 token） */
    public JsonNode getQrCodeStatus(String token, String qrcode) throws Exception {
        return get(token, "/ilink/bot/get_qrcode_status?qrcode=" + qrcode);
    }

    // ── helpers ──────────────────────────────────────────────

    private JsonNode post(String token, String path, Object body) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-WECHAT-UIN", randomUin())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .timeout(Duration.ofSeconds(40));
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return MAPPER.readTree(resp.body());
    }

    private JsonNode get(String token, String path) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-WECHAT-UIN", randomUin())
                .GET()
                .timeout(Duration.ofSeconds(15));
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return MAPPER.readTree(resp.body());
    }

    private String randomUin() {
        byte[] b = new byte[4];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
}
