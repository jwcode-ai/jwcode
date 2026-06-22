import { useChatStore } from "../../stores/chatStore";
import { useSessionStore } from "../../stores/sessionStore";
import { useSwarmStore, SwarmTask } from "../../stores/swarmStore";
import { useHookApprovalStore } from "../../stores/useHookApprovalStore";
import { useSettingsStore } from "../../stores/settingsStore";
import wsService from "../../services/websocket";
import { errLog } from "../../stores/errorStore";
import type { SessionTask } from "../../types";
import { normalizeBlackboardTaskStatus, useWorkflowStore } from "../../stores/workflowStore";

const DEBUG = false;

interface InteractionCtx {
  ensureStep: (sessionId: string, type: string, stepData: any) => any;
  sessionId: string;
}

export function handleHookAsk(rawData: any, sessionId: string) {
  try {
    const data = typeof rawData === "string" ? JSON.parse(rawData) : (rawData || {});
    const approvalStore = useHookApprovalStore.getState();
    const { approvalId, toolName, askPayload } = data;

    const settingsStore = useSettingsStore.getState();
    if (approvalStore.isSessionAllowed(toolName) || approvalStore.autoMode || settingsStore?.yolo?.enabled) {
      wsService.send({ type: "hook_allow", data: JSON.stringify({ approvalId }) });
      return;
    }

    const chatStore = useChatStore.getState();
    chatStore.addMessage(sessionId, {
      id: `hook-approval-${approvalId || Date.now()}`, type: "assistant",
      content: "", timestamp: Date.now(),
      hookApproval: {
        approvalId: approvalId || "", toolName: toolName || "unknown",
        askPayload: askPayload || "", status: "pending", timestamp: Date.now(),
      },
    });

    approvalStore.addApproval({
      approvalId: approvalId || "", toolName: toolName || "unknown",
      askPayload: askPayload || "", timestamp: Date.now(),
    });

    // Trigger modal open event for HookApprovalModal
    window.dispatchEvent(new CustomEvent("hook-approval-required"));
  } catch (e) {
    DEBUG && console.warn("[WS] hook_ask parse error:", e);
  }
}

export function handleTaskUpdate(rawData: any, sessionId: string) {
  try {
    const data = typeof rawData === "string" ? JSON.parse(rawData) : (rawData || {});
    const sessionStore = useSessionStore.getState();
    const board = useWorkflowStore.getState();
    const { action, taskId, data: taskData } = data;

    if (action === "created" && taskData) {
      const sessionTask: SessionTask = {
        id: taskId || `task-${Date.now()}`, title: taskData.title || "",
        completed: taskData.status === "COMPLETED", createdAt: Date.parse(taskData.createdAt) || Date.now(),
        backendId: taskId, backendStatus: taskData.status, description: taskData.description,
      };
      const existing = sessionStore.tasksBySession[sessionId] || [];
      sessionStore.setSessionTasks(sessionId, [...existing, sessionTask]);
      board.upsertTask(sessionId, {
        id: sessionTask.id,
        title: sessionTask.title,
        description: sessionTask.description,
        status: normalizeBlackboardTaskStatus(taskData.status),
        source: "task",
        progress: taskData.progress != null ? Number(taskData.progress) : undefined,
        raw: data,
      });
    } else if ((action === "updated" || action === "status_changed") && taskData) {
      sessionStore.updateTaskPlanStatus(sessionId, taskId, {
        backendStatus: taskData.status, progress: taskData.progress,
      });
      board.upsertTask(sessionId, {
        id: taskId,
        title: taskData.title || taskId,
        description: taskData.description,
        status: normalizeBlackboardTaskStatus(taskData.status),
        source: "task",
        progress: taskData.progress != null ? Number(taskData.progress) : undefined,
        raw: data,
      });
    } else if (action === "deleted") {
      const existing = sessionStore.tasksBySession[sessionId] || [];
      sessionStore.setSessionTasks(sessionId, existing.filter(t => t.id !== taskId && t.backendId !== taskId));
    }
    board.recordEvent(sessionId, {
      type: "task_update",
      source: "task",
      title: taskData?.title || taskId || "Task update",
      message: action,
      status: taskData?.status,
      taskId,
      data,
    });
  } catch (e) {
    DEBUG && console.warn("[WS] task_update parse error:", e);
  }
}

export function handleStepMessage(rawType: string, rawData: any, sessionId: string, ensureStep: InteractionCtx["ensureStep"]) {
  try {
    const stepData = JSON.parse(rawData || "{}");
    const lastStep = ensureStep(sessionId, rawType, stepData);

    if (lastStep) {
      const chatStore = useChatStore.getState();
      switch (rawType) {
        case "step_start":
          chatStore.updateStep(sessionId, lastStep.id, {
            title: stepData.step || lastStep.title,
            description: stepData.description || lastStep.description,
            status: stepData.status === "start" ? "running" : (stepData.status || "running")
          });
          break;
        case "step_thinking":
          chatStore.updateStep(sessionId, lastStep.id, { thought: stepData.thought });
          break;
        case "step_action":
          chatStore.updateStep(sessionId, lastStep.id, { action: stepData.action });
          break;
        case "step_complete":
          chatStore.updateStep(sessionId, lastStep.id, { status: stepData.status ?? "success", result: stepData.result });
          break;
      }
    }
  } catch (e) {
    errLog.warn('Failed to parse ' + rawType, String(e));
  }
}

export function handleTodoUpdate(rawData: any, sessionId: string) {
  try {
    const parsed = typeof rawData === "string" ? JSON.parse(rawData) : (rawData || {});
    let tasks: any[] = [];
    if (Array.isArray(parsed)) tasks = parsed;
    else if (parsed.tasks?.length) tasks = parsed.tasks;
    else if (parsed.items?.length) tasks = parsed.items;

    if (tasks.length > 0 && sessionId) {
      const sessionStore = useSessionStore.getState();
      const board = useWorkflowStore.getState();
      tasks.forEach((task: any) => {
        const title = task.content || task.title || task.name || "";
        if (title.trim()) {
          const existing = sessionStore.getSessionTasks(sessionId);
          const dup = existing.find(t => t.title === title.trim());
          if (!dup) {
            sessionStore.addSessionTask(sessionId, title.trim());
            const isCompleted = task.completed || task.status === "completed" || task.status === "COMPLETED" || task.status === "done" || task.activeForm === "completed";
            if (isCompleted) {
              const updated = sessionStore.getSessionTasks(sessionId);
              const added = updated.find(t => t.title === title.trim());
              if (added) sessionStore.toggleSessionTask(sessionId, added.id);
            }
          }
          const id = String(task.id || task.taskId || title.trim());
          const isCompleted = task.completed || task.status === "completed" || task.status === "COMPLETED" || task.status === "done" || task.activeForm === "completed";
          board.upsertTask(sessionId, {
            id,
            title: title.trim(),
            description: task.description,
            status: normalizeBlackboardTaskStatus(task.status, isCompleted),
            source: "todo",
            raw: task,
          });
        }
      });
      board.recordEvent(sessionId, {
        type: "todo_update",
        source: "todo",
        title: "Todo list updated",
        message: `${tasks.length} items`,
        status: "updated",
        data: parsed,
      });
    }
  } catch (e) { DEBUG && console.warn("[WS] todo_update parse error:", e); }
}

export function handleTodoItemDone(rawData: any, sessionId: string) {
  try {
    const doneData = typeof rawData === "string" ? JSON.parse(rawData) : (rawData || {});
    const taskTitle = doneData.title || doneData.name || "";
    if (taskTitle && sessionId) {
      const tasks = useSessionStore.getState().getSessionTasks(sessionId);
      const match = tasks.find(t => t.title === taskTitle && !t.completed);
      if (match) useSessionStore.getState().toggleSessionTask(sessionId, match.id);
      useWorkflowStore.getState().upsertTask(sessionId, {
        id: match?.id || taskTitle,
        title: taskTitle,
        status: "completed",
        source: "todo",
      });
      useWorkflowStore.getState().recordEvent(sessionId, {
        type: "todo_item_done",
        source: "todo",
        title: taskTitle,
        status: "completed",
        taskId: match?.id || taskTitle,
        data: doneData,
      });
    }
  } catch (e) { DEBUG && console.warn("[WS] todo_item_done parse error:", e); }
}

export function handleSwarmEvent(rawData: any, sessionId: string) {
  try {
    const data = typeof rawData === "string" ? JSON.parse(rawData) : (rawData || {});
    const swarmStore = useSwarmStore.getState();
    useWorkflowStore.getState().ingestAgentFlowEvent(sessionId, data);
    const eventType = data.eventType;
    const eventData = data.data;

    if (!eventType || !sessionId) return;

    switch (eventType) {
      case "task_start": {
        const task: SwarmTask = {
          agentId: eventData.agentId || "",
          taskId: eventData.taskId || "task-${Date.now()}",
          description: eventData.description || "",
          type: eventData.type || "EXECUTION",
          status: "running",
          priority: eventData.priority,
        };
        swarmStore.handleTaskStart(sessionId, task);
        break;
      }
      case "task_complete": {
        swarmStore.handleTaskComplete(
          sessionId,
          eventData.taskId || "",
          eventData.success !== false,
          eventData.durationMs || 0
        );
        break;
      }
      case "progress": {
        swarmStore.handleProgress(sessionId, eventData.completedTasks || 0, eventData.totalTasks || 0);
        break;
      }
    }
  } catch (e) {
    DEBUG && console.warn("[WS] swarm event parse error:", e);
  }
}
