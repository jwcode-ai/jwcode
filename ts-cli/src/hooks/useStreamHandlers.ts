/**
 * WebSocket event handlers -- streaming update batching, tool calls, plan events.
 */
import { useCallback } from 'react';
import type { JwCodeClient } from '../client.js';
import { updateAppState, getStore } from './useAppState.js';
import { createMessage, parseData, type WSMessage, type ToolCall, type Message, type PlanTask, type Step } from '../protocol.js';

function cleanArgs(raw: unknown): string {
  let s: string = typeof raw === 'string' ? raw : JSON.stringify(raw);
  for (let i = 0; i < 10; i++) {
    try {
      const obj = JSON.parse(s);
      if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
        if (typeof obj.command === 'string') return obj.command;
        if (typeof obj.command === 'object') { s = JSON.stringify(obj.command); continue; }
        return JSON.stringify(obj, null, 2);
      }
      return s;
    } catch { return s; }
  }
  return s;
}

interface ApprovalState {
  approvalId: string;
  toolName: string;
  payload: string;
}

export function useStreamHandlers(
  setShowApproval: (v: ApprovalState | null) => void,
  sessionAllowRef: { current: Set<string> },
) {
  return useCallback((client: JwCodeClient) => {
    let _pendingContent = '';
    let _pendingThinking = '';
    let _pendingToolFns: Array<(msg: Message) => Message> = [];
    let _pendingStepFns: Array<(msg: Message) => Message> = [];
    let _flushTimer: ReturnType<typeof setTimeout> | null = null;
    let _flushScheduled = false;

    function doStreamFlush() {
      _flushScheduled = false;
      const c = _pendingContent; _pendingContent = '';
      const t = _pendingThinking; _pendingThinking = '';
      const fns = _pendingToolFns; _pendingToolFns = [];
      const sFns = _pendingStepFns; _pendingStepFns = [];
      if (!c && !t && fns.length === 0 && sFns.length === 0) return;
      updateAppState(prev => {
        if (!prev.currentMessage) return prev;
        let msg = prev.currentMessage;
        if (c) msg = { ...msg, content: msg.content + c };
        if (t) msg = { ...msg, thinking: msg.thinking + t };
        for (const fn of fns) msg = fn(msg);
        for (const fn of sFns) msg = fn(msg);
        return { ...prev, currentMessage: msg };
      });
    }

    function scheduleStreamFlush() {
      if (_flushScheduled) return;
      _flushScheduled = true;
      // First flush after flushNow/start: immediate (reduces perceived latency).
      // Pending content > 100 chars: immediate (avoids visible chunking at the
      // 200ms boundary). Otherwise: 200ms throttle.
      const isFirst = !_flushTimer;
      const tooLong = _pendingContent.length > 100;
      const delay = isFirst || tooLong ? 0 : 200;
      _flushTimer = setTimeout(doStreamFlush, delay);
    }

    function flushNow() {
      if (_flushTimer) { clearTimeout(_flushTimer); _flushTimer = null; }
      _flushScheduled = false;
      doStreamFlush();
    }

    // ---- token_update throttle (100ms) ----
    let _lastTotal = 0;
    let _lastTotalTs = 0;
    let _firstTokenUpdate = true;
    let _pendingToken: Record<string, unknown> | null = null;
    let _tokenScheduled = false;

    function flushToken() {
      _tokenScheduled = false;
      const d = _pendingToken;
      if (!d) return;
      _pendingToken = null;
      const promptTokens = Number(d.promptTokens) || 0;
      const completionTokens = Number(d.completionTokens) || 0;
      const totalTokens = Number(d.totalTokens) || 0;
      const usageRatio = Number(d.usageRatio) || 0;
      if (totalTokens <= 0) return;
      const now = Date.now();
      let tokenRate = 0;
      if (_lastTotalTs > 0 && _lastTotal > 0 && now > _lastTotalTs && totalTokens > _lastTotal) {
        const deltaTokens = totalTokens - _lastTotal;
        const deltaSec = (now - _lastTotalTs) / 1000;
        const instantRate = deltaTokens / deltaSec;
        const prevRate = getStore().getState().tokenRate;
        tokenRate = prevRate > 0 ? prevRate * 0.6 + instantRate * 0.4 : instantRate;
      }
      _lastTotal = totalTokens;
      _lastTotalTs = now;
      updateAppState(prev => ({
        ...prev,
        usage: { promptTokens, completionTokens, totalTokens, usageRatio },
        modelName: (d!.model as string) || prev.modelName,
        tokenRate,
      }));
    }

    // ---- core event wiring ----

    client.on('start', () => {
      flushNow();
      const msg = createMessage('assistant');
      updateAppState(prev => ({
        ...prev,
        currentMessage: msg,
        messages: [...prev.messages, msg],
        scrollOffset: prev.scrollOffset > 0 ? prev.scrollOffset + 1 : 0,
      }));
    });

    client.on('content', (_m: WSMessage) => {
      const text = typeof _m.data === 'string' ? _m.data : (_m.data ? String(_m.data) : '');
      _pendingContent += text;
      scheduleStreamFlush();
    });

    client.on('thinking', (_m: WSMessage) => {
      _pendingThinking += typeof _m.data === 'string' ? _m.data : '';
      scheduleStreamFlush();
    });

    client.on('tool_call', (_m: WSMessage) => {
      const d = parseData(_m) as unknown as ToolCall;
      _pendingToolFns.push((msg: Message): Message => {
        let existingIdx = d.id
          ? msg.toolCalls.findIndex((t: ToolCall) => t.id === d.id)
          : -1;
        if (existingIdx < 0 && d.name) {
          existingIdx = msg.toolCalls.findIndex(
            (t: ToolCall) => t.name === d.name && t.status === 'running'
          );
        }
        const tcs = [...msg.toolCalls];
        if (existingIdx >= 0) {
          const existing = { ...tcs[existingIdx] };
          if (d.args) existing.args = cleanArgs(d.args as string | object);
          if (d.complete) existing.status = 'complete';
          if (d.result) existing.result = d.result;
          tcs[existingIdx] = existing;
        } else {
          tcs.push({
            id: d.id || (d.name ? `${d.name}-${Date.now()}` : ''),
            name: d.name || '',
            args: d.args ? cleanArgs(d.args as string | object) : undefined,
            status: d.complete ? 'complete' : 'running',
            complete: !!d.complete,
            timestamp: Date.now(),
          });
        }
        return { ...msg, toolCalls: tcs };
      });
      scheduleStreamFlush();
    });

    client.on('tool_result', (_m: WSMessage) => {
      const d = parseData(_m) as { toolName?: string; result?: string };
      _pendingToolFns.push((msg: Message): Message => {
        const tcs = [...msg.toolCalls];
        for (let i = tcs.length - 1; i >= 0; i--) {
          if (tcs[i].name === d.toolName && !tcs[i].result) {
            const tc = tcs[i];
            const duration = tc.timestamp ? Math.floor((Date.now() - tc.timestamp) / 1000) : undefined;
            tcs[i] = { ...tc, result: d.result || '', status: 'complete', duration };
            break;
          }
        }
        return { ...msg, toolCalls: tcs };
      });
      scheduleStreamFlush();
    });

    client.on('complete', () => {
      flushNow();
      updateAppState(prev => {
        if (!prev.currentMessage) return prev;
        const msgs = [...prev.messages];
        const cm = prev.currentMessage;
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === 'assistant' && msgs[i].id === cm.id) {
            msgs[i] = cm;
            break;
          }
        }
        return { ...prev, currentMessage: null, messages: msgs };
      });
    });

    client.on('error', (_m: WSMessage) => {
      const text = String(_m.data || 'Error');
      updateAppState(prev => ({
        ...prev,
        statusText: 'Error: ' + text.slice(0, 120),
      }));
    });

    // ---- step events (batched into 32ms flush) ----
    client.on('step_start', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      _pendingStepFns.push((msg: Message): Message => {
        const step: Step = {
          id: (d.id as string) || 'step-' + Date.now(),
          title: (d.title as string) || (d.description as string) || '',
          thought: d.thought as string,
          action: d.action as string,
          status: 'running',
          tools: [],
          timestamp: Date.now(),
        };
        return { ...msg, steps: [...msg.steps, step] };
      });
      scheduleStreamFlush();
    });

    client.on('step_thinking', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      _pendingStepFns.push((msg: Message): Message => {
        const steps = [...msg.steps];
        const idx = d.id ? steps.findIndex(s => s.id === d.id) : steps.length - 1;
        if (idx >= 0) {
          steps[idx] = { ...steps[idx], status: 'thinking', thought: (d.thought as string) || steps[idx].thought };
        }
        return { ...msg, steps };
      });
      scheduleStreamFlush();
    });

    client.on('step_action', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      _pendingStepFns.push((msg: Message): Message => {
        const steps = [...msg.steps];
        const idx = d.id ? steps.findIndex(s => s.id === d.id) : steps.length - 1;
        if (idx >= 0) {
          steps[idx] = { ...steps[idx], status: 'action', action: (d.action as string) || steps[idx].action };
        }
        return { ...msg, steps };
      });
      scheduleStreamFlush();
    });

    client.on('step_complete', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      _pendingStepFns.push((msg: Message): Message => {
        const steps = [...msg.steps];
        const idx = d.id ? steps.findIndex(s => s.id === d.id) : steps.length - 1;
        if (idx >= 0) {
          const dur = d.duration ? Number(d.duration) : (steps[idx].timestamp ? Math.floor((Date.now() - steps[idx].timestamp!) / 1000) : undefined);
          steps[idx] = { ...steps[idx], status: (d.status as string) === 'error' ? 'error' as const : 'success' as const, result: d.result as string, duration: dur };
        }
        return { ...msg, steps };
      });
      scheduleStreamFlush();
    });

    // ---- plan events ----
    client.on('plan_start', () => {
      flushNow();
      const msg = createMessage('assistant');
      updateAppState(prev => ({
        ...prev,
        planWaiting: false,
        currentMessage: msg,
        messages: [...prev.messages, msg],
        scrollOffset: prev.scrollOffset > 0 ? prev.scrollOffset + 1 : 0,
      }));
    });

    client.on('plan_thinking', (_m: WSMessage) => {
      const text = typeof _m.data === 'string' ? _m.data : (_m.data ? String(_m.data) : '');
      _pendingThinking += text + '\n';
      scheduleStreamFlush();
    });

    client.on('plan_tasks', () => {
      _pendingContent += '\nTask list generated\n';
      scheduleStreamFlush();
    });

    client.on('plan_error', (_m: WSMessage) => {
      const text = String(_m.data || 'Plan failed');
      updateAppState(prev => ({
        ...prev,
        planWaiting: false,
        statusText: 'Plan error: ' + text.slice(0, 120),
      }));
    });

    client.on('plan_mode_enter', () => {
      updateAppState(prev => ({ ...prev, planMode: true, statusText: 'Entered plan mode' }));
    });

    client.on('plan_mode_exit', () => {
      updateAppState(prev => ({ ...prev, planMode: false, statusText: 'Exited plan mode' }));
    });

    client.on('plan_task_start', (_m: WSMessage) => {
      const d = parseData(_m) as unknown as PlanTask;
      updateAppState(prev => {
        const tasks = [...prev.planTasks];
        const idx = tasks.findIndex(t => t.id === d.id);
        if (idx >= 0) {
          tasks[idx] = { ...tasks[idx], ...d, status: 'running', timestamp: Date.now() };
        } else {
          tasks.push({ ...d, status: 'running', timestamp: Date.now() });
        }
        return { ...prev, planTasks: tasks, pdcaPhase: prev.pdcaPhase || 'Do' };
      });
    });

    client.on('plan_task_update', (_m: WSMessage) => {
      const d = parseData(_m) as unknown as PlanTask;
      updateAppState(prev => {
        const tasks = [...prev.planTasks];
        const idx = tasks.findIndex(t => t.id === d.id);
        if (idx >= 0) { tasks[idx] = { ...tasks[idx], ...d }; }
        return { ...prev, planTasks: tasks };
      });
    });

    client.on('plan_task_result', (_m: WSMessage) => {
      const d = parseData(_m) as unknown as PlanTask;
      updateAppState(prev => {
        const tasks = [...prev.planTasks];
        const idx = tasks.findIndex(t => t.id === d.id);
        if (idx >= 0) {
          const duration = tasks[idx].timestamp ? Math.floor((Date.now() - tasks[idx].timestamp!) / 1000) : undefined;
          tasks[idx] = { ...tasks[idx], ...d, status: d.status || 'completed', duration };
        } else {
          tasks.push({ ...d });
        }
        return { ...prev, planTasks: tasks };
      });
    });

    client.on('plan_complete', (_m: WSMessage) => {
      flushNow();
      const status = (_m as any).status as string | undefined;
      const planText = typeof _m.data === 'string' ? _m.data : '';
      updateAppState(prev => {
        if (!prev.currentMessage) return prev;
        const msgs = [...prev.messages];
        const cm = prev.currentMessage;
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === 'assistant' && msgs[i].id === cm.id) {
            msgs[i] = { ...cm, content: planText || 'Plan complete.' };
            break;
          }
        }
        return {
          ...prev,
          currentMessage: null,
          messages: msgs,
          planWaiting: status === 'waiting_confirm',
        };
      });
    });

    // ---- token & context events ----
    client.on('token_update', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      _firstTokenUpdate = false;
      const totalTokens = Number(d.totalTokens) || 0;
      if (totalTokens > 0) {
        _pendingToken = d;
        if (!_tokenScheduled) {
          _tokenScheduled = true;
          setTimeout(flushToken, 500);
        }
      }
    });

    client.on('compaction_progress', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      updateAppState(prev => ({
        ...prev,
        compactionProgress: {
          stage: String(d.stage || ''),
          percent: Number(d.percent) || 0,
          message: String(d.message || ''),
        },
      }));
    });

    client.on('context_compressed', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      const orig = Number(d.originalCount) || 0;
      const comp = Number(d.compressedCount) || 0;
      const saved = Number(d.tokensSaved) || 0;
      const tokensStr = saved >= 1000 ? (saved / 1000).toFixed(1) + 'K' : String(saved);
      updateAppState(prev => ({
        ...prev,
        statusText: 'Context compressed ' + orig + ' to ' + comp + ' messages, freed ' + tokensStr + ' tokens',
        usage: { ...prev.usage, usageRatio: Math.max(0, prev.usage.usageRatio - 0.15) },
        compactionProgress: null,
      }));
    });

    // ---- hook & approval ----
    client.on('hook_ask', (_m: WSMessage) => {
      const d = parseData(_m);
      const approvalId = (d.approvalId as string) || '';
      const toolName = (d.toolName as string) || '';
      if (getStore().getState().autoMode || sessionAllowRef.current.has(toolName)) {
        client.approveHook(approvalId);
        return;
      }
      setShowApproval({
        approvalId,
        toolName: (d.toolName as string) || '',
        payload: (d.askPayload as string) || (d.payload as string) || JSON.stringify(d),
      });
    });

    // ---- doctor ----
    client.on('doctor_result', (_m: WSMessage) => {
      const text = String(_m.data || '');
      const truncated = text.length > 300 ? text.slice(0, 300) + '...' : text;
      const msg = createMessage('assistant', truncated);
      updateAppState(prev => ({
        ...prev,
        messages: [...prev.messages, msg],
        statusText: 'Doctor diagnosis complete',
      }));
    });

    // ---- degradation ----
    client.on('degradation_update', (_m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof _m.data === 'string') { try { d = JSON.parse(_m.data); } catch {} }
      else if (_m.data && typeof _m.data === 'object') { d = _m.data as Record<string, unknown>; }
      updateAppState(prev => ({
        ...prev,
        degradation: {
          active: (d.active as boolean) || false,
          retryCount: Number(d.retryCount) || 0,
          maxRetries: Number(d.maxRetries) || 0,
          mode: ((d.mode as string) || 'normal') as 'normal' | 'retrying' | 'alternative' | 'degraded' | 'verifying' | 'verified_fail',
          message: (d.message as string) || '',
        },
      }));
    });

    // ---- TODO events ----
    client.on('todo_update', (_m: WSMessage) => {
      const text = String(_m.data || '');
      updateAppState(prev => ({ ...prev, statusText: 'TODO: ' + text.slice(0, 100) }));
    });
    client.on('todo_item_done', (_m: WSMessage) => {
      const text = String(_m.data || '');
      updateAppState(prev => ({ ...prev, statusText: 'Done: ' + text.slice(0, 100) }));
    });
    client.on('todo_progress', (_m: WSMessage) => {
      const text = String(_m.data || '');
      updateAppState(prev => ({ ...prev, statusText: text.slice(0, 100) }));
    });

    // ---- workspace ----
    client.on('workspace_changed', (_m: WSMessage) => {
      const text = String(_m.data || 'Workspace changed');
      updateAppState(prev => ({ ...prev, statusText: text.slice(0, 100) }));
    });

    // ---- generation state ----
    client.on('generation_paused', () => {
      updateAppState(prev => ({ ...prev, statusText: 'Generation paused -- press ESC to resume or ESC ESC to stop' }));
    });
    client.on('generation_resumed', () => {
      updateAppState(prev => ({ ...prev, statusText: '' }));
    });

    // ---- notification ----
    client.on('notification', (_m: WSMessage) => {
      const text = String(_m.data || '');
      updateAppState(prev => ({
        ...prev,
        statusText: text,
        connected: text === 'Reconnected.' ? true : prev.connected,
      }));
    });

  }, [setShowApproval]);
}

