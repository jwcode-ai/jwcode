package com.jwcode.core.channel.wechat;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 快速测试：给指定用户发一条微信消息
 */
public class SendTestMessage {
    public static void main(String[] args) throws Exception {
        String botToken = "8081e703d6d0@im.bot:060000c1a39c04a5cede089fe5fd7a415e5ba9";
        String toUserId = "o9cq804pW_5PWJKnP2gBSvgan4C4@im.wechat";
        String text = "🧪 JWCode 微信渠道修复测试 — 如果你看到这条消息，说明发送成功了！";

        WechatApiClient client = new WechatApiClient("https://ilinkai.weixin.qq.com");
        System.out.println("Sending to " + toUserId + "...");
        JsonNode resp = client.sendText(botToken, toUserId, text, null);
        System.out.println("Response: " + resp);
        System.out.println("SUCCESS! 消息已发送，请查看手机微信。");
    }
}
