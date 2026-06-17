import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { basename, dirname, extname, join, resolve } from 'node:path';
import { homedir } from 'node:os';
import { spawn } from 'node:child_process';
import { getStore, updateAppState } from '../hooks/useAppState.js';
import { createMessage, type Message } from '../protocol.js';
import { loadConfig } from '../config.js';

const IGNORE_DIRS = new Set([
  '.git', 'node_modules', 'dist', 'backend', 'proguard-7.4.2', 'tmp_extract',
  '.proguard_extract', 'coverage',
]);

const TEXT_EXTS = new Set([
  '.ts', '.tsx', '.js', '.jsx', '.json', '.md', '.txt', '.yaml', '.yml',
  '.css', '.html', '.java', '.kt', '.xml', '.mjs', '.cjs', '.sh', '.bat',
  '.ps1', '.toml', '.ini', '.env', '.gitignore',
]);

function pushAssistant(content: string): void {
  const msg = createMessage('assistant', content);
  updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg], statusText: '' }));
}

function pushStatus(statusText: string): void {
  updateAppState(prev => ({ ...prev, statusText }));
}

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

function conversationMarkdown(messages: Message[]): string {
  const lines = ['# JWCode conversation export', '', `Exported: ${new Date().toISOString()}`, ''];
  for (const msg of messages) {
    lines.push(`## ${msg.type}`, '');
    if (msg.thinking) {
      lines.push('### Thinking', '', msg.thinking, '');
    }
    if (msg.steps.length > 0) {
      lines.push('### Steps', '');
      for (const step of msg.steps) {
        lines.push(`- ${step.status}: ${step.title}`);
        if (step.result) lines.push(`  result: ${step.result}`);
      }
      lines.push('');
    }
    if (msg.toolCalls.length > 0) {
      lines.push('### Tool calls', '');
      for (const tool of msg.toolCalls) {
        lines.push(`- ${tool.status}: ${tool.name}`);
        if (tool.args) lines.push(`  args: ${tool.args}`);
        if (tool.result) lines.push(`  result: ${tool.result}`);
      }
      lines.push('');
    }
    lines.push(msg.content || '(empty)', '');
  }
  return lines.join('\n');
}

function listFilesRecursive(root: string, limit = 5000): string[] {
  const out: string[] = [];
  const walk = (dir: string) => {
    if (out.length >= limit) return;
    let entries: Array<{ name: string; isDirectory(): boolean; isFile(): boolean }>;
    try {
      entries = readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      if (out.length >= limit) return;
      const full = join(dir, entry.name);
      if (entry.isDirectory()) {
        if (!IGNORE_DIRS.has(entry.name)) walk(full);
      } else if (entry.isFile()) {
        out.push(full);
      }
    }
  };
  walk(root);
  return out;
}

function isProbablyText(file: string): boolean {
  const name = basename(file);
  const ext = extname(file).toLowerCase();
  return TEXT_EXTS.has(ext) || TEXT_EXTS.has(name);
}

function runPackageScript(script: 'test' | 'lint', extraArgs: string): Promise<void> {
  const pkgPath = resolve(process.cwd(), 'package.json');
  if (!existsSync(pkgPath)) {
    pushAssistant(`No package.json found in ${process.cwd()}.`);
    return Promise.resolve();
  }

  const pkg = JSON.parse(readFileSync(pkgPath, 'utf-8')) as { scripts?: Record<string, string> };
  if (!pkg.scripts?.[script]) {
    pushAssistant(`No npm script named "${script}" is defined in package.json.`);
    return Promise.resolve();
  }

  pushStatus(`Running npm ${script}...`);
  const args = ['run', script];
  if (extraArgs.trim()) args.push('--', ...extraArgs.trim().split(/\s+/));

  return new Promise(resolveDone => {
    const child = spawn(process.platform === 'win32' ? 'npm.cmd' : 'npm', args, {
      cwd: process.cwd(),
      shell: false,
      windowsHide: true,
    });
    let output = '';
    const append = (buf: Buffer) => {
      output += buf.toString();
      if (output.length > 12000) output = output.slice(-12000);
    };
    child.stdout.on('data', append);
    child.stderr.on('data', append);
    child.on('error', err => {
      pushAssistant(`Failed to run npm ${script}: ${err.message}`);
      resolveDone();
    });
    child.on('close', code => {
      const body = output.trim() || '(no output)';
      pushAssistant(`npm ${script} exited with code ${code ?? 'unknown'}\n\n\`\`\`\n${body}\n\`\`\``);
      resolveDone();
    });
  });
}

function readYamlFile(file: string): Record<string, string> {
  const result: Record<string, string> = {};
  if (!existsSync(file)) return result;
  for (const line of readFileSync(file, 'utf-8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf(':');
    if (idx < 0) continue;
    result[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim().replace(/^["']|["']$/g, '');
  }
  return result;
}

function writeYamlFile(file: string, values: Record<string, string>): void {
  mkdirSync(dirname(file), { recursive: true });
  const content = Object.entries(values)
    .map(([key, value]) => `${key}: ${JSON.stringify(value)}`)
    .join('\n') + '\n';
  writeFileSync(file, content, 'utf-8');
}

function listNamedFiles(dir: string): string[] {
  if (!existsSync(dir)) return [];
  try {
    return readdirSync(dir, { withFileTypes: true })
      .filter(e => e.isDirectory() || e.isFile())
      .map(e => e.name)
      .sort();
  } catch {
    return [];
  }
}

export async function runLocalCommand(action: string, arg: string): Promise<boolean> {
  const state = getStore().getState();
  const cwd = process.cwd();

  switch (action) {
    case 'tokens': {
      const u = state.usage;
      pushAssistant([
        'Token usage',
        `- Prompt: ${formatTokens(u.promptTokens)}`,
        `- Completion: ${formatTokens(u.completionTokens)}`,
        `- Total: ${formatTokens(u.totalTokens)}`,
        `- Context: ${Math.round(u.usageRatio * 100)}%`,
      ].join('\n'));
      return true;
    }
    case 'export': {
      const target = resolve(cwd, arg);
      mkdirSync(dirname(target), { recursive: true });
      writeFileSync(target, conversationMarkdown(state.messages), 'utf-8');
      pushAssistant(`Conversation exported to ${target}.`);
      return true;
    }
    case 'checkpoint': {
      const dir = join(cwd, '.jwcode', 'checkpoints');
      const parts = arg.trim().split(/\s+/).filter(Boolean);
      if (parts[0] === 'list') {
        const files = listNamedFiles(dir);
        pushAssistant(files.length ? `Local checkpoints:\n${files.map(f => `- ${f}`).join('\n')}` : 'No local checkpoints found.');
        return true;
      }
      if (parts[0] === 'restore') {
        const name = parts.slice(1).join(' ');
        if (!name) {
          pushAssistant('Usage: /checkpoint restore <file>');
          return true;
        }
        const file = resolve(dir, name);
        if (!file.startsWith(resolve(dir)) || !existsSync(file)) {
          pushAssistant(`Checkpoint not found: ${name}`);
          return true;
        }
        pushAssistant(readFileSync(file, 'utf-8'));
        return true;
      }
      mkdirSync(dir, { recursive: true });
      const suffix = parts.join('-').replace(/[^\w.-]+/g, '-') || 'manual';
      const file = join(dir, `${new Date().toISOString().replace(/[:.]/g, '-')}-${suffix}.md`);
      writeFileSync(file, conversationMarkdown(state.messages), 'utf-8');
      pushAssistant(`Local checkpoint saved to ${file}.`);
      return true;
    }
    case 'memory': {
      const memoryRoots = [join(cwd, 'JWCODE.md'), join(cwd, '.jwcode')];
      if (arg.trim()) {
        const target = resolve(cwd, arg.trim());
        if (!target.startsWith(cwd) || !existsSync(target) || statSync(target).isDirectory()) {
          pushAssistant(`Memory file not found: ${arg.trim()}`);
          return true;
        }
        pushAssistant(readFileSync(target, 'utf-8').slice(0, 8000));
        return true;
      }
      const lines = ['Project memory'];
      for (const root of memoryRoots) {
        if (!existsSync(root)) continue;
        if (statSync(root).isFile()) lines.push(`- ${root}`);
        else for (const file of listFilesRecursive(root, 100).slice(0, 40)) lines.push(`- ${file}`);
      }
      pushAssistant(lines.length > 1 ? lines.join('\n') : 'No project memory files found.');
      return true;
    }
    case 'project': {
      const pkgPath = join(cwd, 'package.json');
      const files = listFilesRecursive(cwd, 10000);
      const pkg = existsSync(pkgPath) ? JSON.parse(readFileSync(pkgPath, 'utf-8')) as Record<string, unknown> : {};
      const topDirs = listNamedFiles(cwd).slice(0, 30).join(', ');
      pushAssistant([
        'Project summary',
        `- Directory: ${cwd}`,
        `- Package: ${String(pkg.name || '(none)')} ${String(pkg.version || '')}`.trim(),
        `- Files scanned: ${files.length}`,
        `- Top-level entries: ${topDirs || '(none)'}`,
      ].join('\n'));
      return true;
    }
    case 'search': {
      const query = arg.trim();
      const hits: string[] = [];
      for (const file of listFilesRecursive(cwd, 8000)) {
        if (hits.length >= 80 || !isProbablyText(file)) continue;
        try {
          const st = statSync(file);
          if (st.size > 1_000_000) continue;
          const lines = readFileSync(file, 'utf-8').split(/\r?\n/);
          lines.forEach((line, idx) => {
            if (hits.length < 80 && line.toLowerCase().includes(query.toLowerCase())) {
              hits.push(`${file.slice(cwd.length + 1)}:${idx + 1}: ${line.trim().slice(0, 160)}`);
            }
          });
        } catch {}
      }
      pushAssistant(hits.length ? `Search results for "${query}":\n${hits.join('\n')}` : `No results for "${query}".`);
      return true;
    }
    case 'test':
      await runPackageScript('test', arg);
      return true;
    case 'lint':
      await runPackageScript('lint', arg);
      return true;
    case 'config': {
      const file = join(homedir(), '.jwcode', 'config.yaml');
      const parts = arg.trim().split(/\s+/).filter(Boolean);
      const values = { ...loadConfig() } as unknown as Record<string, string>;
      Object.assign(values, readYamlFile(file));
      if (parts[0] === 'set' && parts[1] && parts.length >= 3) {
        values[parts[1]] = parts.slice(2).join(' ');
        writeYamlFile(file, values);
        pushAssistant(`Config updated: ${parts[1]}`);
        return true;
      }
      if (parts[0] === 'get' && parts[1]) {
        pushAssistant(`${parts[1]}: ${values[parts[1]] ?? '(not set)'}`);
        return true;
      }
      pushAssistant(Object.entries(values).map(([k, v]) => `${k}: ${v}`).join('\n'));
      return true;
    }
    case 'skills': {
      const roots = [join(homedir(), '.codex', 'skills'), join(cwd, '.jwcode', 'skills')];
      const lines = roots.flatMap(root => listNamedFiles(root).map(name => `- ${name} (${root})`));
      pushAssistant(lines.length ? `Available local skills:\n${lines.join('\n')}` : 'No local skills found.');
      return true;
    }
    case 'agents': {
      const roots = [join(cwd, '.jwcode', 'subagents'), join(homedir(), '.codex', 'agents')];
      const lines = roots.flatMap(root => listNamedFiles(root).map(name => `- ${name} (${root})`));
      pushAssistant(lines.length ? `Configured agents:\n${lines.join('\n')}` : 'No local agents found.');
      return true;
    }
    case 'plugin': {
      const roots = [join(homedir(), '.codex', 'plugins'), join(cwd, '.codex-plugin')];
      const lines = roots.flatMap(root => listNamedFiles(root).map(name => `- ${name} (${root})`));
      pushAssistant(lines.length ? `Local plugins:\n${lines.join('\n')}` : 'No local plugins found.');
      return true;
    }
    default:
      return false;
  }
}
