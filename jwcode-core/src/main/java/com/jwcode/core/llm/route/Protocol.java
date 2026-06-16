package com.jwcode.core.llm.route;

/**
 * Protocol — LLM API 协议类型。
 *
 * <p>定义了当前支持的 API 协议格式，每种协议对应不同的请求构建和响应解析策略。
 * 这是 4 轴抽象的第一轴。
 */
public enum Protocol {
    /** Anthropic Messages API */
    ANTHROPIC_MESSAGES,
    /** OpenAI Chat Completions API */
    OPENAI_CHAT,
    /** OpenAI Responses API */
    OPENAI_RESPONSES,
    /** 通用 OpenAI 兼容 API */
    OPENAI_COMPATIBLE_CHAT,
    /** AWS Bedrock Converse */
    BEDROCK_CONVERSE,
    /** Google Gemini */
    GEMINI
}
