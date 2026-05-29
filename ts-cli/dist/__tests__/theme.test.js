import { describe, it, expect, afterEach } from 'vitest';
import { getTheme, setTheme, t } from '../theme.js';
describe('theme', () => {
    afterEach(() => {
        setTheme('dark');
    });
    it('default theme is dark', () => {
        const theme = getTheme();
        expect(theme.primary).toBe('cyan');
        expect(theme.success).toBe('green');
        expect(theme.error).toBe('red');
    });
    it('setTheme changes current theme', () => {
        setTheme('light');
        const theme = getTheme();
        expect(theme.muted).toBe('blackBright');
    });
    it('all required color keys exist', () => {
        const required = [
            'bg', 'text', 'muted', 'border',
            'primary', 'success', 'warning', 'error', 'info',
            'user', 'assistant', 'system', 'tool', 'thinking',
            'plan', 'auto', 'connected', 'disconnected',
        ];
        const theme = getTheme();
        for (const key of required) {
            expect(theme).toHaveProperty(key);
        }
    });
    it('module-level t exports dark theme initially', () => {
        expect(t.primary).toBe('cyan');
    });
    it('theme switches preserve all keys', () => {
        setTheme('light');
        const light = getTheme();
        setTheme('dark');
        const dark = getTheme();
        expect(Object.keys(light)).toEqual(Object.keys(dark));
    });
});
