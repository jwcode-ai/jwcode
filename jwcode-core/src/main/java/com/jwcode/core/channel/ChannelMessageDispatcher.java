package com.jwcode.core.channel;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.config.SystemPromptLoader;
import com.jwcode.core.llm.LLMFactory;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.model.Message;
import com.jwcode.core.permission.PermissionManagerChecker;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 渠道消息分发器：轮询所有渠道适配器的入站消息，
 * 为每个 (渠道+用户) 维护独立 Session，提交给 LLMQueryEngine 处理，
 * 并通过 StepCallback 将进度/结果回推到渠道。
 */
public class ChannelMessageDispatcher {

    private static final Logger log = Logger.getLogger(ChannelMessageDispatcher.class.getName());
    private static final int MAX_MSG_LEN = 2000; // 微信单条消息限制

    private final ToolRegistry toolRegistry;
    private final ChannelRegistry channelRegistry;
    /** (channelId:senderId) -> Session */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    /** 执行任务的线程池 */
    private final ThreadPoolExecutor taskExecutor = new ThreadPoolExecutor(
            4, 16, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            r -> { Thread t = new Thread(r, "channel-task"); t.setDaemon(true); return t; });
    private volatile boolean running = false;
    private Thread pollThread;

    public ChannelMessageDispatcher(ToolRegistry toolRegistry, ChannelRegistry channelRegistry) {
        this.toolRegistry = toolRegistry;
        this.channelRegistry = channelRegistry;
    }

    public void start() {
        running = true;
        pollThread = new Thread(this::pollLoop, "channel-poller");
        pollThread.setDaemon(true);
        pollThread.start();
        log.info("[ChannelDispatcher] Started");
    }

    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        taskExecutor.shutdown();
    }

    // ── polling ──────────────────────────────────────────────

    private void pollLoop() {
        while (running) {
            try {
                for (ChannelAdapter adapter : channelRegistry.allAdapters()) {
                    InboundChannelMessage msg = adapter.poll();
                    if (msg != null) dispatch(msg);
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.log(Level.WARNING, "[ChannelDispatcher] Poll error", e);
            }
        }
    }

    // ── dispatch ─────────────────────────────────────────────

    private void dispatch(InboundChannelMessage inbound) {
        String key = inbound.channelId + ":" + inbound.senderId;
        Session session = sessions.computeIfAbsent(key, k -> buildSession(k));
        session.addMessage(Message.createUserMessage(inbound.text));

        // 立即回执
        channelRegistry.send(inbound.channelId, inbound.senderId, "⏳ 正在处理，请稍候...");

        taskExecutor.submit(() -> {
            try {
                LLMFactory factory = LLMFactory.fromConfig((String) null); // 读 ~/.jwcode/config.yaml
                ToolExecutor toolExecutor = new ToolExecutor(
                        toolRegistry, new PermissionManagerChecker(), null, null);
                AgentRegistry agentRegistry = AgentRegistry.createDefault();
                LLMQueryEngine engine = factory.createQueryEngine(session, toolRegistry, toolExecutor, agentRegistry);

                StringBuilder contentBuf = new StringBuilder();
                engine.setStepCallback(new LLMQueryEngine.StepCallback() {
                    @Override
                    public void onStepStart(String name, String desc) {
                        channelRegistry.send(inbound.channelId, inbound.senderId, "📍 " + desc);
                    }
                    @Override
                    public void onContentChunk(String chunk) { contentBuf.append(chunk); }
                    @Override
                    public void onStepComplete(String name, String result) {}
                    @Override
                    public void onStepThinking(String name, String thought) {}
                    @Override
                    public void onStepAction(String name, String action) {}
                    @Override
                    public void onToolResult(String tool, String result, String callId) {}
                });

                LLMQueryEngine.QueryResult result = engine.queryStream(
                        inbound.text, contentBuf::append, t -> {}, tc -> {}).join();

                String response = contentBuf.length() > 0 ? contentBuf.toString()
                        : (result.getMessage() != null && result.getMessage().getContent() != null
                            ? result.getMessage().getContent().stream()
                                .filter(b -> b instanceof com.jwcode.core.model.Message.TextContent)
                                .map(b -> ((com.jwcode.core.model.Message.TextContent) b).getText())
                                .collect(java.util.stream.Collectors.joining())
                            : "✅ 任务完成");
                sendLong(inbound.channelId, inbound.senderId, response);
            } catch (Exception e) {
                log.log(Level.SEVERE, "[ChannelDispatcher] Task error", e);
                channelRegistry.send(inbound.channelId, inbound.senderId, "❌ 执行出错：" + e.getMessage());
            }
        });
    }

    /** 超长消息分段发送 */
    private void sendLong(String channelId, String recipientId, String text) {
        int i = 0;
        while (i < text.length()) {
            // 不在代码块中间切割（简单策略：按段落或按 MAX_MSG_LEN 切）
            int end = Math.min(i + MAX_MSG_LEN, text.length());
            channelRegistry.send(channelId, recipientId, text.substring(i, end));
            i = end;
        }
    }

    private Session buildSession(String key) {
        String workDir = System.getProperty("user.dir");
        Session s = new Session(key, workDir);
        String prompt = SystemPromptLoader.getSystemPrompt(null, workDir);
        if (prompt != null && !prompt.isEmpty()) s.addMessage(Message.createSystemMessage(prompt));
        return s;
    }
}
