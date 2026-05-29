export type ThemeName = 'dark' | 'light';
interface ThemeColors {
    bg: string;
    text: string;
    muted: string;
    border: string;
    primary: string;
    success: string;
    warning: string;
    error: string;
    info: string;
    user: string;
    assistant: string;
    system: string;
    tool: string;
    thinking: string;
    plan: string;
    auto: string;
    connected: string;
    disconnected: string;
}
export declare function getTheme(): ThemeColors;
export declare function setTheme(name: ThemeName): void;
export declare const t: ThemeColors;
export {};
