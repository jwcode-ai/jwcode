/**
 * Simple file logger for ts-cli.
 * Writes timestamped log lines to ~/.jwcode/logs/ts-cli.log.
 */
import { appendFileSync, mkdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { homedir, EOL } from 'node:os';

const LOG_DIR = join(homedir(), '.jwcode', 'logs');
const LOG_FILE = join(LOG_DIR, 'ts-cli.log');

function ensureLogDir(): void {
  if (!existsSync(LOG_DIR)) {
    mkdirSync(LOG_DIR, { recursive: true });
  }
}

function log(level: string, msg: string): void {
  try {
    ensureLogDir();
    const d = new Date();
    const ts = d.toISOString().replace('T', ' ').slice(0, 23);
    appendFileSync(LOG_FILE, '[' + ts + '] [' + level + '] ' + msg + '\n', 'utf-8');
  } catch {}
}

export const logger = {
  info: (msg: string) => log('INFO', msg),
  warn: (msg: string) => log('WARN', msg),
  error: (msg: string) => log('ERROR', msg),
  errorStderr: (msg: string) => { log('ERROR', msg); console.error(msg); },
};

export { LOG_DIR, LOG_FILE };
