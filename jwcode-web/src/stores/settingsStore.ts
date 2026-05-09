import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Settings, AdvancedSettings } from '../types';

interface SettingsState extends Settings, AdvancedSettings {
  // 工作目录
  workspaceDir: string;

  // Actions
  setTheme: (theme: 'dark' | 'light' | 'auto') => void;
  setLanguage: (language: string) => void;
  setFontSize: (fontSize: number) => void;
  setStreamingEnabled: (enabled: boolean) => void;
  setThinkingEnabled: (enabled: boolean) => void;
  setYoloEnabled: (enabled: boolean) => void;
  setAutoSwarmEnabled: (enabled: boolean) => void;
  setAutoAIEnabled: (enabled: boolean) => void;
  setCompressionEnabled: (enabled: boolean) => void;
  setCompressionMaxMessages: (value: number) => void;
  setCompressionTokenThreshold: (value: number) => void;
  setWorkspaceDir: (dir: string) => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'dark',
      language: 'zh-CN',
      fontSize: 14,
      streamingEnabled: true,
      thinking: { enabled: false },
      yolo: { enabled: false },
      autoSwarm: { enabled: false },
      autoAI: { enabled: false },
      compression: {
        enabled: false,
        maxMessages: 50,
        tokenThreshold: 4000,
      },
      workspaceDir: 'c:\\Users\\HUAWEI\\Desktop\\jwcode',

      setTheme: (theme) => set({ theme }),
      setLanguage: (language) => set({ language }),
      setFontSize: (fontSize) => set({ fontSize }),
      setStreamingEnabled: (streamingEnabled) => set({ streamingEnabled }),
      setThinkingEnabled: (enabled) => set({ thinking: { enabled } }),
      setYoloEnabled: (enabled) => set({ yolo: { enabled } }),
      setAutoSwarmEnabled: (enabled) => set({ autoSwarm: { enabled } }),
      setAutoAIEnabled: (enabled) => set({ autoAI: { enabled } }),
      setCompressionEnabled: (enabled) =>
        set((state) => ({
          compression: { ...state.compression, enabled },
        })),
      setCompressionMaxMessages: (maxMessages) =>
        set((state) => ({
          compression: { ...state.compression, maxMessages },
        })),
      setCompressionTokenThreshold: (tokenThreshold) =>
        set((state) => ({
          compression: { ...state.compression, tokenThreshold },
        })),
      setWorkspaceDir: (dir) => set({ workspaceDir: dir }),
    }),
    {
      name: 'jwcode-settings-storage',
    }
  )
);
