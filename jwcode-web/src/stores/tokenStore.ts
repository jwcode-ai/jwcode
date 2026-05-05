import { create } from 'zustand';

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
  history: { timestamp: number; usage: TokenUsage }[];

  updateUsage: (usage: Partial<TokenUsage>) => void;
  setMaxContextTokens: (max: number) => void;
  setShowTokenInfo: (show: boolean) => void;
  addToHistory: (usage: TokenUsage) => void;
  estimateTokens: (text: string) => number;
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
  maxContextTokens: 128000, // Default for most modern models
  showTokenInfo: false,
  history: [],

  updateUsage: (usage) => set((state) => ({
    currentUsage: { ...state.currentUsage, ...usage },
  })),

  setMaxContextTokens: (max) => set({ maxContextTokens: max }),

  setShowTokenInfo: (show) => set({ showTokenInfo: show }),

  addToHistory: (usage) => set((state) => ({
    history: [...state.history.slice(-100), { timestamp: Date.now(), usage }],
  })),

  estimateTokens: (text) => estimateTokenCount(text),

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
