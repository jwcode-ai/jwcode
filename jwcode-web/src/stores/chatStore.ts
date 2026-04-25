import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Message, Step, ToolCall } from '../types';

interface ChatState {
  messages: Message[];
  currentSessionId: string;
  isGenerating: boolean;
  hasError: boolean;
  
  // Actions
  addMessage: (message: Message) => void;
  updateLastMessage: (content: string) => void;
  appendToLastMessage: (content: string) => void;
  setThinking: (thinking: string) => void;
  appendToLastMessageThinking: (chunk: string) => void;
  addStep: (step: Step) => void;
  updateStep: (stepId: string, updates: Partial<Step>) => void;
  addToolCall: (toolCall: ToolCall) => void;
  updateToolCall: (toolCallId: string, updates: Partial<ToolCall>) => void;
  appendToLastToolCallArgs: (toolCallId: string, argsPartial: string, index?: number) => void;
  startGeneration: () => void;
  endGeneration: (error?: string) => void;
  clearMessages: () => void;
  setCurrentSessionId: (sessionId: string) => void;
}

export const useChatStore = create<ChatState>()(
  persist(
    (set) => ({
      messages: [],
      currentSessionId: `session-${Date.now()}`,
      isGenerating: false,
      hasError: false,

      addMessage: (message) =>
        set((state) => ({
          messages: [...state.messages, message],
        })),

      updateLastMessage: (content) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.type === 'assistant') {
            lastMessage.content = content;
          }
          return { messages };
        }),

      appendToLastMessage: (content) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.type === 'assistant') {
            lastMessage.content += content;
          }
          return { messages };
        }),

      setThinking: (thinking) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.type === 'assistant') {
            // 优先写入最后一条 running Step 的 thought
            const runningStep = lastMessage.steps?.find((s) => s.status === 'running');
            if (runningStep) {
              runningStep.thought = thinking;
            } else {
              lastMessage.thinking = thinking;
            }
          }
          return { messages };
        }),

      appendToLastMessageThinking: (chunk) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.type === 'assistant') {
            const runningStep = lastMessage.steps?.find((s) => s.status === 'running');
            if (runningStep) {
              runningStep.thought = (runningStep.thought || '') + chunk;
            } else if (lastMessage.steps && lastMessage.steps.length > 0) {
              // 没有 running step 时，追加到最后一个 step
              const lastStep = lastMessage.steps[lastMessage.steps.length - 1];
              lastStep.thought = (lastStep.thought || '') + chunk;
            } else {
              lastMessage.thinking = (lastMessage.thinking || '') + chunk;
            }
          }
          return { messages };
        }),

      addStep: (step) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.type === 'assistant') {
            // 将未被任何 step 引用的 toolCalls 转移到新 step
            const orphanedToolCalls = lastMessage.toolCalls?.filter(tc =>
              !lastMessage.steps?.some(s => s.toolCalls?.some(stc => stc.id === tc.id))
            );

            const newStep = { ...step };
            if (orphanedToolCalls?.length) {
              newStep.toolCalls = [...(newStep.toolCalls || []), ...orphanedToolCalls];
              lastMessage.toolCalls = lastMessage.toolCalls?.filter(tc =>
                !orphanedToolCalls.some(otc => otc.id === tc.id)
              );
            }

            // 将孤儿的 thinking 转移到新 step 的 thought
            if (lastMessage.thinking && lastMessage.thinking.trim().length > 0) {
              newStep.thought = (newStep.thought || '') + lastMessage.thinking;
              lastMessage.thinking = '';
            }

            lastMessage.steps = [...(lastMessage.steps || []), newStep];
          }
          return { messages };
        }),

      updateStep: (stepId, updates) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.steps) {
            const stepIndex = lastMessage.steps.findIndex((s) => s.id === stepId);
            if (stepIndex !== -1) {
              lastMessage.steps[stepIndex] = { ...lastMessage.steps[stepIndex], ...updates };
            }
          }
          return { messages };
        }),

      addToolCall: (toolCall) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.type === 'assistant') {
            // 优先写入最后一条 running Step 的 toolCalls
            const runningStep = lastMessage.steps?.find((s) => s.status === 'running');
            if (runningStep) {
              runningStep.toolCalls = [...(runningStep.toolCalls || []), toolCall];
            } else if (lastMessage.steps && lastMessage.steps.length > 0) {
              // 没有 running step 时，追加到最后一个 step
              const lastStep = lastMessage.steps[lastMessage.steps.length - 1];
              lastStep.toolCalls = [...(lastStep.toolCalls || []), toolCall];
            } else {
              lastMessage.toolCalls = [...(lastMessage.toolCalls || []), toolCall];
            }
          }
          return { messages };
        }),

      updateToolCall: (toolCallId, updates) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (!lastMessage || lastMessage.type !== 'assistant') return { messages };

          // 辅助函数：更新 toolCalls 数组中的指定 toolCall
          const doUpdate = (toolCalls: ToolCall[] | undefined): boolean => {
            if (!toolCalls) return false;
            const toolCallIndex = toolCalls.findIndex((tc) => tc.id === toolCallId);
            if (toolCallIndex === -1) return false;
            toolCalls[toolCallIndex] = {
              ...toolCalls[toolCallIndex],
              ...updates,
            };
            return true;
          };

          // 优先在 running Step 的 toolCalls 中查找
          const runningStep = lastMessage.steps?.find((s) => s.status === 'running');
          if (runningStep && doUpdate(runningStep.toolCalls)) {
            return { messages };
          }

          // Fallback 到 message.toolCalls
          if (doUpdate(lastMessage.toolCalls)) {
            return { messages };
          }

          return { messages };
        }),

      appendToLastToolCallArgs: (toolCallId, argsPartial, index) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (!lastMessage || lastMessage.type !== 'assistant') return { messages };

          // 辅助函数：更新 toolCalls 数组中的指定 toolCall
          const updateToolCall = (toolCalls: ToolCall[] | undefined): boolean => {
            if (!toolCalls) return false;
            const toolCallIndex = toolCalls.findIndex((tc) =>
              (index !== undefined && tc.index === index) || tc.id === toolCallId
            );
            if (toolCallIndex === -1) return false;
            const existing = toolCalls[toolCallIndex];
            let mergedArgs = existing.args;
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
            toolCalls[toolCallIndex] = { ...existing, args: mergedArgs };
            return true;
          };

          // 优先在 running Step 的 toolCalls 中查找
          const runningStep = lastMessage.steps?.find((s) => s.status === 'running');
          if (runningStep && updateToolCall(runningStep.toolCalls)) {
            return { messages };
          }

          // Fallback 到 message.toolCalls
          if (updateToolCall(lastMessage.toolCalls)) {
            return { messages };
          }

          return { messages };
        }),

      startGeneration: () =>
        set({
          isGenerating: true,
          hasError: false,
        }),

      endGeneration: () =>
        set({
          isGenerating: false,
          hasError: false,
        }),

      clearMessages: () =>
        set({
          messages: [],
        }),

      setCurrentSessionId: (sessionId) =>
        set({ currentSessionId: sessionId }),
    }),
    {
      name: 'jwcode-chat-storage',
      partialize: (state) => ({
        currentSessionId: state.currentSessionId,
      }),
    }
  )
);