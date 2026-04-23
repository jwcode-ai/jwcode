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
  addStep: (step: Step) => void;
  updateStep: (stepId: string, updates: Partial<Step>) => void;
  addToolCall: (toolCall: ToolCall) => void;
  updateToolCall: (toolCallId: string, updates: Partial<ToolCall>) => void;
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
            lastMessage.thinking = thinking;
          }
          return { messages };
        }),

      addStep: (step) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.type === 'assistant') {
            lastMessage.steps = [...(lastMessage.steps || []), step];
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
            lastMessage.toolCalls = [...(lastMessage.toolCalls || []), toolCall];
          }
          return { messages };
        }),

      updateToolCall: (toolCallId, updates) =>
        set((state) => {
          const messages = [...state.messages];
          const lastMessage = messages[messages.length - 1];
          if (lastMessage && lastMessage.toolCalls) {
            const toolCallIndex = lastMessage.toolCalls.findIndex((tc) => tc.id === toolCallId);
            if (toolCallIndex !== -1) {
              lastMessage.toolCalls[toolCallIndex] = {
                ...lastMessage.toolCalls[toolCallIndex],
                ...updates,
              };
            }
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