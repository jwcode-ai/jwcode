import { create } from 'zustand';
import type { Message } from '../types';

interface TokenUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  estimatedCost?: number;
}

interface TokenState {
  currentUsage: TokenUsage;
  maxContextTokens: number;
  showTokenInfo: boolean;
  model: string;
  history: { timestamp: number; usage: TokenUsage }[];

  updateUsage: (usage: Partial<TokenUsage>) => void;
  setModel: (model: string) => void;
  setMaxContextTokens: (max: number) => void;
  setShowTokenInfo: (show: boolean) => void;
  addToHistory: (usage: TokenUsage) => void;
  estimateTokens: (text: string) => number;
  recalculateFromMessages: (messages: Message[]) => void;
  pruneContext: (targetRatio: number) => { cutMessages: number; recoveredTokens: number };
  resetUsage: () => void;
}

// Rough estimation: ~4 chars per token for Chinese, ~6 for mixed
function estimateTokenCount(text: string): number {
  if (!text) return 0;
  // Count Chinese characters
  const chineseChars = (text.match(/[\u4e00-\u9fff]/g) || []).length;
  // Count other characters
  const otherChars = text.length - chineseChars;
  // Chinese ~1.5 tokens per char, English ~0.25 tokens per char
  return Math.ceil(chineseChars * 1.5 + otherChars * 0.25);
}

export const useTokenStore = create<TokenState>((set, get) => ({
  currentUsage: {
    promptTokens: 0,
    completionTokens: 0,
    totalTokens: 0,
    estimatedCost: 0,
  },
  maxContextTokens: 128000,
  showTokenInfo: false,
  model: '',
  history: [],

  updateUsage: (usage) => set((state) => ({
    currentUsage: { ...state.currentUsage, ...usage },
  })),
  setModel: (model) => set({ model }),

  setMaxContextTokens: (max) => set({ maxContextTokens: max }),

  setShowTokenInfo: (show) => set({ showTokenInfo: show }),

  addToHistory: (usage) => set((state) => ({
    history: [...state.history.slice(-100), { timestamp: Date.now(), usage }],
  })),

  estimateTokens: (text) => estimateTokenCount(text),

  recalculateFromMessages: (messages) => {
    if (!messages || messages.length === 0) {
      set({
        currentUsage: { promptTokens: 0, completionTokens: 0, totalTokens: 0, estimatedCost: 0 },
      });
      return;
    }

    const estimate = get().estimateTokens;
    let promptTokens = 0;
    let completionTokens = 0;

    for (const msg of messages) {
      // 计算消息主体内容
      let text = msg.content || '';

      // 加上 thinking 内容
      if (msg.thinking) text += ' ' + msg.thinking;

      // 加上 steps 中的 thought/action/result
      if (msg.steps) {
        for (const step of msg.steps) {
          if (step.thought) text += ' ' + step.thought;
          if (step.action) text += ' ' + step.action;
          if (step.result) text += ' ' + step.result;
        }
      }

      // 加上 toolCalls 参数
      if (msg.toolCalls) {
        for (const tc of msg.toolCalls) {
          if (tc.args) text += ' ' + (typeof tc.args === 'string' ? tc.args : JSON.stringify(tc.args));
        }
      }

      const tokens = estimate(text);
      if (msg.type === 'user') {
        promptTokens += tokens;
      } else {
        completionTokens += tokens;
      }
    }

    const totalTokens = promptTokens + completionTokens;

    // 粗略成本估算: $0.15/M prompt, $0.60/M completion (GPT-4o 参考)
    const estimatedCost = (promptTokens / 1_000_000) * 0.15 + (completionTokens / 1_000_000) * 0.60;

    set({
      currentUsage: { promptTokens, completionTokens, totalTokens, estimatedCost },
    });
  },

  pruneContext: (targetRatio) => {
    const state = get();
    const targetTokens = Math.floor(state.currentUsage.totalTokens * targetRatio);
    if (targetTokens >= state.currentUsage.totalTokens) {
      return { cutMessages: 0, recoveredTokens: 0 };
    }
    
    // The actual pruning logic will be handled externally
    // This just reports what could be saved
    const tokensToRecover = state.currentUsage.totalTokens - targetTokens;
    const estimatedMessagesToCut = Math.ceil(tokensToRecover / 200); // ~200 tokens per message avg
    
    // Update the usage after pruning
    set({
      currentUsage: {
        ...state.currentUsage,
        promptTokens: Math.max(0, state.currentUsage.promptTokens - tokensToRecover),
        totalTokens: targetTokens,
      }
    });

    return {
      cutMessages: estimatedMessagesToCut,
      recoveredTokens: tokensToRecover,
    };
  },

  resetUsage: () => set({
    currentUsage: { promptTokens: 0, completionTokens: 0, totalTokens: 0, estimatedCost: 0 },
  }),
}));
