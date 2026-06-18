import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Settings, AdvancedSettings, CustomThemeColors, DEFAULT_DARK_THEME } from '../types';

interface SettingsState extends Settings, AdvancedSettings {
  // 工作目录
  workspaceDir: string;
  // 自定义主题颜色
  customTheme: CustomThemeColors;
  customThemeEnabled: boolean;
  // 工作区守卫绕过
  workspaceGuardBypass: boolean;

  // Actions
  setTheme: (theme: 'dark' | 'light' | 'auto') => void;
  setThemePreset: (presetId: string) => void;
  setLanguage: (language: string) => void;
  setFontSize: (fontSize: number) => void;
  setStreamingEnabled: (enabled: boolean) => void;
  setYoloEnabled: (enabled: boolean) => void;
  setAutoSwarmEnabled: (enabled: boolean) => void;
  setWorkspaceDir: (dir: string) => void;
  setWorkspaceGuardBypass: (bypass: boolean) => void;
  setCustomTheme: (colors: Partial<CustomThemeColors>) => void;
  setCustomThemeEnabled: (enabled: boolean) => void;
  resetCustomTheme: () => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'dark',
      themePresetId: 'github',
      language: 'zh-CN',
      fontSize: 14,
      streamingEnabled: true,
      yolo: { enabled: false },
      autoSwarm: { enabled: false },
      workspaceDir: 'c:\\Users\\HUAWEI\\Desktop\\jwcode',
      workspaceGuardBypass: true,
      customTheme: DEFAULT_DARK_THEME,
      customThemeEnabled: false,

      setTheme: (theme) => set({ theme }),
      setThemePreset: (themePresetId) => set({ themePresetId, customThemeEnabled: false }),
      setLanguage: (language) => set({ language }),
      setFontSize: (fontSize) => set({ fontSize }),
      setStreamingEnabled: (streamingEnabled) => set({ streamingEnabled }),
      setYoloEnabled: (enabled) => set({ yolo: { enabled } }),
      setAutoSwarmEnabled: (enabled) => set({ autoSwarm: { enabled } }),
      setCustomTheme: (colors) => set((state) => ({ customTheme: { ...state.customTheme, ...colors } })),
      setCustomThemeEnabled: (enabled) => set({ customThemeEnabled: enabled }),
      setWorkspaceGuardBypass: (bypass) => set({ workspaceGuardBypass: bypass }),
      resetCustomTheme: () => set({ customTheme: DEFAULT_DARK_THEME }),
      setWorkspaceDir: (dir) => set({ workspaceDir: dir }),
    }),
    {
      name: 'jwcode-settings-storage',
    }
  )
);
