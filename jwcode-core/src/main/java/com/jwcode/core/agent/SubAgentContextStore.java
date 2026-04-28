package com.jwcode.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SubAgentContextStore — 子 Agent 语义持久化层。
 *
 * <p>实现 Kimi Code 的 {@code subagents/<agent_id>/context.jsonl} 机制：</p>
 * <ul>
 *   <li><b>Append-only 写入</b>：每条消息、每次工具调用、每个状态变更都追加写入</li>
 *   <li><b>跨调用恢复</b>：JVM 重启后可以从 {@code context.jsonl} 恢复完整对话历史</li>
 *   <li><b>损坏容忍</b>：跳过无效 JSON 行，从最后一个有效状态继续</li>
 *   <li><b>元数据索引</b>：{@code agent_meta.json} 保存当前状态摘要，加速恢复</li>
 * </ul>
 */
public class SubAgentContextStore {

    private static final Logger logger = LoggerFactory.getLogger(SubAgentContextStore.class);

    private static final String CONTEXT_FILE = "context.jsonl";
    private static final String META_FILE = "agent_meta.json";
    private static final String BASE_DIR = ".jwcode/subagents";

    private final Path baseDir;
    private final ObjectMapper objectMapper;

    public SubAgentContextStore() {
        this(Paths.get(System.getProperty("user.dir"), BASE_DIR));
    }

    public SubAgentContextStore(Path baseDir) {
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            logger.error("[SubAgentContextStore] 无法创建存储目录: {}", baseDir, e);
        }
    }

    // ==================== 核心写入接口 ====================

    /**
     * 追加记录一条 Turn（用户输入 + AI 回复 + 工具调用）。
     */
    public void appendTurn(String agentId, TurnRecord record) {
        Path contextFile = getContextFile(agentId);
        try {
            ensureAgentDir(agentId);
            String jsonl = objectMapper.writeValueAsString(record);
            Files.writeString(contextFile, jsonl + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // 同步更新元数据
            updateMeta(agentId, record);

            logger.debug("[SubAgentContextStore] Turn appended | agentId={} | turnId={}",
                agentId, record.turnId);
        } catch (IOException e) {
            logger.error("[SubAgentContextStore] 写入失败 | agentId={}", agentId, e);
        }
    }

    /**
     * 追加系统事件（状态变更、压缩、错误等）。
     */
    public void appendEvent(String agentId, String eventType, Map<String, Object> payload) {
        TurnRecord record = TurnRecord.builder()
            .turnId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .recordType(RecordType.EVENT)
            .eventType(eventType)
            .eventPayload(payload)
            .build();
        appendTurn(agentId, record);
    }

    // ==================== 恢复接口 ====================

    /**
     * 从持久化存储恢复 Session。
     *
     * <p>恢复策略：</p>
     * <ol>
     *   <li>读取 {@code context.jsonl} 所有行</li>
     *   <li>过滤无效 JSON（损坏容忍）</li>
     *   <li>重建 Message 列表和元数据</li>
     *   <li>返回可继续使用的 Session</li>
     * </ol>
     */
    public Session resumeSession(String agentId, String workingDirectory) {
        Path contextFile = getContextFile(agentId);
        if (!Files.exists(contextFile)) {
            logger.warn("[SubAgentContextStore] 无持久化数据 | agentId={}", agentId);
            return null;
        }

        logger.info("[SubAgentContextStore] 开始恢复 | agentId={}", agentId);

        Session session = new Session(agentId, workingDirectory);
        int validLines = 0;
        int skippedLines = 0;
        TurnRecord lastRecord = null;

        try {
            List<String> lines = Files.readAllLines(contextFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Optional<TurnRecord> opt = parseLine(line);
                if (opt.isPresent()) {
                    TurnRecord record = opt.get();
                    applyToSession(session, record);
                    validLines++;
                    lastRecord = record;
                } else {
                    skippedLines++;
                }
            }
        } catch (IOException e) {
            logger.error("[SubAgentContextStore] 读取失败 | agentId={}", agentId, e);
        }

        logger.info("[SubAgentContextStore] 恢复完成 | agentId={} | valid={} | skipped={}",
            agentId, validLines, skippedLines);

        // 恢复最后状态
        if (lastRecord != null && lastRecord.metadata != null) {
            Object model = lastRecord.metadata.get("model");
            if (model != null) {
                session.setModel(model.toString());
            }
        }

        return session;
    }

    /**
     * 检查是否存在可恢复的持久化数据。
     */
    public boolean isResumable(String agentId) {
        return Files.exists(getContextFile(agentId));
    }

    /**
     * 获取所有可恢复的 Agent ID。
     */
    public List<String> listResumableAgents() {
        if (!Files.exists(baseDir)) return List.of();
        try (var stream = Files.list(baseDir)) {
            return stream
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .filter(id -> Files.exists(getContextFile(id)))
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("[SubAgentContextStore] 列出 Agent 失败", e);
            return List.of();
        }
    }

    // ==================== 清理接口 ====================

    public void deleteAgentContext(String agentId) {
        Path dir = baseDir.resolve(safeId(agentId));
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
                logger.info("[SubAgentContextStore] 已删除 | agentId={}", agentId);
            }
        } catch (IOException e) {
            logger.error("[SubAgentContextStore] 删除失败 | agentId={}", agentId, e);
        }
    }

    // ==================== 内部实现 ====================

    private Path getContextFile(String agentId) {
        return baseDir.resolve(safeId(agentId)).resolve(CONTEXT_FILE);
    }

    private Path getMetaFile(String agentId) {
        return baseDir.resolve(safeId(agentId)).resolve(META_FILE);
    }

    private void ensureAgentDir(String agentId) throws IOException {
        Files.createDirectories(baseDir.resolve(safeId(agentId)));
    }

    private String safeId(String agentId) {
        return agentId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private Optional<TurnRecord> parseLine(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            TurnRecord record = objectMapper.treeToValue(node, TurnRecord.class);
            return Optional.of(record);
        } catch (Exception e) {
            logger.debug("[SubAgentContextStore] 跳过损坏行: {}", line.substring(0, Math.min(80, line.length())));
            return Optional.empty();
        }
    }

    private void applyToSession(Session session, TurnRecord record) {
        switch (record.recordType) {
            case MESSAGE -> {
                if (record.messages != null) {
                    for (MessageRecord mr : record.messages) {
                        Message msg = mr.toMessage();
                        if (msg != null) session.addMessage(msg);
                    }
                }
            }
            case TOOL_CALL -> {
                // 工具调用已包含在 MESSAGE 类型的 assistant 消息中
                // 单独记录用于审计
            }
            case EVENT -> {
                if ("system_prompt_updated".equals(record.eventType) && record.eventPayload != null) {
                    Object prompt = record.eventPayload.get("systemPrompt");
                    if (prompt != null) {
                        session.addMessage(Message.createSystemMessage(prompt.toString()));
                    }
                }
            }
        }
    }

    private void updateMeta(String agentId, TurnRecord record) {
        try {
            Path metaFile = getMetaFile(agentId);
            Map<String, Object> meta = new HashMap<>();
            meta.put("agentId", agentId);
            meta.put("lastTurnId", record.turnId);
            meta.put("lastTimestamp", record.timestamp.toString());
            meta.put("lastRecordType", record.recordType.name());
            meta.put("messageCount", record.messages != null ? record.messages.size() : 0);
            meta.put("updatedAt", Instant.now().toString());

            objectMapper.writeValue(metaFile.toFile(), meta);
        } catch (IOException e) {
            logger.debug("[SubAgentContextStore] 元数据更新失败 | agentId={}", agentId);
        }
    }

    // ==================== 数据类 ====================

    public enum RecordType {
        MESSAGE,    // 对话消息
        TOOL_CALL,  // 工具调用
        EVENT       // 系统事件
    }

    public static class TurnRecord {
        public String turnId;
        public Instant timestamp;
        public RecordType recordType;
        public List<MessageRecord> messages;
        public List<ToolCallRecord> toolCalls;
        public String eventType;
        public Map<String, Object> eventPayload;
        public Map<String, Object> metadata;

        public TurnRecord() {}

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final TurnRecord r = new TurnRecord();
            public Builder turnId(String v) { r.turnId = v; return this; }
            public Builder timestamp(Instant v) { r.timestamp = v; return this; }
            public Builder recordType(RecordType v) { r.recordType = v; return this; }
            public Builder messages(List<MessageRecord> v) { r.messages = v; return this; }
            public Builder toolCalls(List<ToolCallRecord> v) { r.toolCalls = v; return this; }
            public Builder eventType(String v) { r.eventType = v; return this; }
            public Builder eventPayload(Map<String, Object> v) { r.eventPayload = v; return this; }
            public Builder metadata(Map<String, Object> v) { r.metadata = v; return this; }
            public TurnRecord build() {
                if (r.turnId == null) r.turnId = UUID.randomUUID().toString();
                if (r.timestamp == null) r.timestamp = Instant.now();
                if (r.recordType == null) r.recordType = RecordType.MESSAGE;
                return r;
            }
        }
    }

    public static class MessageRecord {
        public String role;
        public String content;
        public String reasoningContent;
        public List<ToolCallRecord> toolCalls;
        public String timestamp;

        public MessageRecord() {}

        public static MessageRecord fromMessage(Message msg) {
            MessageRecord mr = new MessageRecord();
            mr.role = msg.getRole().name().toLowerCase();
            mr.content = msg.getTextContent();
            mr.reasoningContent = msg.getReasoningContent();
            mr.timestamp = msg.getTimestamp() != null ? msg.getTimestamp().toString() : Instant.now().toString();
            if (msg.hasToolCalls()) {
                mr.toolCalls = msg.getToolCalls().stream()
                    .map(tc -> new ToolCallRecord(tc.getId(), tc.getName(), tc.getArguments()))
                    .collect(Collectors.toList());
            }
            return mr;
        }

        public Message toMessage() {
            if (role == null || content == null) return null;
            return switch (role.toLowerCase()) {
                case "user" -> Message.createUserMessage(content);
                case "assistant" -> {
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        List<Message.ToolCallInfo> tcs = toolCalls.stream()
                            .map(tc -> new Message.ToolCallInfo(tc.id, tc.name, tc.arguments))
                            .collect(Collectors.toList());
                        yield Message.createAssistantMessageWithToolCalls(content, tcs, reasoningContent);
                    } else {
                        yield Message.createAssistantMessage(content, reasoningContent);
                    }
                }
                case "system" -> Message.createSystemMessage(content);
                default -> null;
            };
        }
    }

    public static class ToolCallRecord {
        public String id;
        public String name;
        public String arguments;

        public ToolCallRecord() {}

        public ToolCallRecord(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }
}
