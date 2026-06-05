#!/usr/bin/env node
/**
 * JWCode TypeScript CLI — entry point.
 *
 * Commands:
 *   jwcode start [-p <port>] [-B] [-w <dir>]
 *   jwcode run [-b <url>] [--ws <url>]
 *   jwcode version
 *
 * jwcode (no subcommand) defaults to 'start' — launches backend
 * and opens the interactive TUI. The current working directory
 * becomes the workspace directory.
 */
import { render } from 'ink';
import { createElement } from 'react';
import { App } from './App.js';
import { SetupWizard } from './components/SetupWizard.js';

// EPIPE / ECONNRESET on process streams means the remote end disappeared.
// Attach no-op error listeners so they never become unhandled crashes.
['stdout', 'stderr'].forEach(name => {
  const stream = (process as any)[name];
  if (stream) {
    stream.on('error', (e: NodeJS.ErrnoException) => {
      if (e.code === 'EPIPE' || e.code === 'ECONNRESET') { /* suppress */ }
    });
  }
});

// Last-resort safety net: swallow EPIPE/ECONNRESET so the reconnect loop
// can keep running instead of crashing the process.
process.on('uncaughtException', (err: NodeJS.ErrnoException) => {
  if (err.code === 'EPIPE' || err.code === 'ECONNRESET') {
    return;
  }
  console.error('Fatal error:', err);
  process.exit(1);
});
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import {
  findInstallDir, findJar, findMvn, buildBackend,
  startBackend, waitForBackend, cleanupBackend,
  findAvailablePorts, portInUse, killPort,
} from './launcher.js';
import { loadConfig } from './config.js';
import type { ChildProcess } from 'node:child_process';

const VERSION = '3.0.0';

function printUsage(): void {
  console.log(`JWCode CLI v${VERSION}`);
  console.log('');
  console.log('Usage:');
  console.log('  jwcode [options]              Start backend + interactive terminal (default)');
  console.log('  jwcode start [options]        Start backend + interactive terminal');
  console.log('  jwcode run [options]          Connect to existing backend');
  console.log('  jwcode version                Print version');
  console.log('');
  console.log('Options:');
  console.log('  -p, --port <port>             HTTP port (default: auto, first available from 8080)');
  console.log('  -w, --workspace <dir>         Workspace directory (default: current directory)');
  console.log('  -F, --force                   Kill existing process on target port before starting');
  console.log('  -B, --build                   Force rebuild backend (dev mode only)');
  console.log('  -b, --backend <url>           Backend URL (run mode, WS auto-derived)');
  console.log('  --ws <url>                    Override WebSocket URL');
  console.log('');
  console.log('Environment:');
  console.log('  JWCODE_THEME=dark|light       Color theme (default: dark)');
  console.log('');
  console.log('Keyboard shortcuts (in TUI):');
  console.log('  /             Open command palette');
  console.log('  Tab           Toggle Plan/Act mode');
  console.log('  Up/Down       Browse input history (last 30)');
  console.log('  PgUp/PgDn     Scroll message history');
  console.log('  Home/End      Jump to oldest/newest message');
  console.log('  Esc           Close palette / deny approval');
}

function parseArgs(): Record<string, string | boolean> {
  const args: Record<string, string | boolean> = {};
  const argv = process.argv.slice(2);
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--build' || arg === '-B') args.build = true;
    else if (arg === '--force' || arg === '-F') args.force = true;
    else if (arg === '--port' || arg === '-p') args.port = argv[++i];
    else if (arg === '--workspace' || arg === '-w') args.workspace = argv[++i];
    else if (arg === '--backend' || arg === '-b') args.backend = argv[++i];
    else if (arg === '--ws') args.ws = argv[++i];
    else if (!arg.startsWith('-')) args._cmd = arg;
  }
  return args;
}

async function cmdStart(args: Record<string, string | boolean>): Promise<void> {
  const forceKill = !!args.force;
  const build = !!args.build;
  const workspaceDir = String(args.workspace || process.cwd());
  const installDir = findInstallDir();

  // Determine ports: explicit -p flag, or auto-detect available pair
  let httpPort: number;
  let wsPort: number;
  if (args.port) {
    httpPort = parseInt(String(args.port), 10);
    wsPort = httpPort + 1;
    if (!forceKill && (portInUse(httpPort) || portInUse(wsPort))) {
      console.error(`[launcher] Port ${httpPort} or ${wsPort} is already in use.`);
      console.error('[launcher] Use --force to replace the existing process, or omit -p for auto port selection.');
      process.exit(1);
    }
  } else {
    const ports = findAvailablePorts(8080);
    httpPort = ports.httpPort;
    wsPort = ports.wsPort;
  }

  console.log('╔══════════════════════════════════════╗');
  console.log('║   JWCode — Java AI Coding Tool       ║');
  console.log('╚══════════════════════════════════════╝');
  console.log(`  Workspace: ${workspaceDir}`);
  console.log(`  HTTP API:  http://localhost:${httpPort}`);
  console.log(`  WebSocket: ws://localhost:${wsPort}/ws`);

  // Build if requested or if jar doesn't exist (dev mode)
  if (build || !findJar(installDir)) {
    if (existsSync(join(installDir, 'pom.xml'))) {
      buildBackend(installDir);
    } else if (!findJar(installDir)) {
      console.error('[launcher] Backend JAR not found and no pom.xml for build.');
      console.error('[launcher] Install via: npm install -g @jwcode/cli');
      process.exit(1);
    }
  }

  // Start Java backend
  const backendProc = startBackend({ installDir, workspaceDir, port: httpPort, wsPort, forceKill });

  // Cleanup on exit
  let stopping = false;
  const cleanup = () => {
    if (stopping) return;
    stopping = true;
    // Restore main screen buffer before printing shutdown message
    process.stdout.write('\x1b[?1049l');
    console.log('\n[jwcode] Shutting down...');
    cleanupBackend(backendProc);
  };

  process.on('SIGINT', () => { cleanup(); process.exit(0); });
  process.on('SIGTERM', () => { cleanup(); process.exit(0); });
  process.on('exit', cleanup);

  // Wait for backend or detect early exit
  const backendDead = new Promise<void>((resolve) => {
    backendProc.on('exit', (code) => {
      if (code !== 0 && code !== null) resolve();
    });
  });

  await Promise.race([waitForBackend(httpPort), backendDead]);

  if (backendProc.exitCode !== null && backendProc.exitCode !== 0) {
    console.error('[launcher] Backend failed to start. Exiting.');
    cleanup();
    process.exit(1);
  }

  // Start TUI
  const backendUrl = `http://localhost:${httpPort}`;
  const wsUrl = `ws://localhost:${wsPort}/ws`;


  // Enter alternate screen buffer so Ink renders in an isolated buffer.
  // This eliminates terminal-level flicker caused by frequent re-renders
  // (content flush @31Hz + token updates @10Hz) colliding with scrollback.
  process.stdout.write('\x1b[?1049h');
  // Clear the alternate screen for a clean start.
  process.stdout.write('\x1b[2J\x1b[H');

  // First-run: check if a provider is configured, show setup wizard if not
  let providerConfigured = false;
  try {
    const statusRes = await fetch(`${backendUrl}/api/config/provider`);
    const status = await statusRes.json() as any;
    providerConfigured = status?.data?.configured === true;
  } catch { /* proceed to wizard if status check fails */ }

  if (!providerConfigured) {
    // Render setup wizard instead of main app
    await new Promise<void>((resolve) => {
      const { unmount } = render(
        createElement(SetupWizard, {
          backendUrl,
          onComplete: () => { unmount(); resolve(); },
        }),
      );
    });
  }

  const { unmount } = render(
    createElement(App, {
      backendUrl,
      wsUrl,
      onExit: () => {
        // Restore main screen before exit
        process.stdout.write('\x1b[?1049l');
        cleanup();
        process.exit(0);
      },
    }),
  );

  // Keep alive
  await new Promise<void>((resolve) => {
    process.on('SIGINT', () => resolve());
    process.on('SIGTERM', () => resolve());
  });
  // Restore main screen on normal exit
  process.stdout.write('\x1b[?1049l');
}

async function cmdRun(args: Record<string, string | boolean>): Promise<void> {
  if (!process.stdin.isTTY) {
    console.error('Error: jwcode requires a real terminal (TTY).');
    console.error('Please run from a terminal emulator like Windows Terminal, CMD, or PowerShell.');
    process.exit(1);
  }

  // Enable bracketed paste mode so the terminal wraps pasted content
  // in \e[200~ ... \e[201~ markers. TextInput detects these to show a
  // "[Pasted text #N +X chars]" summary instead of flooding the input line.
  // Only enable if the terminal supports it (modern Windows Terminal, iTerm2, etc.)
  if (process.env.JWCODE_NO_BRACKETED_PASTE !== '1') {
    process.stdout.write('\x1b[?2004h');
  }

  const config = loadConfig();
  const backendUrl = String(args.backend || config.backend_url);
  // Derive WS URL from backend URL (port+1), or explicit override, or config
  const wsUrl = String(args.ws || backendUrl.replace(/^http/, 'ws').replace(/:(\d+)/, (_, p) => ':' + (parseInt(p) + 1)) + '/ws' || config.ws_url);

  // Enter alternate screen buffer for flicker-free rendering
  process.stdout.write('\x1b[?1049h');
  process.stdout.write('\x1b[2J\x1b[H');

  const { unmount, waitUntilExit } = render(
    createElement(App, {
      backendUrl,
      wsUrl,
      onExit: () => {
        process.stdout.write('\x1b[?1049l');
        process.exit(0);
      },
    }),
  );

  await waitUntilExit();
  // Restore main screen on normal exit
  process.stdout.write('\x1b[?1049l');
}

async function main(): Promise<void> {
  // Handle --help / --version first regardless of command position
  if (process.argv.includes('--help') || process.argv.includes('-h') || process.argv.includes('help')) {
    printUsage();
    return;
  }
  if (process.argv.includes('--version') || process.argv.includes('-v') || process.argv.includes('version')) {
    console.log(`JWCode CLI v${VERSION}`);
    return;
  }

  const args = parseArgs();
  const cmd = (args._cmd as string) || 'start';

  switch (cmd) {
    case 'start':
      await cmdStart(args);
      break;
    case 'run':
      await cmdRun(args);
      break;
    default:
      console.error(`Unknown command: ${cmd}`);
      printUsage();
      process.exit(1);
  }
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
