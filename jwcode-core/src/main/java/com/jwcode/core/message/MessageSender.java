package com.jwcode.core.message;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 消息发送器接口
 */
public interface MessageSender {
    
    /**
     * 发送消息
     * @param message 消息内容
     * @param params 额外参数
     * @return 发送结果
     */
    CompletableFuture<MessageResult> send(String message, Map<String, String> params);
    
    /**
     * 检查发送器是否已配置
     */
    boolean isConfigured();
    
    /**
     * 获取发送器名称
     */
    String getName();
}
