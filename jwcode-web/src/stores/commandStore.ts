import { create } from 'zustand';

/**
 * Backend command descriptor (served by GET /api/commands).
 */
export interface BackendCommand {
  name: string;
  description: string;
  usage: string;
  category?: string;
  source?: string;
  requiresArgs?: boolean;
  aliases?: string[];
}

interface CommandState {
  /** Commands fetched from the backend manifest. */
  backendCommands: BackendCommand[];
  /** Whether the manifest has been fetched. */
  loaded: boolean;
  setBackendCommands: (commands: BackendCommand[]) => void;
  getAllCommands: () => BackendCommand[];
}

export const useCommandStore = create<CommandState>()((set, get) => ({
  backendCommands: [],
  loaded: false,

  setBackendCommands: (commands: BackendCommand[]) => {
    set({ backendCommands: commands, loaded: true });
  },

  getAllCommands: () => {
    return get().backendCommands;
  },
}));

/**
 * Fetch the unified command manifest from the backend.
 *
 * <p>The Java backend is the single source of truth for slash commands; the web
 * frontend pulls the full list here and merges it with its local UI commands.
 * Uses a relative URL so it resolves against the same origin that served the UI.
 */
export async function fetchCommands(baseUrl = ''): Promise<BackendCommand[]> {
  try {
    const resp = await fetch(`${baseUrl}/api/commands`);
    if (!resp.ok) return [];
    const body = await resp.json();
    const cmds: BackendCommand[] = (body && body.data) || [];
    useCommandStore.getState().setBackendCommands(cmds);
    return cmds;
  } catch {
    return [];
  }
}
