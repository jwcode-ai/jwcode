import { useEffect, useCallback } from "react";
import wsService from "../services/websocket";
import { useChatStore } from "../stores/chatStore";
import { useSessionStore } from "../stores/sessionStore";
import { LogEntry } from "../types";
import { errLog } from "../stores/errorStore";
import { handlePlanMessage } from "./handlers/planHandlers";
import { handleStreamMessage } from "./handlers/streamHandlers";
import { handleSystemMessage } from "./handlers/systemHandlers";
import { handleHookAsk, handleTaskUpdate, handleStepMessage, handleTodoUpdate, handleTodoItemDone, handleSwarmEvent } from "./handlers/interactionHandlers";
import { useWorkflowStore } from "../stores/workflowStore";

const DEBUG = false;

interface UseWebSocketOptions {
  activeTab: string;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

const collectWorkflowContent = (value: unknown, out: string[] = []): string[] => {
  if (!value) return out;
  if (Array.isArray(value)) {
    value.forEach((item) => collectWorkflowContent(item, out));
    return out;
  }
  if (typeof value !== 'object') return out;

  const record = value as Record<string, unknown>;
  const content = record.content;
  if (typeof content === 'string' && content.trim()) {
    out.push(content.trim());
  }

  Object.entries(record).forEach(([key, child]) => {
    if (key !== 'content' && key !== 'structuredOutput') {
      collectWorkflowContent(child, out);
    }
  });
  return out;
};

const workflowOutputText = (output: unknown): string => {
  const contents = collectWorkflowContent(output);
  return contents[contents.length - 1] || '';
};

export function useWebSocket({ activeTab, setLogs, setUnreadLogs }: UseWebSocketOptions) {
  const chatStore = useChatStore;

  const parseJson = useCallback((rawData?: unknown) => {
    if (!rawData) return {};
    if (typeof rawData === 'object') return rawData as Record<string, unknown>;
    if (typeof rawData !== 'string') return {};
    try { return JSON.parse(rawData); } catch { return {}; }
  }, []);

  const handleWorkflowMessage = useCallback((rawType: string, rawData: unknown, sessionId: string) => {
    const data = parseJson(rawData) as Record<string, any>;
    const runId = data.runId || '';
    if (!runId) return;
    const store = useWorkflowStore.getState();
    const chat = useChatStore.getState();

    if (rawType === "workflow_error") {
      store.fail(sessionId, runId, data.error || "Workflow failed");
      if (chat.isGenerating(sessionId) || chat.isPaused(sessionId)) {
        chat.endGeneration(sessionId, data.error || "Workflow failed");
      }
      return;
    }

    const status =
      rawType === "workflow_finished" ? (data.status || "COMPLETED") :
      rawType === "workflow_started" ? (data.status || "RUNNING") :
      data.status || undefined;
    const eventType = data.type || rawType;
    const payload = data.data && typeof data.data === 'object' ? data.data : {};
    const effectKind = String(data.kind || payload.kind || '');
    const nodeId = data.nodeId || data.node_id || payload.nodeId || payload.node_id;
    const role = data.role || data.agentRole || data.agent_role || payload.role || payload.agentRole || payload.agent_role;
    const phase = data.phase || data.phaseId || payload.phase || payload.phaseId;
    const effectId = data.effectId || payload.effectId || nodeId;
    const toolName = data.toolName || data.tool_name || payload.toolName || payload.tool_name;
    const error = data.error || payload.error;
    const isAgentEffect = ["effect.scheduled", "effect.completed", "effect.failed"].includes(eventType)
      && nodeId
      && effectKind !== 'tool';
    const isToolEffect = ["effect.scheduled", "effect.completed", "effect.failed"].includes(eventType)
      && effectKind === 'tool'
      && (toolName || nodeId);
    const existingAgent = nodeId
      ? store.getAgentsForSession(sessionId).find((agent) => agent.id === String(nodeId))
      : undefined;

    store.upsert(sessionId, {
      runId,
      status,
      completedPhases: Number(data.completedPhases || 0),
      totalPhases: Number(data.totalPhases || 0),
      completedEffects: Number(data.completedEffects || 0),
      totalEffects: Number(data.totalEffects || 0),
      tokensUsed: Number(data.tokensUsed || 0),
      tokensRemaining: Number(data.tokensRemaining || 0),
      lastEventType: eventType,
      agentNode: isAgentEffect ? {
        nodeId: String(nodeId),
        role: String(role || existingAgent?.role || existingAgent?.name || 'agent'),
        phase: phase ? String(phase) : existingAgent?.phase,
        status: eventType === 'effect.failed' ? 'failed' : eventType === 'effect.completed' ? 'completed' : 'scheduled',
        tokens: data.tokensUsed != null ? Number(data.tokensUsed) : payload.tokens != null && typeof payload.tokens === 'number' ? Number(payload.tokens) : undefined,
        error: error ? String(error) : undefined,
        updatedAt: Date.now(),
      } : undefined,
    });

    if (isToolEffect) {
      const toolStatus =
        eventType === 'effect.failed' ? 'failed' :
        eventType === 'effect.completed' ? 'completed' :
        'running';
      const taskId = String(effectId || `${runId}-${toolName || nodeId}`);
      store.upsertTask(sessionId, {
        id: taskId,
        title: String(toolName || nodeId),
        description: nodeId ? String(nodeId) : undefined,
        status: toolStatus,
        source: 'workflow',
        runId,
        result: payload.artifactRef ? String(payload.artifactRef) : undefined,
        error: error ? String(error) : undefined,
        raw: data,
      });
      store.recordEvent(sessionId, {
        type: eventType,
        source: 'workflow',
        title: String(toolName || nodeId),
        status: toolStatus,
        runId,
        taskId,
        message: error ? String(error) : undefined,
        data,
      });
    }

    if (rawType === "workflow_started") {
      chat.startGeneration(sessionId);
    } else if (rawType === "workflow_finished") {
      const content = workflowOutputText(data.output);
      if (content) {
        const messages = chat.getMessages(sessionId);
        const lastMessage = messages[messages.length - 1];
        if (lastMessage?.type === 'assistant') {
          chat.appendToLastMessage(sessionId, content);
        } else {
          chat.addMessage(sessionId, {
            id: crypto.randomUUID(),
            type: 'assistant',
            content,
            timestamp: Date.now(),
          });
        }
      }
      chat.endGeneration(sessionId);
    } else if (status === "PAUSED") {
      chat.pauseGeneration(sessionId);
    } else if (status === "RUNNING" || status === "RESUMING") {
      chat.resumeGeneration(sessionId);
    }
  }, [parseJson]);

  const getLatestStep = useCallback((sessionId: string) => {
    const state = chatStore.getState();
    const msgs = state.messagesBySession[sessionId] || [];
    const lastMessage = msgs[msgs.length - 1];
    return lastMessage?.steps?.[lastMessage.steps.length - 1];
  }, [chatStore]);

  const ensureStep = useCallback((sessionId: string, type: string, stepData: any) => {
    const lastStep = getLatestStep(sessionId);
    const isCompleteEvent = type === "step_complete";
    const stepFinished = lastStep && (lastStep.status === "success" || lastStep.status === "error");

    if (!lastStep || (stepFinished && !isCompleteEvent)) {
      const stepTitle = stepData?.step || type.replace("step_", "").replace("_", " ");
      chatStore.getState().addStep(sessionId, {
        id: `step-${Date.now()}`,
        title: stepTitle,
        description: stepData?.description || "Executing...",
        status: "running",
        timestamp: Date.now(),
      });
      return getLatestStep(sessionId);
    }
    return lastStep;
  }, [getLatestStep, chatStore]);

  const handleWSMessage = useCallback((msg: { type: string; data?: string; sessionId?: string }) => {
    const rawType = msg.type;
    const rawData = msg.data;
    const activeSessionId = useSessionStore.getState().activeSessionId;
    const sessionId = msg.sessionId || activeSessionId;
    if (!sessionId) { DEBUG && console.warn("[WS] no sessionId, message dropped:", rawType); return; }

    // Hook ask
    if (rawType === "hook_ask") { handleHookAsk(rawData, sessionId); return; }
    if (rawType === "task_update") { handleTaskUpdate(rawData, sessionId); return; }

    // Agent flow events (swarm)
    if (rawType === "agent_flow_event") {
      try {
        const parsed = JSON.parse(rawData || "{}");
        handleSwarmEvent(parsed, sessionId);
      } catch (e) {
        DEBUG && console.warn("[WS] agent_flow_event parse error:", e);
      }
      return;
    }

    // Step messages
    if (rawType.startsWith("step_")) { handleStepMessage(rawType, rawData, sessionId, ensureStep); return; }

    // Plan messages
    if (rawType.startsWith("plan_")) {
      try { handlePlanMessage(rawType, rawData, sessionId); } catch (e) { errLog.error(`Failed to handle ${rawType}`, String(e)); }
      return;
    }

    if (rawType === "step_prompt") {
      try { handlePlanMessage(rawType, rawData, sessionId); } catch (e) { errLog.error(`Failed to handle ${rawType}`, String(e)); }
      return;
    }

    if (rawType === "todo_update") { handleTodoUpdate(rawData, sessionId); return; }
    if (rawType === "todo_item_done") { handleTodoItemDone(rawData, sessionId); return; }
    if (rawType === "todo_progress") return;

    if (["workflow_started", "workflow_event", "workflow_progress", "workflow_finished", "workflow_error"].includes(rawType)) {
      handleWorkflowMessage(rawType, rawData, sessionId);
      return;
    }

    // Stream messages
    if (["start", "content", "thinking", "tool_call", "tool_result", "complete", "generation_paused", "generation_resumed", "error", "context_compressed", "compaction_progress", "tombstone"].includes(rawType)) {
      handleStreamMessage(rawType, rawData, sessionId);
      return;
    }

    // System messages
    if (["token_update", "auth_required", "auth_success", "auth_failed", "log", "commands_list", "ping", "workspace_changed", "degradation_update", "doctor_result"].includes(rawType)) {
      handleSystemMessage(rawType, rawData, sessionId, { activeTab, setLogs, setUnreadLogs });
      return;
    }

    DEBUG && console.log("[WS] unhandled message type:", rawType);
  }, [activeTab, ensureStep, handleWorkflowMessage, setLogs, setUnreadLogs]);

  useEffect(() => {
    wsService.connect();
    const unsub = wsService.onMessage(handleWSMessage);
    // 断连时清理所有正在生成的会话状态 — 避免 UI 永远卡在 "生成中"
    const unsubClose = wsService.onClose(() => {
      const state = useChatStore.getState();
      for (const sessionId of state.generatingSessions) {
        state.endGeneration(sessionId, 'WebSocket disconnected');
      }
    });
    return () => { unsub(); unsubClose(); };
  }, [handleWSMessage]);
}
