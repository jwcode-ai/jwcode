package com.jwcode.core.channel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelConfig {
    public String id = UUID.randomUUID().toString();
    public String name;
    public String type; // "wechat" | "feishu" | "dingtalk"
    public String appId;
    public String appSecret;
    public String token;
    public String encodingAESKey;
    public boolean enabled = true;
    public Map<String, String> extra = new ConcurrentHashMap<>();

    // Getters/setters for Jackson
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getEncodingAESKey() { return encodingAESKey; }
    public void setEncodingAESKey(String encodingAESKey) { this.encodingAESKey = encodingAESKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, String> getExtra() { return extra; }
    public void setExtra(Map<String, String> extra) {
        // Jackson 反序列化时包装为 ConcurrentHashMap 保证线程安全
        if (extra instanceof ConcurrentHashMap) {
            this.extra = extra;
        } else {
            this.extra = new ConcurrentHashMap<>(extra);
        }
    }
}
