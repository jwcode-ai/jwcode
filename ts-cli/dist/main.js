#!/usr/bin/env node
/**
 * JWCode TypeScript CLI — entry point.
 *
 * Commands:
 *   jwcode start [--port 8080] [--ws-port 8081] [--build]
 *   jwcode run [--backend http://localhost:8080] [--ws ws://localhost:8081/ws]
 *   jwcode version
 */
import { render } from 'ink';
import { createElement } from 'react';
import { App } from './App.js';
import { findProjectRoot, jarExists, buildBackend, startBackend, waitForBackend, cleanupBackend, } from './launcher.js';
import { loadConfig } from './config.js';
const VERSION = '3.0.0';
function printUsage() {
    console.log(`JWCode CLI v${VERSION}`);
    console.log('');
    console.log('Usage:');
    console.log('  jwcode start [options]    Start backend + interactive terminal');
    console.log('  jwcode run [options]      Connect to existing backend');
    console.log('  jwcode version            Print version');
    console.log('');
    console.log('Options:');
    console.log('  --port, -p <port>         Backend HTTP port (default: 8080)');
    console.log('  --ws-port <port>          WebSocket port (default: 8081)');
    console.log('  --build, -B               Force rebuild backend');
    console.log('  --backend, -b <url>       Backend URL (run mode)');
    console.log('  --ws <url>                WebSocket URL (run mode)');
}
function parseArgs() {
    const args = {};
    const argv = process.argv.slice(2);
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i];
        if (arg === '--build' || arg === '-B')
            args.build = true;
        else if (arg === '--port' || arg === '-p')
            args.port = argv[++i];
        else if (arg === '--ws-port')
            args['ws-port'] = argv[++i];
        else if (arg === '--backend' || arg === '-b')
            args.backend = argv[++i];
        else if (arg === '--ws')
            args.ws = argv[++i];
        else if (!arg.startsWith('-'))
            args._cmd = arg;
    }
    return args;
}
async function cmdStart(args) {
    const port = parseInt(String(args.port || '8080'), 10);
    const wsPort = parseInt(String(args['ws-port'] || '8081'), 10);
    const build = !!args.build;
    const root = findProjectRoot();
    console.log('╔══════════════════════════════════════╗');
    console.log('║   JWCode — Java AI Coding Tool       ║');
    console.log('╚══════════════════════════════════════╝');
    console.log('');
    // Build if requested or if jar doesn't exist
    if (build || !jarExists(root)) {
        buildBackend(root);
    }
    // Start Java backend
    const backendProc = startBackend(root, port, wsPort);
    // Cleanup on exit
    const cleanup = () => {
        console.log('\n[jwcode] Shutting down...');
        cleanupBackend(backendProc);
    };
    process.on('SIGINT', () => { cleanup(); process.exit(0); });
    process.on('SIGTERM', () => { cleanup(); process.exit(0); });
    process.on('exit', cleanup);
    // Wait for backend
    await waitForBackend(port);
    // Start TUI
    const backendUrl = `http://localhost:${port}`;
    const wsUrl = `ws://localhost:${wsPort}/ws`;
    const { unmount } = render(createElement(App, { backendUrl, wsUrl, onExit: () => { cleanup(); process.exit(0); } }));
    // Keep alive
    await new Promise((resolve) => {
        process.on('SIGINT', () => resolve());
        process.on('SIGTERM', () => resolve());
    });
}
async function cmdRun(args) {
    if (!process.stdin.isTTY) {
        console.error('Error: jwcode requires a real terminal (TTY).');
        console.error('Please run from a terminal emulator like Windows Terminal, CMD, or PowerShell.');
        process.exit(1);
    }
    const config = loadConfig();
    const backendUrl = String(args.backend || config.backend_url);
    const wsUrl = String(args.ws || config.ws_url);
    const { unmount, waitUntilExit } = render(createElement(App, {
        backendUrl,
        wsUrl,
        onExit: () => process.exit(0),
    }));
    await waitUntilExit();
}
async function main() {
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
    const cmd = args._cmd || 'start';
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
