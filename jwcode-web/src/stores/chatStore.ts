import { create } from 'zustand';
import { persist, type PersistStorage } from 'zustand/middleware';
import { HookApprovalInfo, Message, Step, ToolCall } from '../types';

const MAX_MESSAGES_PER_SESSION = 200;
const STORAGE_KEY = 'jwcode-chat-store';

// 防抖版 localStorage 适配器：流式输出期间高频 set() 不再立即阻塞主线程写盘
// getItem 保持同步，确保启动时正常恢复历史记录
const debouncedStorage = (() => {
  let timer: ReturnType<typeof setTimeout> | null = null;
  const DEBOUNCE_MS = 500;
  return {
    getItem: (name: string) => {
      const val = localStorage.getItem(name);
      try { return val ? JSON.parse(val) : null; } catch { return val; }
    },
    setItem: (name: string, value: string) => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        localStorage.setItem(name, value);
        timer = null;
      }, DEBOUNCE_MS);
    },
    removeItem: (name: string) => localStorage.removeItem(name),
  };
})();

interface ChatState {
  // 多会话消息存储：sessionId → Message[]
  messagesBySession: Record<string, Message[]>;
  // 正在生成的会话集合
  generatingSessions: string[];
  // 已暂停的会话集合
  pausedSessions: string[];
  // 每个会话独立的输入内容
  sessionInputs: Record<string, string>;

  // Actions — 全部带 sessionId
  getMessages: (sessionId: string) => Message[];
  addMessage: (sessionId: string, message: Message) => void;
  updateLastMessage: (sessionId: string, content: string) => void;
  appendToLastMessage: (sessionId: string, content: string) => void;
  setThinking: (sessionId: string, thinking: string) => void;
  appendToLastMessageThinking: (sessionId: string, chunk: string) => void;
  addStep: (sessionId: string, step: Step) => void;
  updateStep: (sessionId: string, stepId: string, updates: Partial<Step> & { id?: string }) => void;
  addToolCall: (sessionId: string, toolCall: ToolCall) => void;
  updateToolCall: (sessionId: string, toolCallId: string, updates: Partial<ToolCall> & { id?: string }) => void;
  appendToLastToolCallArgs: (sessionId: string, toolCallId: string, argsPartial: string, index?: number) => void;
  startGeneration: (sessionId: string) => void;
  endGeneration: (sessionId: string, error?: string) => void;
  pauseGeneration: (sessionId: string) => void;
  resumeGeneration: (sessionId: string) => void;
  clearMessages: (sessionId: string) => void;
  removeSession: (sessionId: string) => void;
  setSessionInput: (sessionId: string, input: string) => void;
  getSessionInput: (sessionId: string) => string;
  attachHookApproval: (sessionId: string, approval: HookApprovalInfo) => void;
  /** Tombstone: 标记指定消息为已删除（模型切换/上下文压缩导致） */
  applyTombstone: (sessionId: string, messageIds: string[]) => void;
  isGenerating: (sessionId: string) => boolean;
  isPaused: (sessionId: string) => boolean;
}

export const useChatStore = create<ChatState>()(
  persist(
    (set, get) => ({
      messagesBySession: {},
      generatingSessions: [],
      pausedSessions: [],
      sessionInputs: {},

      getMessages: (sessionId) => {
        return get().messagesBySession[sessionId] || [];
      },

      addMessage: (sessionId, message) =>
        set((state) => {
          const messages = state.messagesBySession[sessionId] || [];
          const updated = [...messages, message].slice(-MAX_MESSAGES_PER_SESSION);
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: updated,
            },
          };
        }),

      updateLastMessage: (sessionId, content) =>
        set((state) => {
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) =>
            index === arr.length - 1 && msg.type === 'assistant'
              ? { ...msg, content }
              : msg
          );
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      appendToLastMessage: (sessionId, content) =>
        set((state) => {
          const messages = state.messagesBySession[sessionId];
          if (!messages || messages.length === 0) return state;
          const lastIdx = messages.length - 1;
          const lastMsg = messages[lastIdx];
          if (!lastMsg || lastMsg.type !== 'assistant') return state;
          const updated = messages.slice();
          updated[lastIdx] = { ...lastMsg, content: lastMsg.content + content } as Message;
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: updated,
            },
          };
        }),

      setThinking: (sessionId, thinking) =>
        set((state) => {
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr): Message => {
            if (index !== arr.length - 1 || msg.type !== 'assistant') return msg;
            const runningStepIndex = msg.steps?.findIndex((s) => s.status === 'running');
            if (runningStepIndex !== undefined && runningStepIndex !== -1 && msg.steps && msg.steps[runningStepIndex]) {
              const newSteps = msg.steps.map((s, i) =>
                i === runningStepIndex ? { ...s, thought: thinking } as Step : s
              );
              return { ...msg, steps: newSteps };
            }
            return { ...msg, thinking };
          });
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      appendToLastMessageThinking: (sessionId, chunk) =>
        set((state) => {
          const messages = state.messagesBySession[sessionId];
          if (!messages || messages.length === 0) return state;
          const lastIdx = messages.length - 1;
          const lastMsg = messages[lastIdx];
          if (!lastMsg || lastMsg.type !== 'assistant') return state;

          let updatedLast: Message;
          const runningStepIndex = lastMsg.steps?.findIndex((s) => s.status === 'running');
          if (runningStepIndex !== undefined && runningStepIndex !== -1 && lastMsg.steps?.[runningStepIndex]) {
            const newSteps = lastMsg.steps.slice();
            const curStep = newSteps[runningStepIndex]!;
            newSteps[runningStepIndex] = { ...curStep, thought: (curStep.thought || '') + chunk } as Step;
            updatedLast = { ...lastMsg, steps: newSteps } as Message;
          } else if (lastMsg.steps && lastMsg.steps.length > 0) {
            const lastStepIdx = lastMsg.steps.length - 1;
            const newSteps = lastMsg.steps.slice();
            const curStep = newSteps[lastStepIdx]!;
            newSteps[lastStepIdx] = { ...curStep, thought: (curStep.thought || '') + chunk } as Step;
            updatedLast = { ...lastMsg, steps: newSteps } as Message;
          } else {
            updatedLast = { ...lastMsg, thinking: (lastMsg.thinking || '') + chunk } as Message;
          }

          const updated = messages.slice();
          updated[lastIdx] = updatedLast;
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: updated,
            },
          };
        }),

      addStep: (sessionId, step) =>
        set((state) => {
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr): Message => {
            if (index !== arr.length - 1 || msg.type !== 'assistant') return msg;

            const orphanedToolCalls = msg.toolCalls?.filter(tc =>
              !msg.steps?.some(s => s.toolCalls?.some(stc => stc.id === tc.id))
            );

            const newStep: Step = { ...step };
            if (orphanedToolCalls?.length) {
              newStep.toolCalls = [...(newStep.toolCalls || []), ...orphanedToolCalls];
            }

            let remainingThinking = msg.thinking || '';
            if (remainingThinking.trim().length > 0) {
              newStep.thought = (newStep.thought || '') + remainingThinking;
              remainingThinking = '';
            }

            return {
              ...msg,
              thinking: remainingThinking,
              toolCalls: orphanedToolCalls?.length
                ? msg.toolCalls?.filter(tc => !orphanedToolCalls.some(otc => otc.id === tc.id))
                : msg.toolCalls,
              steps: [...(msg.steps || []), newStep],
            } as Message;
          });
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      updateStep: (sessionId, stepId, updates) =>
        set((state) => {
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) => {
            if (index !== arr.length - 1 || !msg.steps) return msg;
            const stepIndex = msg.steps.findIndex((s) => s.id === stepId);
            if (stepIndex === -1) return msg;
            const newSteps = msg.steps.map((s, i) =>
              i === stepIndex ? { ...s, ...(updates as Partial<Step>) } as Step : s
            );
            return { ...msg, steps: newSteps };
          });
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      addToolCall: (sessionId, toolCall) =>
        set((state) => {
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) => {
            if (index !== arr.length - 1 || msg.type !== 'assistant') return msg;

            const runningStepIndex = msg.steps?.findIndex((s) => s.status === 'running');
            if (runningStepIndex !== undefined && runningStepIndex !== -1 && msg.steps && msg.steps[runningStepIndex]) {
              const newSteps = msg.steps.map((s, i) =>
                i === runningStepIndex
                  ? { ...s, toolCalls: [...(s.toolCalls || []), toolCall] }
                  : s
              );
              return { ...msg, steps: newSteps };
            }

            if (msg.steps && msg.steps.length > 0) {
              const lastStepIndex = msg.steps.length - 1;
              const newSteps = msg.steps.map((s, i) =>
                i === lastStepIndex
                  ? { ...s, toolCalls: [...(s.toolCalls || []), toolCall] }
                  : s
              );
              return { ...msg, steps: newSteps };
            }

            return { ...msg, toolCalls: [...(msg.toolCalls || []), toolCall] };
          });
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      updateToolCall: (sessionId, toolCallId, updates) =>
        set((state) => {
          const updateInList = (toolCalls: ToolCall[] | undefined): ToolCall[] | null => {
            if (!toolCalls) return null;
            const idx = toolCalls.findIndex((tc) => tc.id === toolCallId);
            if (idx === -1) return null;
            return toolCalls.map((tc, i) =>
              i === idx ? { ...tc, ...(updates as Partial<ToolCall>) } as ToolCall : tc
            );
          };

          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) => {
            if (index !== arr.length - 1 || msg.type !== 'assistant') return msg;

            // Try running step first
            const runningStepIndex = msg.steps?.findIndex((s) => s.status === 'running');
            if (runningStepIndex !== undefined && runningStepIndex !== -1 && msg.steps && msg.steps[runningStepIndex]) {
              const updatedToolCalls = updateInList(msg.steps[runningStepIndex]?.toolCalls);
              if (updatedToolCalls) {
                const newSteps = msg.steps.map((s, i) =>
                  i === runningStepIndex ? { ...s, toolCalls: updatedToolCalls } : s
                );
                return { ...msg, steps: newSteps };
              }
            }

            // Try all steps
            if (msg.steps) {
              let found = false;
              const newSteps = msg.steps.map((s) => {
                if (found) return s;
                const updated = updateInList(s.toolCalls);
                if (updated) {
                  found = true;
                  return { ...s, toolCalls: updated };
                }
                return s;
              });
              if (found) return { ...msg, steps: newSteps };
            }

            // Try message-level toolCalls
            const updatedMsgToolCalls = updateInList(msg.toolCalls);
            if (updatedMsgToolCalls) {
              return { ...msg, toolCalls: updatedMsgToolCalls };
            }

            return msg;
          });

          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      appendToLastToolCallArgs: (sessionId, toolCallId, argsPartial, index) =>
        set((state) => {
          const mergeArgs = (existing: ToolCall): ToolCall => {
            let mergedArgs: string | Record<string, unknown> = existing.args;
            if (typeof mergedArgs === 'string') {
              mergedArgs = mergedArgs + argsPartial;
            } else if (typeof mergedArgs === 'object' && mergedArgs !== null) {
              // åªåœ?argsPartial æ˜¯å®Œæ•?JSON å¯¹è±¡æ—¶æ‰?å?merge
              // å¦åˆ™ç»§ç»­åšå­—ç¬¦ä¸²ç§¯ç´¯ï¼Œé¿å……æµ?å¼ä¸嶇е®Œ JSON ç‰囨电殀 JSON.parse
              const trimmed = argsPartial.trim();
              if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
                try {
                  const parsed = JSON.parse(trimmed);
                  // è‹¥existing.args æ˜¯字符串åˆ§åˆ濈姸æ€?åˆ?åˆ?parse ä½滺Record
                  if (typeof mergedArgs === 'string') {
                    mergedArgs = { ...parsed };
                  } else {
                    mergedArgs = { ...(mergedArgs as Record<string, unknown>), ...parsed };
                  }
                } catch {
                  // ä¸嶇е®Œ JSON ï¼Œå?ªèƒ½å?š字符串拼接
                  const str = JSON.stringify(mergedArgs);
                  mergedArgs = str.slice(0, -1) + ',' + trimmed.slice(1);
                }
              } else {
                // æ?å°?éƒㄧ被åï¼Œå?ªèƒ½å?š字符串拼接
                if (typeof mergedArgs === 'string') {
                  mergedArgs = mergedArgs + trimmed;
                } else {
                  mergedArgs = JSON.stringify(mergedArgs) + trimmed;
                }
              }
            } else {
              mergedArgs = argsPartial;
            }
            return { ...existing, args: mergedArgs } as ToolCall;
          };

          const updateInList = (toolCalls: ToolCall[] | undefined): ToolCall[] | null => {
            if (!toolCalls) return null;
            const idx = toolCalls.findIndex((tc) =>
              (index !== undefined && tc.index === index) || tc.id === toolCallId
            );
            if (idx === -1) return null;
            return toolCalls.map((tc, i) => i === idx ? mergeArgs(tc) : tc);
          };

          const messages = (state.messagesBySession[sessionId] || []).map((msg, i, arr) => {
            if (i !== arr.length - 1 || msg.type !== 'assistant') return msg;

            // Try running step first
            const runningStepIndex = msg.steps?.findIndex((s) => s.status === 'running');
            if (runningStepIndex !== undefined && runningStepIndex !== -1 && msg.steps && msg.steps[runningStepIndex]) {
              const updated = updateInList(msg.steps[runningStepIndex]?.toolCalls);
              if (updated) {
                const newSteps = msg.steps.map((s, si) =>
                  si === runningStepIndex ? { ...s, toolCalls: updated } : s
                );
                return { ...msg, steps: newSteps };
              }
            }

            // Try all steps
            if (msg.steps) {
              let found = false;
              const newSteps = msg.steps.map((s) => {
                if (found) return s;
                const updated = updateInList(s.toolCalls);
                if (updated) {
                  found = true;
                  return { ...s, toolCalls: updated };
                }
                return s;
              });
              if (found) return { ...msg, steps: newSteps };
            }

            // Try message-level toolCalls
            const updatedMsgToolCalls = updateInList(msg.toolCalls);
            if (updatedMsgToolCalls) {
              return { ...msg, toolCalls: updatedMsgToolCalls };
            }

            return msg;
          });

          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      startGeneration: (sessionId) =>
        set((state) => ({
          generatingSessions: state.generatingSessions.includes(sessionId)
            ? state.generatingSessions
            : [...state.generatingSessions, sessionId],
        })),

      endGeneration: (sessionId, _error) =>
        set((state) => {
          const messages = state.messagesBySession[sessionId] || [];
          // 将所有仍在 running 的工具调用标记为 error（防止后端崩溃导致 UI 永久显示运行中）
          const now = Date.now();
          const cleanedMessages = messages.map(msg => {
            let changed = false;
            const cleanSteps = msg.steps?.map(step => {
              if (!step.toolCalls) return step;
              const cleanTCs = step.toolCalls.map(tc => {
                if (tc.status !== 'running') return tc;
                changed = true;
                return { ...tc, status: 'error' as const, result: '生成已结束（工具执行超时或连接中断）', duration: Math.floor((now - (tc.timestamp || now)) / 1000) };
              });
              return cleanTCs !== step.toolCalls ? { ...step, toolCalls: cleanTCs } : step;
            });
            const cleanMsgTCs = msg.toolCalls?.map(tc => {
              if (tc.status !== 'running') return tc;
              changed = true;
              return { ...tc, status: 'error' as const, result: '生成已结束（工具执行超时或连接中断）', duration: Math.floor((now - (tc.timestamp || now)) / 1000) };
            });
            if (!changed) return msg;
            return { ...msg, steps: cleanSteps, toolCalls: cleanMsgTCs };
          });

          const lastMsg = cleanedMessages[cleanedMessages.length - 1];
          if (lastMsg && lastMsg.type === 'assistant'
            && !lastMsg.content && !lastMsg.thinking
            && !lastMsg.steps?.length && !lastMsg.toolCalls?.length
            && !lastMsg.hookApproval) {
            const { [sessionId]: _, ...rest } = state.messagesBySession;
            return {
              generatingSessions: state.generatingSessions.filter(s => s !== sessionId),
              pausedSessions: state.pausedSessions.filter(s => s !== sessionId),
              messagesBySession: { ...rest, [sessionId]: cleanedMessages.slice(0, -1) },
            };
          }
          return {
            generatingSessions: state.generatingSessions.filter(s => s !== sessionId),
            pausedSessions: state.pausedSessions.filter(s => s !== sessionId),
            messagesBySession: { ...state.messagesBySession, [sessionId]: cleanedMessages },
          };
        }),

      pauseGeneration: (sessionId) =>
        set((state) => ({
          pausedSessions: state.pausedSessions.includes(sessionId)
            ? state.pausedSessions
            : [...state.pausedSessions, sessionId],
        })),

      resumeGeneration: (sessionId) =>
        set((state) => ({
          pausedSessions: state.pausedSessions.filter((s) => s !== sessionId),
        })),

      clearMessages: (sessionId) =>
        set((state) => {
          const { [sessionId]: _, ...rest } = state.messagesBySession;
          return { messagesBySession: rest };
        }),

      removeSession: (sessionId) =>
        set((state) => {
          const { [sessionId]: _, ...restMessages } = state.messagesBySession;
          const { [sessionId]: _input, ...restInputs } = state.sessionInputs;
          return {
            messagesBySession: restMessages,
            sessionInputs: restInputs,
            generatingSessions: state.generatingSessions.filter((s) => s !== sessionId),
          };
        }),

      setSessionInput: (sessionId, input) =>
        set((state) => ({
          sessionInputs: {
            ...state.sessionInputs,
            [sessionId]: input,
          },
        })),

      getSessionInput: (sessionId) => {
        return get().sessionInputs[sessionId] || '';
      },

      attachHookApproval: (sessionId, approval) =>
        set((state) => {
          const messages = state.messagesBySession[sessionId] || [];
          if (messages.length === 0) {
            // 没有消息则创建一条
            const newMsg: Message = {
              id: `hook-approval-${approval.approvalId || Date.now()}`,
              type: 'assistant', content: '', timestamp: Date.now(),
              hookApproval: approval,
            };
            return {
              messagesBySession: { ...state.messagesBySession, [sessionId]: [newMsg] },
            };
          }
          const lastIdx = messages.length - 1;
          const lastMsg = messages[lastIdx];
          if (lastMsg && lastMsg.type === 'assistant') {
            const updated = messages.slice();
            updated[lastIdx] = { ...lastMsg, hookApproval: approval } as Message;
            return {
              messagesBySession: { ...state.messagesBySession, [sessionId]: updated },
            };
          }
          // 最后一条不是 assistant 消息，追加新消息
          const newMsg: Message = {
            id: `hook-approval-${approval.approvalId || Date.now()}`,
            type: 'assistant', content: '', timestamp: Date.now(),
            hookApproval: approval,
          };
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: [...messages, newMsg].slice(-MAX_MESSAGES_PER_SESSION),
            },
          };
        }),

      applyTombstone: (sessionId, messageIds) =>
        set((state) => {
          const messages = state.messagesBySession[sessionId];
          if (!messages || messages.length === 0) return state;
          const idSet = new Set(messageIds);
          const updated = messages.map(msg =>
            idSet.has(msg.id) ? { ...msg, deleted: true } as Message : msg
          );
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: updated,
            },
          };
        }),

      isGenerating: (sessionId) => {
        return get().generatingSessions.includes(sessionId);
      },

      isPaused: (sessionId) => {
        return get().pausedSessions.includes(sessionId);
      },
    }),
    {
      name: STORAGE_KEY,
      storage: debouncedStorage as unknown as PersistStorage<Partial<ChatState>>,
      partialize: (state: ChatState) => ({
        messagesBySession: state.messagesBySession,
        sessionInputs: state.sessionInputs,
      } as Partial<ChatState>),
    }
  )
);
