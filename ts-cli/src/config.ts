/**
 * Config loader — reads ~/.jwcode/config.yaml with defaults.
 */
import { readFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';

export interface JwcodeConfig {
  backend_url: string;
  ws_url: string;
  ws_auth_token: string;
  workspace_dir: string;
}

const DEFAULTS: JwcodeConfig = {
  backend_url: 'http://localhost:8080',
  ws_url: 'ws://localhost:8081/ws',
  ws_auth_token: 'default-token',
  workspace_dir: '',
};

function parseYaml(content: string): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const colonIdx = trimmed.indexOf(':');
    if (colonIdx === -1) continue;
    const key = trimmed.slice(0, colonIdx).trim();
    let value: unknown = trimmed.slice(colonIdx + 1).trim();
    if (typeof value === 'string') {
      if (value === 'true' || value === 'false') value = value === 'true';
      else if (/^\d+$/.test(value)) value = parseInt(value);
      else {
        const quoted = value.match(/^["'](.*)["']$/);
        if (quoted) value = quoted[1];
      }
    }
    result[key] = value;
  }
  return result;
}

export function loadConfig(): JwcodeConfig {
  const configDir = join(homedir(), '.jwcode');
  const configPath = join(configDir, 'config.yaml');

  if (!existsSync(configPath)) {
    return { ...DEFAULTS };
  }

  try {
    const content = readFileSync(configPath, 'utf-8');
    const parsed = parseYaml(content);
    return {
      backend_url: (parsed.backend_url as string) || DEFAULTS.backend_url,
      ws_url: (parsed.ws_url as string) || DEFAULTS.ws_url,
      ws_auth_token: (parsed.ws_auth_token as string) || DEFAULTS.ws_auth_token,
      workspace_dir: (parsed.workspace_dir as string) || DEFAULTS.workspace_dir,
    };
  } catch {
    return { ...DEFAULTS };
  }
}
