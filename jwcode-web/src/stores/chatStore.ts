import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Message, Step, ToolCall } from '../types';

const MAX_MESSAGES_PER_SESSION = 200;
const STORAGE_KEY = 'jwcode-chat-store';

interface ChatState {
  // 多会话消息存储：sessionId → Message[]
  messagesBySession: Record<string, Message[]>;
  // 正在生成的会话集合
  generatingSessions: string[];
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
  clearMessages: (sessionId: string) => void;
  removeSession: (sessionId: string) => void;
  setSessionInput: (sessionId: string, input: string) => void;
  getSessionInput: (sessionId: string) => string;
  isGenerating: (sessionId: string) => boolean;
}

export const useChatStore = create<ChatState>()(
  persist(
    (set, get) => ({
      messagesBySession: {},
      generatingSessions: [],
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
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) =>
            index === arr.length - 1 && msg.type === 'assistant'
              ? { ...msg, content: msg.content + content }
              : msg
          );
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      setThinking: (sessionId, thinking) =>
        set((state) => {
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) => {
            if (index !== arr.length - 1 || msg.type !== 'assistant') return msg;
            const runningStepIndex = msg.steps?.findIndex((s) => s.status === 'running');
            if (runningStepIndex !== undefined && runningStepIndex !== -1 && msg.steps && msg.steps[runningStepIndex]) {
              const newSteps = msg.steps.map((s, i) =>
                i === runningStepIndex ? { ...s, thought: thinking } : s
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
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) => {
            if (index !== arr.length - 1 || msg.type !== 'assistant') return msg;

            const runningStepIndex = msg.steps?.findIndex((s) => s.status === 'running');
            if (runningStepIndex !== undefined && runningStepIndex !== -1 && msg.steps && msg.steps[runningStepIndex]) {
              const newSteps = msg.steps.map((s, i) =>
                i === runningStepIndex
                  ? { ...s, thought: (s.thought || '') + chunk }
                  : s
              );
              return { ...msg, steps: newSteps };
            }

            if (msg.steps && msg.steps.length > 0) {
              const lastStepIndex = msg.steps.length - 1;
              const newSteps = msg.steps.map((s, i) =>
                i === lastStepIndex
                  ? { ...s, thought: (s.thought || '') + chunk }
                  : s
              );
              return { ...msg, steps: newSteps };
            }

            return { ...msg, thinking: (msg.thinking || '') + chunk };
          });
          return {
            messagesBySession: {
              ...state.messagesBySession,
              [sessionId]: messages,
            },
          };
        }),

      addStep: (sessionId, step) =>
        set((state) => {
          const messages = (state.messagesBySession[sessionId] || []).map((msg, index, arr) => {
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
            };
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
              try {
                const parsed = JSON.parse(argsPartial);
                mergedArgs = { ...(mergedArgs as Record<string, unknown>), ...parsed };
              } catch {
                mergedArgs = JSON.stringify(mergedArgs) + argsPartial;
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
        set((state) => ({
          generatingSessions: state.generatingSessions.filter((s) => s !== sessionId),
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

      isGenerating: (sessionId) => {
        return get().generatingSessions.includes(sessionId);
      },
    }),
    {
      name: STORAGE_KEY,
      partialize: (state) => ({
        messagesBySession: state.messagesBySession,
        sessionInputs: state.sessionInputs,
      }),
    }
  )
);
