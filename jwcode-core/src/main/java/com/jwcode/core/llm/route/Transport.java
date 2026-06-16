package com.jwcode.core.llm.route;

/**
 * Transport — LLM API 传输方式。
 *
 * <p>定义 HTTP 请求的字节帧传输方式。
 * 这是 4 轴抽象的第四轴。
 */
public enum Transport {
    /** 标准 HTTP JSON（非流式） */
    HTTP_JSON,
    /** SSE (Server-Sent Events) 流式 */
    HTTP_SSE,
    /** WebSocket 双向流 */
    WEB_SOCKET
}
