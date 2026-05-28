import { useEffect, useCallback } from 'react';
import wsService from '../services/websocket';
import { useChatStore } from '../stores/chatStore';
import { useSessionStore } from '../stores/sessionStore';
import { usePlanStore, diffTasks } from '../stores/planStore';
import { useTokenStore } from '../stores/tokenStore';
import { useCommandStore } from '../stores/commandStore';
import { useSettingsStore } from '../stores/settingsStore';
import { useHookApprovalStore } from '../stores/useHookApprovalStore';
import { LogEntry, SessionTask } from '../types';

const DEBUG = false;

interface UseWebSocketOptions {
  activeTab: string;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

/**
 * 生成结束时重新计算当前会话的 token 用量
 */
function recalcTokenUsage(sessionId: string) {
  const messages = useChatStore.getState().messagesBySession[sessionId];
  if (messages) {
    useTokenStore.getState().recalculateFromMessages(messages);
  }
}

export function useWebSocket({ activeTab, setLogs, setUnreadLogs }: UseWebSocketOptions) {
  const chatStore = useChatStore;

  // 辅助函数：从 store 获取最新的 step（带 sessionId）
  const getLatestStep = useCallback((sessionId: string) => {
    const state = chatStore.getState();
    const msgs = state.messagesBySession[sessionId] || [];
    const lastMessage = msgs[msgs.length - 1];
    return lastMessage?.steps?.[lastMessage.steps.length - 1];
  }, [chatStore]);

  // 辅助函数：确保有可写的 step 存在（带 sessionId）
  const ensureStep = useCallback((sessionId: string, type: string, stepData: any) => {
    const lastStep = getLatestStep(sessionId);
    const isCompleteEvent = type === 'step_complete';
    const stepFinished = lastStep && (lastStep.status === 'success' || lastStep.status === 'error');

    if (!lastStep || (stepFinished && !isCompleteEvent)) {
      const stepTitle = stepData?.step || type.replace('step_', '').replace('_', ' ');
      chatStore.getState().addStep(sessionId, {
        id: `step-${Date.now()}`,
        title: stepTitle,
        description: stepData?.description || '执行中...',
        status: 'running',
        timestamp: Date.now(),
      });
      return getLatestStep(sessionId);
    }
    return lastStep;
  }, [getLatestStep, chatStore]);

  // Handle WebSocket messages（已移除 startTransition，确保 content 流式块不被 React 降级延迟渲染）
  const handleWSMessage = useCallback((msg: { type: string; data?: string; sessionId?: string }) => {
    _handleWSMessage(msg);
  }, [activeTab, chatStore, ensureStep, setLogs, setUnreadLogs]);

  // 实际的 WebSocket 消息处理逻辑
  const _handleWSMessage = useCallback((msg: { type: string; data?: string; sessionId?: string }) => {
    const rawType = msg.type;
    const rawData = msg.data;

    // 确定消息所属的 sessionId
    // 优先使用后端返回的 sessionId，如果后端没有返回则使用当前活跃 sessionId
    const activeSessionId = useSessionStore.getState().activeSessionId;
    const sessionId = msg.sessionId || activeSessionId;
    if (!sessionId) {
      DEBUG && console.warn('[WS] 无法确定消息的 sessionId，消息被丢弃:', rawType);
      return;
    }

    // 如果消息的 sessionId 与当前活跃 sessionId 不同，记录日志但不丢弃
    // （例如后台任务完成通知可能属于其他会话）
    if (msg.sessionId && msg.sessionId !== activeSessionId) {
      DEBUG && console.log(`[WS] 消息 sessionId(${msg.sessionId}) 与当前活跃 sessionId(${activeSessionId}) 不同，但仍会处理`);
    }

    // 处理 hook_ask 消息（Hook ASK 审批）— 嵌入到对话中
    if (rawType === 'hook_ask') {
      try {
        const data = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
        const approvalStore = useHookApprovalStore.getState();
        const { approvalId, toolName, askPayload } = data;

        // 检查会话允许列表
        if (approvalStore.isSessionAllowed(toolName)) {
          wsService.send({
            type: 'hook_allow' as any,
            data: JSON.stringify({ approvalId }),
          });
          return;
        }

        // 将权限申请作为一条消息插入到对话中
        const chatState = chatStore.getState();
        chatState.addMessage(sessionId, {
          id: `hook-approval-${approvalId || Date.now()}`,
          type: 'system',
          content: '',
          timestamp: Date.now(),
          hookApproval: {
            approvalId: approvalId || '',
            toolName: toolName || 'unknown',
            askPayload: askPayload || '',
            status: 'pending',
            timestamp: Date.now(),
          },
        });

        // 同时保留后台审批列表（供自动模式等使用）
        approvalStore.addApproval({
          approvalId: approvalId || '',
          toolName: toolName || 'unknown',
          askPayload: askPayload || '',
          timestamp: Date.now(),
        });
      } catch (e) {
        DEBUG && console.warn('[WS] hook_ask parse error:', e);
      }
      return;
    }

    // 处理 task_update 消息（实时任务推送 — 来自 TaskStore 监听器）
    if (rawType === 'task_update') {
      try {
        const data = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
        const sessionStore = useSessionStore.getState();
        const sid = msg.sessionId || activeSessionId;
        if (!sid) return;

        const { action, taskId, data: taskData } = data;

        if (action === 'created' && taskData) {
          // 将后端任务转换为 SessionTask 并添加到当前 session
          const sessionTask: SessionTask = {
            id: taskId || `task-${Date.now()}`,
            title: taskData.title || '',
            completed: taskData.status === 'COMPLETED',
            createdAt: Date.parse(taskData.createdAt) || Date.now(),
            backendId: taskId,
            backendStatus: taskData.status,
            description: taskData.description,
          };
          const existing = sessionStore.tasksBySession[sid] || [];
          sessionStore.setSessionTasks(sid, [...existing, sessionTask]);
          DEBUG && console.log('[WS] Task created via push:', taskData.title);
        } else if ((action === 'updated' || action === 'status_changed') && taskData) {
          // 更新任务状态
          sessionStore.updateTaskPlanStatus(sid, taskId, {
            backendStatus: taskData.status,
            progress: taskData.progress,
          });
          DEBUG && console.log('[WS] Task updated via push:', taskId, taskData.status);
        } else if (action === 'deleted') {
          // 删除任务
          const existing = sessionStore.tasksBySession[sid] || [];
          sessionStore.setSessionTasks(sid, existing.filter(t => t.id !== taskId && t.backendId !== taskId));
          DEBUG && console.log('[WS] Task deleted via push:', taskId);
        }
      } catch (e) {
        DEBUG && console.warn('[WS] task_update parse error:', e);
      }
      return;
    }

    // 处理 plan_* 类型的消息
    if (rawType.startsWith('plan_')) {
      const planStore = usePlanStore.getState();
      try {
        switch (rawType) {
          case 'plan_start':
            DEBUG && console.log('[WS] plan_start:', rawData);
            planStore.startPlanning(sessionId, rawData || '');
            // 创建 assistant message，使后续 content 消息能正确追加
            chatStore.getState().startGeneration(sessionId);
            chatStore.getState().addMessage(sessionId, {
              id: `msg-${Date.now()}`,
              type: 'assistant',
              content: '',
              timestamp: Date.now(),
            });
            break;

          case 'plan_thinking':
            // 更新规划状态描述 - 将思考内容同步到 plan store 供前端展示
            DEBUG && console.log('[WS] plan_thinking:', rawData);
            planStore.setThinkingStatus(sessionId, rawData || '正在分析需求...');
            break;

          case 'plan_tasks': {
            DEBUG && console.log('[WS] plan_tasks:', rawData);
            const data = JSON.parse(rawData || '{}');

            // 检查是否包含结构化任务数据
            let structuredTasksData: any = null;

            if (data.structuredTasks && Array.isArray(data.structuredTasks)) {
              structuredTasksData = data.structuredTasks;
            } else if (data.tasks?.structuredTasks && Array.isArray(data.tasks.structuredTasks)) {
              structuredTasksData = data.tasks.structuredTasks;
            } else if (data.tasks && Array.isArray(data.tasks)) {
              structuredTasksData = data.tasks;
            }

            if (structuredTasksData) {
              // 统一数据源：将结构化任务写入 sessionStore.tasksBySession
              const sessionStore = useSessionStore.getState();
              const flattenTasks = (list: any[]): SessionTask[] => {
                const result: SessionTask[] = [];
                for (const t of list) {
                  const task: SessionTask = {
                    id: t.id || `task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
                    title: t.title || t.name || '',
                    completed: false,
                    createdAt: Date.now(),
                    planStatus: 'pending',
                    agentType: t.agentType || 'default',
                    dependencies: t.dependencies || [],
                    description: t.description,
                    stepNumber: t.stepNumber,
                    action: t.action,
                    stepPrompt: t.stepPrompt,
                    executionMode: t.executionMode,
                    phase: t.phase,
                    parallelGroup: t.parallelGroup,
                    context: t.context,
                  };
                  if (t.children?.length) {
                    task.children = flattenTasks(t.children);
                  }
                  result.push(task);
                }
                return result;
              };
              const tasks = flattenTasks(structuredTasksData);
              sessionStore.setSessionTasks(sessionId, tasks);
              DEBUG && console.log('[WS] Structured tasks written to sessionStore:', tasks.length);

              // 同步写入 planStore.structuredTasksBySession（供 PlanPanel 结构化视图使用）
              // 保持 StructuredTask 完整结构（不 flatten），保留树形
              const buildStructuredTasks = (list: any[]): any[] => {
                return list.map((t: any) => ({
                  id: t.id || `task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
                  title: t.title || t.name || '',
                  description: t.description || '',
                  status: t.status || 'pending',
                  agentType: t.agentType || 'default',
                  dependencies: t.dependencies || [],
                  executionMode: t.executionMode || 'SEQUENTIAL',
                  phase: t.phase || 'GENERAL',
                  parallelGroup: t.parallelGroup,
                  stepNumber: t.stepNumber,
                  action: t.action,
                  stepPrompt: t.stepPrompt,
                  context: t.context,
                  progress: t.progress || 0,
                  children: t.children?.length ? buildStructuredTasks(t.children) : [],
                }));
              };
              const structuredTasks = buildStructuredTasks(structuredTasksData);
              
              // 计算任务变更 diff（对比旧任务树）
              const oldTasks = planStore.getStructuredTasks(sessionId);
              if (oldTasks.length > 0) {
                const diff = diffTasks(oldTasks, structuredTasks);
                planStore.setTaskDiff(sessionId, diff);
                if (diff.total > 0) {
                  DEBUG && console.log('[WS] Task diff:', diff);
                }
              }
              
              planStore.setStructuredTasks(sessionId, structuredTasks);
              DEBUG && console.log('[WS] Structured tasks synced to planStore:', structuredTasks.length);

              // 自动切换到 Plan Tab
              window.dispatchEvent(new CustomEvent('switch-tab', { detail: 'plan' }));
            }

            // planStore 只保留 phase 用于控制 SessionTaskBoard 显隐
            const analysis = data.analysis || '';
            const currentPlan = planStore.getPlan(sessionId);
            if (currentPlan) {
              planStore.setPlan(sessionId, {
                ...currentPlan,
                tasks: [],
                phase: 'executing',
              });
            } else {
              planStore.startPlanning(sessionId, analysis || '任务计划');
              setTimeout(() => {
                const newPlan = planStore.getPlan(sessionId);
                if (newPlan) {
                  planStore.setPlan(sessionId, {
                    ...newPlan,
                    tasks: [],
                    phase: 'executing',
                  });
                }
              }, 0);
            }
            break;
          }

          case 'plan_refine': {
            // 用户在前端 PlanPanel 点击"完善计划"后，后端重新规划
            // 前端只需清除 planRefining 状态（后端会通过 plan_tasks + plan_complete 更新）
            DEBUG && console.log('[WS] plan_refine received:', rawData);
            planStore.setPlanRefining(false);
            break;
          }

          case 'plan_task_start': {
            const data = JSON.parse(rawData || '{}');
            DEBUG && console.log('[WS] plan_task_start:', data.id);
            // 统一数据源：写入 sessionStore
            useSessionStore.getState().updateTaskPlanStatus(sessionId, data.id, {
              planStatus: 'running',
              startedAt: Date.now(),
              logs: data.logs || [],
            });
            break;
          }

          case 'plan_task_update': {
            const data = JSON.parse(rawData || '{}');
            useSessionStore.getState().updateTaskPlanStatus(sessionId, data.id, {
              progress: data.progress,
              logs: data.logs,
            });
            break;
          }

          case 'plan_task_result': {
            const data = JSON.parse(rawData || '{}');
            DEBUG && console.log('[WS] plan_task_result:', data.id, data.status);
            useSessionStore.getState().updateTaskPlanStatus(sessionId, data.id, {
              planStatus: data.status || 'completed',
              result: data.result,
              error: data.error,
              completedAt: Date.now(),
              logs: data.logs,
            });
            break;
          }

          case 'plan_complete': {
            DEBUG && console.log('[WS] plan_complete:', rawData);
            const planData = JSON.parse(rawData || '{}');
            
            // 无论 status 字段是否存在，都结束生成状态（兼容后端纯字符串 data 的情况）
            chatStore.getState().endGeneration(sessionId);
            recalcTokenUsage(sessionId);
            // 清除完善中状态（无论是完善后完成还是首次完成）
            planStore.setPlanRefining(false);
            
            if (planData.status === 'waiting_confirm') {
              // AI 分析完成，等待用户确认
              planStore.setPhase(sessionId, 'planning');
              planStore.setShowConfirmButton(true);
              DEBUG && console.log('[WS] Plan 分析完成，等待用户确认');
            } else {
              // 执行完成（兜底：status 为 'completed' 或 undefined 均走此分支）
              planStore.setPhase(sessionId, 'result');
              planStore.setShowConfirmButton(false);
              // 检查消息队列
              const nextMsg = planStore.dequeueMessage();
              if (nextMsg) {
                const sid = useSessionStore.getState().activeSessionId;
                if (sid) {
                  wsService.setSessionId(sid);
                  wsService.send({
                    type: 'chat',
                    sessionId: sid,
                    message: nextMsg.content,
                  });
                }
              }
            }
            break;
          }

          case 'plan_error':
            console.error('[Plan] Error:', rawData);
            // 结束生成状态，清除"AI 正在思考..."提示
            chatStore.getState().endGeneration(sessionId);
            recalcTokenUsage(sessionId);
            // 将错误信息保存到 plan 中，phase 设为 'error' 以便前端显示错误状态
            planStore.setPhase(sessionId, 'error');
            break;

          case 'plan_mode_change': {
            // Phase 3: 后端 PlanModeManager 模式切换同步到前端
            DEBUG && console.log('[WS] plan_mode_change:', rawData);
            try {
              const modeData = JSON.parse(rawData || '{}');
              const newMode = modeData.newMode;
              if (newMode === 'plan') {
                planStore.setMode('plan');
              } else if (newMode === 'act') {
                planStore.setMode('act');
              } else if (newMode === 'normal') {
                planStore.setMode('normal');
              }
              DEBUG && console.log(`[Plan] 模式已同步: ${modeData.previousMode} → ${newMode}`);
            } catch (e) {
              console.error('[Plan] 模式同步解析失败:', e);
            }
            break;
          }

          case 'step_prompt': {
            const data = JSON.parse(rawData || '{}');
            DEBUG && console.log('[WS] step_prompt: step', data.stepNumber, data.action);
            planStore.setCurrentStepPrompt({
              sessionId,
              taskId: data.taskId || '',
              stepIndex: data.stepIndex || 0,
              stepNumber: data.stepNumber || 1,
              description: data.description || '',
              action: data.action || '',
              stepPrompt: data.stepPrompt || '',
              agentType: data.agentType || '',
            });
            break;
          }
        }
      } catch (e) {
        console.error(`Failed to handle ${rawType}:`, e);
      }
      return;
    }


    // 处理 step_* 类型的消息
    if (rawType.startsWith('step_')) {
      try {
        const stepData = JSON.parse(rawData || '{}');
        const lastStep = ensureStep(sessionId, rawType, stepData);

        if (lastStep) {
          switch (rawType) {
            case 'step_start':
              chatStore.getState().updateStep(sessionId, lastStep.id, {
                title: stepData.step || lastStep.title,
                description: stepData.description || lastStep.description,
                status: stepData.status === 'start' ? 'running' : (stepData.status || 'running')
              });
              break;
            case 'step_thinking':
              chatStore.getState().updateStep(sessionId, lastStep.id, { thought: stepData.thought });
              break;
            case 'step_action':
              chatStore.getState().updateStep(sessionId, lastStep.id, { action: stepData.action });
              break;
            case 'step_complete':
              chatStore.getState().updateStep(sessionId, lastStep.id, {
                status: 'success',
                result: stepData.result
              });
              break;
          }
        }
      } catch (e) {
        console.error(`Failed to parse ${rawType}:`, e);
      }
      return;
    }

    // 标准消息类型处理
    switch (msg.type) {
      case 'start':
        chatStore.getState().startGeneration(sessionId);
        chatStore.getState().addMessage(sessionId, {
          id: `msg-${Date.now()}`,
          type: 'assistant',
          content: '',
          timestamp: Date.now(),
        });
        break;

      case 'content':
        DEBUG && console.log(`[WS] 收到 content 消息, data长度=${(msg.data || '').length}, sessionId=${sessionId}, data前50字符=${(msg.data || '').substring(0, 50)}`);
        chatStore.getState().appendToLastMessage(sessionId, msg.data || '');
        break;

      case 'thinking':
        chatStore.getState().appendToLastMessageThinking(sessionId, msg.data || '');
        break;

      case 'tool_call':
        try {
          const toolData = JSON.parse(msg.data || '{}');
          const toolId = toolData.id || `tool-${Date.now()}`;
          const toolIndex = typeof toolData.index === 'number' ? toolData.index : undefined;

          // 确保有 step 容器，这样 toolCall 才能挂到 step 下形成树形结构
          ensureStep(sessionId, 'tool_call', { step: toolData.name || '工具调用', description: `执行 ${toolData.name || '工具'}...` });
          const state = chatStore.getState();
          const msgs = state.messagesBySession[sessionId] || [];
          const lastMsg = msgs[msgs.length - 1];
          const allToolCalls = [
            ...(lastMsg?.steps?.flatMap(s => s.toolCalls || []) || []),
            ...(lastMsg?.toolCalls || [])
          ];
          const existing = allToolCalls.find((tc: any) =>
            (toolIndex !== undefined && tc.index === toolIndex) || tc.id === toolId
          );

          // 兼容 args 和 arguments 两种字段名（Act 模式用 args，Plan 模式旧版本可能用 arguments）
          const toolArgs = toolData.args || toolData.arguments || '';
          if (existing) {
            chatStore.getState().updateToolCall(sessionId, toolId, { args: toolArgs });
          } else {
            chatStore.getState().addToolCall(sessionId, {
              id: toolId,
              index: toolIndex,
              name: toolData.name || 'Unknown',
              args: toolArgs,
              status: 'running',
              timestamp: Date.now(),
            });
          }
        } catch (e) {
          console.error('Failed to parse tool call:', e);
        }
        break;

      case 'tool_result':
        try {
          const toolData = JSON.parse(msg.data || '{}');
          const lastStep = ensureStep(sessionId, 'tool_result', { step: '工具结果', description: toolData.toolName || '工具执行' });
          if (lastStep) {
            chatStore.getState().updateStep(sessionId, lastStep.id, {
              status: 'success',
              result: toolData.result || '执行完成'
            });
          }
          const tcState = chatStore.getState();
          const tcMsgs = tcState.messagesBySession[sessionId] || [];
          const tcLastMsg = tcMsgs[tcMsgs.length - 1];
          const allToolCalls = [
            ...(tcLastMsg?.steps?.flatMap(s => s.toolCalls || []) || []),
            ...(tcLastMsg?.toolCalls || [])
          ];
          const matchedToolCall = allToolCalls.find((tc: any) => tc.name === toolData.toolName);
          if (matchedToolCall) {
            chatStore.getState().updateToolCall(sessionId, matchedToolCall.id, {
              status: 'completed',
              result: toolData.result
            });
          }
        } catch (e) {
          console.error('Failed to parse tool result:', e);
        }
        break;

      case 'complete':
        chatStore.getState().endGeneration(sessionId);
        recalcTokenUsage(sessionId);
        break;

      case 'generation_paused':
        chatStore.getState().pauseGeneration(sessionId);
        break;

      case 'generation_resumed':
        chatStore.getState().resumeGeneration(sessionId);
        break;

      case 'token_update':
        try {
          const tokenData = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
          if (tokenData.totalTokens > 0) {
            useTokenStore.getState().updateUsage({
              promptTokens: tokenData.promptTokens || 0,
              completionTokens: tokenData.completionTokens || 0,
              totalTokens: tokenData.totalTokens || 0,
            });
          }
          if (tokenData.model) {
            useTokenStore.getState().setModel(tokenData.model);
          }
        } catch (e) {
          // ignore parse errors
        }
        break;

      case 'error':
        chatStore.getState().endGeneration(sessionId);
        recalcTokenUsage(sessionId);
        chatStore.getState().addMessage(sessionId, {
          id: `msg-${Date.now()}`,
          type: 'assistant',
          content: `❌ 错误: ${msg.data}`,
          timestamp: Date.now(),
        });
        break;

      case 'auth_required':
        let savedToken = localStorage.getItem('auth_token');
        if (!savedToken) {
          savedToken = 'default-token';
          localStorage.setItem('auth_token', savedToken);
        }
        wsService.setToken(savedToken);
        wsService.setAuthenticated(false);
        wsService.send({ type: 'auth', token: savedToken });
        break;

      case 'auth_success':
        wsService.setAuthenticated(true);
        // 认证成功后订阅日志和获取命令列表
        wsService.send({ type: 'subscribe_logs' });
        wsService.send({ type: 'get_commands' });
        // 向后端同步前端当前的工作目录，确保前后端一致
        const currentDir = useSettingsStore.getState().workspaceDir;
        if (currentDir) {
          wsService.send({ type: 'workspace', message: currentDir });
        }
        break;

      case 'auth_failed':
        console.error('[WS] Authentication failed:', msg.data);
        break;

      case 'log':
        try {
          const logData = typeof msg.data === 'string' ? JSON.parse(msg.data) : msg.data;
          const newLog: LogEntry = {
            id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            level: logData.level || 'info',
            source: logData.source || 'System',
            message: logData.message || '',
            timestamp: logData.timestamp || Date.now(),
          };
          setLogs(prev => [...prev, newLog].slice(-500));
          if (activeTab !== 'logs') {
            setUnreadLogs(prev => prev + 1);
          }
        } catch (e) {
          console.error('Failed to parse log:', e);
        }
        break;

      case 'commands_list':
        try {
          const commands = JSON.parse(msg.data || '[]');
          useCommandStore.getState().setBackendCommands(commands);
          DEBUG && console.log(`[WS] 已加载 ${commands.length} 个后端命令`);
        } catch (e) {
          console.error('Failed to parse commands list:', e);
        }
        break;

      case 'ping':
        wsService.send({ type: 'pong', data: Date.now().toString() });
        break;

      case 'workspace_changed':
        try {
          const wsData = JSON.parse(msg.data || '{}');
          DEBUG && console.log('[WS] 工作目录已切换:', wsData.oldDir, '->', wsData.newDir);
          // 同步更新前端 store，确保前后端工作目录一致
          if (wsData.newDir) {
            useSettingsStore.getState().setWorkspaceDir(wsData.newDir);
          }
        } catch (e) {
          DEBUG && console.log('[WS] 工作目录已切换:', msg.data);
        }
        break;

      // === 任务列表实时同步（todo_* 消息） ===
      case 'todo_update': {
        // 全量任务列表更新
        // 支持两种数据格式：
        //   格式1（后端 TodoWriteBroadcaster）: 原始 JSON 数组 [{content, status, index}, ...]
        //   格式2（前端兼容）: {tasks: [...], items: [...]}
        try {
          const parsed = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
          let tasks: any[] = [];

          if (Array.isArray(parsed)) {
            // 格式1：原始 JSON 数组（来自 TodoWriteBroadcaster）
            tasks = parsed;
          } else if (parsed.tasks && Array.isArray(parsed.tasks)) {
            tasks = parsed.tasks;
          } else if (parsed.items && Array.isArray(parsed.items)) {
            tasks = parsed.items;
          }

          if (tasks.length > 0 && sessionId) {
            const sessionStore = useSessionStore.getState();
            tasks.forEach((task: any) => {
              // TodoWriteBroadcaster 使用 content 字段，兼容 title/name
              const title = task.content || task.title || task.name || '';
              if (title.trim()) {
                const existing = sessionStore.getSessionTasks(sessionId);
                const dup = existing.find(t => t.title === title.trim());
                if (!dup) {
                  sessionStore.addSessionTask(sessionId, title.trim());
                  // 如果后端标记了已完成，同步状态
                  const isCompleted = task.completed
                    || task.status === 'completed'
                    || task.status === 'COMPLETED'
                    || task.status === 'done'
                    || task.activeForm === 'completed';
                  if (isCompleted) {
                    const updated = useSessionStore.getState().getSessionTasks(sessionId);
                    const added = updated.find(t => t.title === title.trim());
                    if (added) {
                      useSessionStore.getState().toggleSessionTask(sessionId, added.id);
                    }
                  }
                }
              }
            });
          }
        } catch (e) {
          DEBUG && console.warn('[WS] todo_update parse error:', e);
        }
        break;
      }

      case 'todo_item_done': {
        // 单个任务完成通知
        try {
          const doneData = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
          const taskTitle = doneData.title || doneData.name || '';
          if (taskTitle && sessionId) {
            const tasks = useSessionStore.getState().getSessionTasks(sessionId);
            const match = tasks.find(t => t.title === taskTitle && !t.completed);
            if (match) {
              useSessionStore.getState().toggleSessionTask(sessionId, match.id);
            }
          }
        } catch (e) {
          DEBUG && console.warn('[WS] todo_item_done parse error:', e);
        }
        break;
      }

      case 'todo_progress': {
        // 进度更新（暂不处理，保持兼容）
        break;
      }
    }
  }, [activeTab, chatStore, ensureStep, setLogs, setUnreadLogs]);

  // WebSocket connection
  useEffect(() => {
    wsService.connect();

    const unsubOpen = wsService.onOpen(() => {
      // 连接成功后，等待认证完成后再订阅
      // 认证由 handleWSMessage 中的 auth_success 触发
    });
    const unsubClose = wsService.onClose(() => {});
    const unsubMessage = wsService.onMessage(handleWSMessage);

    return () => {
      unsubOpen();
      unsubClose();
      unsubMessage();
    };
  }, [handleWSMessage]);
}
