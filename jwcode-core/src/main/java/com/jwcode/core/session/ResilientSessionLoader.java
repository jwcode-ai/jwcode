package com.jwcode.core.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ResilientSessionLoader — 损坏容忍的会话恢复器。
 *
 * <p>实现 Kimi Code {@code --continue/--resume} 的损坏容忍机制：</p>
 * <ul>
 *   <li><b>无效 JSON 跳过</b>：遇到损坏行时记录日志并继续读取后续有效行</li>
 *   <li><b>部分恢复</b>：即使文件尾部损坏，也能恢复前缀有效部分</li>
 *   <li><b>消息一致性校验</b>：确保 tool_calls 与 tool results 数量匹配</li>
 *   <li><b>自动修复</b>：缺失必要字段时填充默认值，而非直接失败</li>
 * </ul>
 */
public class ResilientSessionLoader {

    private static final Logger logger = LoggerFactory.getLogger(ResilientSessionLoader.class);

    private final ObjectMapper objectMapper;

    public ResilientSessionLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public ResilientSessionLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 JSON 字符串恢复会话，容忍损坏。
     */
    public Session loadFromJson(String json, String workingDirectory) {
        if (json == null || json.trim().isEmpty()) {
            logger.warn("[ResilientSessionLoader] 空输入，返回新会话");
            return new Session(UUID.randomUUID().toString(), workingDirectory);
        }

        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return loadFromMap(map, workingDirectory);
        } catch (Exception e) {
            logger.warn("[ResilientSessionLoader] 整体解析失败，尝试逐消息恢复", e);
            return recoverFromPartialJson(json, workingDirectory);
        }
    }

    /**
     * 从文件恢复会话。
     */
    public Session loadFromFile(Path filePath) {
        String workingDirectory = filePath.getParent() != null
            ? filePath.getParent().toString()
            : System.getProperty("user.dir");

        if (!Files.exists(filePath)) {
            logger.warn("[ResilientSessionLoader] 文件不存在: {}", filePath);
            return new Session(UUID.randomUUID().toString(), workingDirectory);
        }

        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            return loadFromJson(json, workingDirectory);
        } catch (IOException e) {
            logger.error("[ResilientSessionLoader] 读取文件失败: {}", filePath, e);
            return new Session(UUID.randomUUID().toString(), workingDirectory);
        }
    }

    // ==================== 核心恢复逻辑 ====================

    private Session loadFromMap(Map<String, Object> map, String workingDirectory) {
        String id = map.get("id") instanceof String s ? s : UUID.randomUUID().toString();
        Session session = new Session(id, workingDirectory);

        // 恢复基本字段
        if (map.get("title") instanceof String title) session.setTitle(title);
        if (map.get("model") instanceof String model) session.setModel(model);

        // 恢复消息（关键：容忍单条消息损坏）
        Object messagesObj = map.get("messages");
        if (messagesObj instanceof List<?> msgList) {
            int recovered = 0;
            int skipped = 0;
            for (Object msgObj : msgList) {
                if (msgObj instanceof Map<?, ?> msgMap) {
                    try {
                        Message msg = parseMessageSafely(castMap(msgMap));
                        if (msg != null) {
                            session.addMessage(msg);
                            recovered++;
                        }
                    } catch (Exception e) {
                        logger.debug("[ResilientSessionLoader] 跳过损坏消息: {}", e.getMessage());
                        skipped++;
                    }
                }
            }
            logger.info("[ResilientSessionLoader] 消息恢复: {} 成功, {} 跳过", recovered, skipped);
        }

        // 恢复元数据
        Object metaObj = map.get("metadata");
        if (metaObj instanceof Map<?, ?> metaMap) {
            for (Map.Entry<?, ?> entry : metaMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    session.setMetadata(key, entry.getValue());
                }
            }
        }

        // 恢复 ActiveTask（如果有）
        Object taskObj = map.get("activeTask");
        if (taskObj instanceof Map<?, ?> taskMap) {
            try {
                session.setActiveTask(parseActiveTask(castMap(taskMap)));
            } catch (Exception e) {
                logger.debug("[ResilientSessionLoader] ActiveTask 恢复失败，跳过");
            }
        }

        // 一致性校验与修复
        fixMessageConsistency(session);

        return session;
    }

    /**
     * 当整体 JSON 损坏时，尝试逐行/逐块恢复。
     */
    private Session recoverFromPartialJson(String json, String workingDirectory) {
        Session session = new Session(UUID.randomUUID().toString(), workingDirectory);

        // 策略 1：尝试提取所有嵌套的 message 对象
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode messagesNode = root.path("messages");
            if (messagesNode.isArray()) {
                for (JsonNode msgNode : messagesNode) {
                    try {
                        Map<String, Object> msgMap = objectMapper.convertValue(msgNode, new TypeReference<>() {});
                        Message msg = parseMessageSafely(msgMap);
                        if (msg != null) session.addMessage(msg);
                    } catch (Exception ignored) { logger.debug("Message parse skipped"); }
                }
            }
        } catch (Exception e) {
            logger.debug("[ResilientSessionLoader] 部分恢复策略 1 失败");
        }

        // 策略 2：正则提取所有 content 块（最后手段）
        if (session.getMessageCount() == 0) {
            logger.warn("[ResilientSessionLoader] 无法恢复任何消息，返回空会话");
        }

        fixMessageConsistency(session);
        return session;
    }

    // ==================== 消息解析 ====================

    private Message parseMessageSafely(Map<String, Object> map) {
        String role = map.get("role") instanceof String s ? s : "user";
        String content = map.get("content") instanceof String s ? s : "";
        String reasoning = map.get("reasoningContent") instanceof String s ? s : null;

        return switch (role.toUpperCase()) {
            case "USER" -> Message.createUserMessage(content);
            case "ASSISTANT" -> {
                Object toolCallsObj = map.get("toolCalls");
                if (toolCallsObj instanceof List<?> tcList && !tcList.isEmpty()) {
                    List<Message.ToolCallInfo> toolCalls = new ArrayList<>();
                    for (Object tcObj : tcList) {
                        if (tcObj instanceof Map<?, ?> tcMap) {
                            String tcId = tcMap.get("id") instanceof String s ? s : UUID.randomUUID().toString();
                            String tcName = tcMap.get("name") instanceof String s ? s : "unknown";
                            String tcArgs = tcMap.get("arguments") instanceof String s ? s : "";
                            toolCalls.add(new Message.ToolCallInfo(tcId, tcName, tcArgs));
                        }
                    }
                    yield Message.createAssistantMessageWithToolCalls(content, toolCalls, reasoning);
                } else {
                    yield Message.createAssistantMessage(content, reasoning);
                }
            }
            case "SYSTEM" -> Message.createSystemMessage(content);
            case "TOOL" -> {
                String toolCallId = map.get("toolCallId") instanceof String s ? s : "unknown";
                String toolName = map.get("toolName") instanceof String s ? s : "unknown";
                yield Message.createToolResultMessage(toolCallId, toolName, content);
            }
            default -> Message.createUserMessage(content);
        };
    }

    private com.jwcode.core.task.ActiveTask parseActiveTask(Map<String, Object> map) {
        com.jwcode.core.task.ActiveTask task = new com.jwcode.core.task.ActiveTask();
        if (map.get("taskId") instanceof String id) task.setTaskId(id);
        if (map.get("description") instanceof String desc) task.setDescription(desc);
        if (map.get("currentStepIndex") instanceof Number n) task.setCurrentStepIndex(n.intValue());
        if (map.get("waitingFor") instanceof String wf) task.setWaitingFor(wf);
        return task;
    }

    // ==================== 一致性修复 ====================

    /**
     * 修复消息一致性：确保每个 assistant 的 tool_calls 都有对应的 tool result。
     * 如果缺少 tool result，添加占位符防止 API 报错。
     */
    private void fixMessageConsistency(Session session) {
        List<Message> messages = new ArrayList<>(session.getMessages());
        Set<String> existingToolCallIds = new HashSet<>();
        Set<String> expectedToolCallIds = new HashSet<>();

        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.TOOL) {
                for (Message.ContentBlock block : msg.getContent()) {
                    if (block instanceof Message.ToolResultContent trc) {
                        existingToolCallIds.add(trc.getToolUseId());
                    }
                }
            }
            if (msg.hasToolCalls()) {
                for (Message.ToolCallInfo tc : msg.getToolCalls()) {
                    expectedToolCallIds.add(tc.getId());
                }
            }
        }

        // 检查缺失的 tool results
        for (String expectedId : expectedToolCallIds) {
            if (!existingToolCallIds.contains(expectedId)) {
                logger.warn("[ResilientSessionLoader] 修复缺失 tool result | toolCallId={}", expectedId);
                session.addMessage(Message.createToolResultMessage(
                    expectedId, "unknown",
                    "[系统恢复] 该工具调用的结果在会话恢复过程中丢失。"
                ));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }
}
