package com.jwcode.core.command;

/**
 * Origin category of a command, used by the unified command manifest
 * served via {@code GET /api/commands} and the {@code command_execute} WS path.
 */
public enum CommandSource {
    /** Core lifecycle commands (help, exit, clear, eval, ...). */
    CORE,
    /** Commands operating on session state (rewind, compact, tokens, ...). */
    SESSION,
    /** Commands operating on the workspace (search, memory, init, project, ...). */
    WORKSPACE,
    /** Commands invoking tools / external processes (test, lint, doctor, ...). */
    TOOLS,
    /** Commands managing configuration (config, model, effort, mcp, ...). */
    CONFIG
}
