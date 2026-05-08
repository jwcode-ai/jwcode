import { useEffect, useCallback } from 'react';
import wsService from '../services/websocket';
import { useChatStore } from '../stores/chatStore';
import { useSessionStore } from '../stores/sessionStore';
import { usePlanStore } from '../stores/planStore';
import { useCommandStore } from '../stores/commandStore';
import { LogEntry, PlanTask } from '../types';

interface UseWebSocketOptions {
  activeTab: string;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
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

  // Handle WebSocket messages
  const handleWSMessage = useCallback((msg: { type: string; data?: string; sessionId?: string }) => {
    const rawType = msg.type;
    const rawData = msg.data;

    // 确定消息所属的 sessionId
    // 优先使用后端返回的 sessionId，如果后端没有返回则使用当前活跃 sessionId
    const activeSessionId = useSessionStore.getState().activeSessionId;
    const sessionId = msg.sessionId || activeSessionId;
    if (!sessionId) {
      console.warn('[WS] 无法确定消息的 sessionId，消息被丢弃:', rawType);
      return;
    }

    // 如果消息的 sessionId 与当前活跃 sessionId 不同，记录日志但不丢弃
    // （例如后台任务完成通知可能属于其他会话）
    if (msg.sessionId && msg.sessionId !== activeSessionId) {
      console.log(`[WS] 消息 sessionId(${msg.sessionId}) 与当前活跃 sessionId(${activeSessionId}) 不同，但仍会处理`);
    }

    // 处理 plan_* 类型的消息
    if (rawType.startsWith('plan_')) {
      const planStore = usePlanStore.getState();
      try {
        switch (rawType) {
          case 'plan_start':
            console.log('[WS] plan_start:', rawData);
            planStore.startPlanning(sessionId, rawData || '');
            break;

          case 'plan_thinking':
            // 更新规划状态描述
            console.log('[WS] plan_thinking:', rawData);
            break;

          case 'plan_tasks': {
            console.log('[WS] plan_tasks:', rawData);
            const data = JSON.parse(rawData || '{}');
            const tasks: PlanTask[] = data.tasks || [];
            const currentPlan = planStore.getPlan(sessionId);
            if (currentPlan) {
              planStore.setPlan(sessionId, {
                ...currentPlan,
                tasks,
                phase: 'executing',
              });
            }
            break;
          }

          case 'plan_task_start': {
            const data = JSON.parse(rawData || '{}');
            console.log('[WS] plan_task_start:', data.id);
            planStore.updateTask(sessionId, data.id, {
              status: 'running',
              startedAt: Date.now(),
              logs: data.logs || [],
            });
            break;
          }

          case 'plan_task_update': {
            const data = JSON.parse(rawData || '{}');
            planStore.updateTask(sessionId, data.id, {
              progress: data.progress,
              logs: data.logs,
            });
            break;
          }

          case 'plan_task_result': {
            const data = JSON.parse(rawData || '{}');
            console.log('[WS] plan_task_result:', data.id, data.status);
            planStore.updateTask(sessionId, data.id, {
              status: data.status || 'completed',
              result: data.result,
              error: data.error,
              completedAt: Date.now(),
              logs: data.logs,
            });
            break;
          }

          case 'plan_complete':
            console.log('[WS] plan_complete');
            planStore.setPhase(sessionId, 'result');
            // 检查消息队列
            const nextMsg = planStore.dequeueMessage();
            if (nextMsg) {
              // 有排队消息，自动发送
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
            break;

          case 'plan_error':
            console.error('[Plan] Error:', rawData);
            planStore.setPhase(sessionId, 'result');
            break;
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
        console.log(`[WS] 收到 content 消息, data长度=${(msg.data || '').length}, sessionId=${sessionId}, data前50字符=${(msg.data || '').substring(0, 50)}`);
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

          if (existing) {
            chatStore.getState().updateToolCall(sessionId, toolId, { args: toolData.args || '' });
          } else {
            chatStore.getState().addToolCall(sessionId, {
              id: toolId,
              index: toolIndex,
              name: toolData.name || 'Unknown',
              args: toolData.args || '',
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
        break;

      case 'error':
        chatStore.getState().endGeneration(sessionId);
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
        break;

      case 'auth_failed':
        console.error('[WS] Authentication failed:', msg.data);
        break;

      case 'log':
        try {
          const logData = typeof msg.data === 'string' ? JSON.parse(msg.data) : msg.data;
          const newLog: LogEntry = {
            id: `log-${Date.now()}`,
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
          console.log(`[WS] 已加载 ${commands.length} 个后端命令`);
        } catch (e) {
          console.error('Failed to parse commands list:', e);
        }
        break;

      case 'ping':
        wsService.send({ type: 'pong', data: Date.now().toString() });
        break;
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
