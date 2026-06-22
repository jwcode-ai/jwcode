package com.jwcode.core.channel;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.config.SystemPromptLoader;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.llm.LLMFactory;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;
import com.jwcode.core.permission.PermissionManagerChecker;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChannelMessageDispatcher {

    private static final Logger log = Logger.getLogger(ChannelMessageDispatcher.class.getName());
    private static final int MAX_MSG_LEN = 2000;

    private final ToolRegistry toolRegistry;
    private final ChannelRegistry channelRegistry;
    private final LLMFactory llmFactory;
    private final AgentRegistry agentRegistry;
    private final ToolExecutor toolExecutor;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor taskExecutor = new ThreadPoolExecutor(
        4, 16, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
        r -> {
            Thread t = new Thread(r, "channel-task");
            t.setDaemon(true);
            return t;
        });

    private volatile boolean running;
    private Thread pollThread;

    public ChannelMessageDispatcher(ToolRegistry toolRegistry, ChannelRegistry channelRegistry) {
        this(toolRegistry, channelRegistry,
            LLMFactory.fromConfig(YamlConfigLoader.getInstance().getConfig()),
            AgentRegistry.createDefault(),
            new ToolExecutor(toolRegistry, new PermissionManagerChecker(), null, null));
    }

    ChannelMessageDispatcher(ToolRegistry toolRegistry,
                             ChannelRegistry channelRegistry,
                             LLMFactory llmFactory,
                             AgentRegistry agentRegistry,
                             ToolExecutor toolExecutor) {
        this.toolRegistry = toolRegistry;
        this.channelRegistry = channelRegistry;
        this.llmFactory = llmFactory;
        this.agentRegistry = agentRegistry;
        this.toolExecutor = toolExecutor;
        LLMFactory.setGlobalInstance(llmFactory);
    }

    void dispatchForTest(InboundChannelMessage inbound) {
        dispatch(inbound);
    }

    public void start() {
        running = true;
        pollThread = new Thread(this::pollLoop, "channel-poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        taskExecutor.shutdown();
    }

    private void pollLoop() {
        while (running) {
            try {
                for (ChannelAdapter adapter : channelRegistry.allAdapters()) {
                    InboundChannelMessage msg = adapter.poll();
                    if (msg != null) {
                        dispatch(msg);
                    }
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

    private void dispatch(InboundChannelMessage inbound) {
        String key = inbound.channelId + ":" + inbound.senderId;
        Session session = sessions.computeIfAbsent(key, k -> buildSession(k));
        session.addMessage(Message.createUserMessage(inbound.text));

        if ("wechat".equalsIgnoreCase(inbound.channelType)) {
            taskExecutor.submit(() -> handleWechatMessage(inbound, session));
            return;
        }

        channelRegistry.send(inbound.channelId, inbound.senderId, "working...");
        taskExecutor.submit(() -> runQuery(inbound, session));
    }

    private void handleWechatMessage(InboundChannelMessage inbound, Session session) {
        try {
            runQuery(inbound, session);
        } catch (Exception e) {
            log.log(Level.SEVERE, "[ChannelDispatcher] WeChat task error", e);
            safeSend(inbound.channelId, inbound.senderId, "wechat failed: " + e.getMessage());
        }
    }

    private String runQuery(InboundChannelMessage inbound, Session session) {
        LLMQueryEngine engine = createQueryEngine(session);
        StringBuilder responseBuf = new StringBuilder();
        // 标记是否已发送过状态通知，避免重复
        final boolean[] startedSent = {false};
        final boolean[] executingSent = {false};

        engine.setStepCallback(new LLMQueryEngine.StepCallback() {
            @Override public void onStepStart(String stepName, String description) {
                // 仅在第一个步骤发送"开始处理"，避免重复
                if (!startedSent[0]) {
                    startedSent[0] = true;
                    safeSend(inbound.channelId, inbound.senderId, "开始处理");
                }
            }
            @Override public void onStepThinking(String stepName, String thought) {}
            @Override public void onStepAction(String stepName, String action) {
                // 仅发送一次"正在执行"
                if (!executingSent[0]) {
                    executingSent[0] = true;
                    safeSend(inbound.channelId, inbound.senderId, "正在执行");
                }
            }
            @Override public void onStepComplete(String stepName, String result, boolean success) {}
            @Override public void onContentChunk(String chunk) {}
            @Override public void onThinkingChunk(String chunk) {}
            @Override public void onSwarmEvent(String eventType, String eventData) {}
            @Override public void onTokenUpdate(long usedTokens, long totalBudget, double usageRatio) {}
            @Override public void onContextCompressed(int originalCount, int compressedCount, long tokensSaved, String summary) {}

            @Override
            public void onToolResult(String toolName, String result, String toolCallId) {
                // 不再每步工具都向微信发送通知，避免频繁刷屏
            }

            @Override
            public void onToolCallChunk(LLMService.StreamToolCallEvent event) {
                // 不再跟踪工具耗时
            }
        });

        LLMQueryEngine.QueryResult result = engine.queryStream(
            inbound.text,
            responseBuf::append,
            t -> {},
            tc -> {}
        ).join();

        String response = result != null && result.getMessage() != null
            ? result.getMessage().getTextContent()
            : responseBuf.toString();
        if (response == null || response.isBlank()) {
            response = "done";
        }

        // 先发执行完成状态，再发最终结果
        safeSend(inbound.channelId, inbound.senderId, "执行完成");
        safeSend(inbound.channelId, inbound.senderId, response);
        return response;
    }

    private void safeSend(String channelId, String recipientId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + MAX_MSG_LEN, text.length());
            channelRegistry.send(channelId, recipientId, text.substring(i, end));
            i = end;
        }
    }

    private LLMQueryEngine createQueryEngine(Session session) {
        return llmFactory.createQueryEngine(session, toolRegistry, toolExecutor, agentRegistry);
    }

    private Session buildSession(String key) {
        String workDir = System.getProperty("user.dir");
        Session s = new Session(key, workDir);
        String prompt = SystemPromptLoader.getSystemPrompt(null, workDir);
        if (prompt != null && !prompt.isEmpty()) {
            s.addMessage(Message.createSystemMessage(prompt));
        }
        return s;
    }
}
