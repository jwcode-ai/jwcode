package com.jwcode.core.service;

import com.jwcode.core.llm.LLMResponse;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 流式响应打印机 - 在 CLI 中提供打字机效果的流式输出
 * 
 * 功能说明：
 * - 实时打印 AI 生成的内容
 * - 支持打字机效果（可选）
 * - 支持显示思考过程
 * - 支持工具调用显示
 */
public class StreamingResponsePrinter {
    
    private final PrintStream out;
    private final PrintStream err;
    private final boolean typewriterEffect;
    private final int typewriterDelayMs;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    
    // 颜色代码
    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String CYAN = "\033[36m";
    private static final String GRAY = "\033[90m";
    
    public StreamingResponsePrinter() {
        this(System.out, System.err, true, 15);
    }
    
    public StreamingResponsePrinter(boolean typewriterEffect) {
        this(System.out, System.err, typewriterEffect, 15);
    }
    
    public StreamingResponsePrinter(PrintStream out, PrintStream err, 
                                   boolean typewriterEffect, int typewriterDelayMs) {
        this.out = out;
        this.err = err;
        this.typewriterEffect = typewriterEffect;
        this.typewriterDelayMs = typewriterDelayMs;
    }
    
    /**
     * 打印流式内容
     * 
     * @param contentStream 内容流（每个字符串是一块内容）
     * @return 累积的完整内容
     */
    public CompletableFuture<String> printStream(java.util.stream.Stream<String> contentStream) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder accumulated = new StringBuilder();
            
            contentStream.forEach(chunk -> {
                if (isCancelled.get()) {
                    return;
                }
                
                accumulated.append(chunk);
                printChunk(chunk);
            });
            
            out.println(); // 最后换行
            return accumulated.toString();
        });
    }
    
    /**
     * 打印单块内容
     */
    private void printChunk(String chunk) {
        if (typewriterEffect && typewriterDelayMs > 0) {
            // 打字机效果：逐字符打印
            for (char c : chunk.toCharArray()) {
                if (isCancelled.get()) {
                    return;
                }
                out.print(c);
                out.flush();
                
                try {
                    Thread.sleep(typewriterDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } else {
            // 直接打印
            out.print(chunk);
            out.flush();
        }
    }
    
    /**
     * 创建内容消费者（用于 LLMService 流式接口）
     * 
     * @return 消费字符串的 Consumer
     */
    public Consumer<String> createContentConsumer() {
        return this::printChunk;
    }
    
    /**
     * 创建思考过程消费者
     * 
     * @return 消费思考过程的 Consumer
     */
    public Consumer<String> createThinkingConsumer() {
        return thinking -> {
            if (!thinking.isEmpty()) {
                err.print(GRAY + "[思考] " + thinking + RESET);
                err.flush();
            }
        };
    }
    
    /**
     * 创建工具调用消费者
     * 
     * @return 消费工具调用事件的 Consumer
     */
    public Consumer<com.jwcode.core.llm.LLMService.StreamToolCallEvent> createToolCallConsumer() {
        return event -> {
            if (event.isComplete()) {
                out.println();
                out.println(CYAN + "⚡ 调用工具: " + event.getName() + RESET);
            }
        };
    }
    
    /**
     * 完成打印
     */
    public void complete() {
        out.println();
        out.flush();
    }
    
    /**
     * 取消打印
     */
    public void cancel() {
        isCancelled.set(true);
    }
    
    /**
     * 打印响应结果（非流式）
     */
    public void printResponse(LLMResponse response) {
        if (response.isSuccess()) {
            String content = response.getContent();
            if (content != null && !content.isEmpty()) {
                out.println(content);
            }
            
            // 打印工具调用
            if (response.hasToolCalls()) {
                out.println();
                out.println(CYAN + "工具调用:" + RESET);
                for (var toolCall : response.getToolCalls()) {
                    out.println("  - " + toolCall.getFunction().getName());
                }
            }
            
            // 打印用量信息
            if (response.getTotalTokens() > 0) {
                out.println();
                out.println(GRAY + "Token 用量: " + 
                    response.getPromptTokens() + " 提示 + " +
                    response.getCompletionTokens() + " 生成 = " +
                    response.getTotalTokens() + " 总计" + RESET);
            }
        } else {
            err.println("错误: " + response.getErrorMessage());
        }
    }
    
    /**
     * 打印带打字机效果的文本
     * 
     * @param text 要打印的文本
     * @param delayMs 每个字符的延迟（毫秒）
     */
    public static void printWithTypewriterEffect(String text, int delayMs) {
        for (char c : text.toCharArray()) {
            System.out.print(c);
            System.out.flush();
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println();
    }
    
    /**
     * 打印带打字机效果的文本（使用默认延迟）
     */
    public static void printWithTypewriterEffect(String text) {
        printWithTypewriterEffect(text, 15);
    }
    
    /**
     * 模拟流式输出（用于测试）
     * 
     * @param fullContent 完整内容
     * @param chunkSize 每块字符数
     * @param delayMs 每块延迟
     */
    public void simulateStreamOutput(String fullContent, int chunkSize, int delayMs) {
        for (int i = 0; i < fullContent.length(); i += chunkSize) {
            if (isCancelled.get()) {
                return;
            }
            
            int end = Math.min(i + chunkSize, fullContent.length());
            String chunk = fullContent.substring(i, end);
            printChunk(chunk);
            
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        complete();
    }
}
