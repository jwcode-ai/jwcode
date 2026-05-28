export const COMMANDS = [
    { name: '/help', description: 'Show all commands', via: 'local', action: null },
    { name: '/plan', description: 'Toggle Plan mode (plan first, then execute)', via: 'local', action: 'plan_mode' },
    { name: '/doctor', description: 'System diagnostics (8 checks)', via: 'ws', action: 'doctor' },
    { name: '/rewind', description: 'Rewind to checkpoint', via: 'ws', action: 'rewind' },
    { name: '/update-docs', description: 'Auto-update project docs', via: 'ws', action: 'update_docs' },
    { name: '/compact', description: 'Compact context', via: 'ws', action: 'compact' },
    { name: '/model', description: 'Switch model (specify name)', via: 'ws', action: 'model_change' },
    { name: '/confirm', description: 'Confirm current plan', via: 'local', action: '__confirm_plan' },
    { name: '/cancel', description: 'Cancel current plan', via: 'local', action: '__cancel_plan' },
    { name: '/exit', description: 'Exit JWCode', via: 'local', action: '__exit__' },
];
