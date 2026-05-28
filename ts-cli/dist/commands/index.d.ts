/**
 * Command registry — slash command definitions.
 * Pattern adapted from claude-code-source-main/src/commands.ts
 */
export interface CommandDef {
    name: string;
    description: string;
    via: 'local' | 'ws';
    action: string | null;
}
export declare const COMMANDS: CommandDef[];
