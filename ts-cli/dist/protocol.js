// Event types from Java backend MessageType enum
export const EVENT_TYPES = [
    'connected', 'session_created',
    'auth_required', 'auth_success', 'auth_failed',
    'start', 'content', 'thinking',
    'tool_call', 'tool_result',
    'step_start', 'step_thinking', 'step_action', 'step_complete',
    'progress', 'complete', 'error',
    'ping', 'pong', 'log',
    'commands_list', 'notification',
    'plan_start', 'plan_thinking', 'plan_tasks',
    'plan_task_start', 'plan_task_update', 'plan_task_result',
    'plan_complete', 'plan_error', 'plan_mode_change',
    'workspace_changed',
    'generation_paused', 'generation_resumed',
    'token_update',
    'hook_ask',
    'doctor_result',
];
export const SLASH_COMMANDS = {
    '/help': null,
    '/plan': 'plan_mode',
    '/doctor': 'doctor',
    '/rewind': 'rewind',
    '/update-docs': 'update_docs',
    '/docs': 'update_docs',
    '/model': 'model_change',
    '/compact': 'compact',
    '/exit': '__exit__',
    '/quit': '__exit__',
    '/confirm': '__confirm_plan',
    '/cancel': '__cancel_plan',
};
export function parseData(m) {
    if (typeof m.data === 'string') {
        try {
            return JSON.parse(m.data);
        }
        catch {
            return {};
        }
    }
    return m.data || {};
}
export function createMessage(type, content = '') {
    return {
        id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        type,
        content,
        thinking: '',
        steps: [],
        toolCalls: [],
        timestamp: Date.now(),
    };
}
