/**
 * Java backend launcher.
 */
import { spawn, exec, execSync, ChildProcess } from 'node:child_process';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { homedir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createInterface } from 'node:readline';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export function findProjectRoot(): string {
  let dir = join(__dirname, '..', '..');
  while (dir !== dirname(dir)) {
    if (existsSync(join(dir, 'jwcode-parent', 'pom.xml'))) return dir;
    dir = dirname(dir);
  }
  return process.cwd();
}

export function findMvn(): string {
  const paths = process.env.PATH?.split(';') || [];
  for (const dir of paths) {
    for (const name of ['mvn.cmd', 'mvn.bat', 'mvn']) {
      const full = join(dir, name);
      if (existsSync(full)) return full;
    }
  }
  for (const root of ['C:\\Program Files', homedir()]) {
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        if (entry.isDirectory() && entry.name.startsWith('apache-maven')) {
          const mvn = join(root, entry.name, 'bin', 'mvn.cmd');
          if (existsSync(mvn)) return mvn;
        }
      }
    } catch {}
  }
  return 'mvn';
}

export function jarExists(projectRoot: string): string | null {
  const targetDir = join(projectRoot, 'jwcode-web', 'target');
  if (!existsSync(targetDir)) return null;
  try {
    const jars = readdirSync(targetDir)
      .filter(f => f.startsWith('jwcode-web-') && f.endsWith('.jar'))
      .map(f => ({ name: f, mtime: statSync(join(targetDir, f)).mtimeMs }))
      .sort((a, b) => b.mtime - a.mtime);
    return jars.length > 0 ? join(targetDir, jars[0].name) : null;
  } catch { return null; }
}

export function buildBackend(projectRoot: string): void {
  const mvn = findMvn();
  // Use quoted path for spaces in "Program Files"
  const cmd = `"${mvn}" package -pl jwcode-web -am -q -DskipTests`;
  console.log(`[launcher] Building: ${cmd}`);
  try {
    execSync(cmd, { cwd: projectRoot, stdio: 'pipe' });
  } catch (e: unknown) {
    const err = e as { stderr?: Buffer; stdout?: Buffer };
    console.error('[launcher] Build failed:');
    console.error(err.stderr?.toString() || err.stdout?.toString() || String(e));
    process.exit(1);
  }
  if (!jarExists(projectRoot)) {
    console.error('[launcher] Build succeeded but jar not found');
    process.exit(1);
  }
}

export function killPort(port: number): void {
  try {
    if (process.platform === 'win32') {
      execSync(`netstat -ano | findstr :${port} | findstr LISTENING`, { stdio: 'pipe' });
      // Find and kill the process
      const out = execSync(`netstat -ano | findstr :${port} | findstr LISTENING`, { encoding: 'utf-8' });
      for (const line of out.trim().split('\n')) {
        const pid = line.trim().split(/\s+/).pop();
        if (pid) {
          try {
            process.kill(parseInt(pid), 'SIGTERM');
            console.log(`[launcher] Killed process on port ${port} (PID ${pid})`);
          } catch {}
        }
      }
    } else {
      execSync(`lsof -ti:${port} | xargs kill -9 2>/dev/null`, { stdio: 'ignore' });
    }
  } catch {
    // Nothing listening on that port — good
  }
}

export function waitForBackend(port: number, timeout = 60): Promise<void> {
  const start = Date.now();
  const url = `http://localhost:${port}/api/system/status`;

  return new Promise((resolve, reject) => {
    function check() {
      if (Date.now() - start > timeout * 1000) {
        console.log(`[launcher] WARNING: Backend not responding after ${timeout}s`);
        resolve(); // Don't reject, let the TUI show the error
        return;
      }
      fetch(url, { signal: AbortSignal.timeout(2000) })
        .then(async r => {
          const text = await r.text();
          // Backend returns {"status":"running",...}
          if (r.status === 200 && (text.includes('running') || text.includes('status'))) {
            console.log(`[launcher] Backend ready on port ${port}`);
            resolve();
          } else {
            setTimeout(check, 1000);
          }
        })
        .catch(() => setTimeout(check, 1000));
    }
    check();
  });
}

function toUtf8(data: Buffer): string {
  // Windows CMD uses GBK/CP936; try UTF-8 first, fall back to latin1
  try {
    const s = data.toString('utf-8');
    if (s.includes('�')) throw new Error('replacement chars');
    return s;
  } catch {
    return data.toString('latin1'); // latin1 preserves all bytes
  }
}

export function startBackend(
  projectRoot: string,
  port: number,
  wsPort: number,
): ChildProcess {
  const mvn = findMvn();
  const cmd = `"${mvn}" exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher "-Dexec.args=${port} ${wsPort}" -q`;
  console.log(`[launcher] Starting backend: ${mvn} exec:java ...`);

  // Kill any stale process on the port first
  killPort(port);
  killPort(wsPort);

  const proc = exec(cmd, {
    cwd: projectRoot,
    env: { ...process.env, JWCODE_WS_PORT: String(wsPort) },
    encoding: 'buffer', // Get raw Buffer for manual decoding
  });

  let started = false;
  let bindError = false;

  proc.stderr?.on('data', (data: Buffer) => {
    const msg = toUtf8(data).trim();
    if (!msg) return;
    // Only show port conflict errors — suppress everything else
    if (msg.includes('Address already in use') || msg.includes('BindException')) {
      bindError = true;
      console.error(`[backend] ERROR: Port ${port} is in use.`);
    }
  });

  // Suppress all stdout — backend readiness is detected via HTTP health check
  proc.stdout?.on('data', () => {});

  // Detect startup failure
  proc.on('exit', (code) => {
    if (!started && code !== 0 && code !== null) {
      console.error(`[launcher] Backend process exited with code ${code}`);
      if (bindError) {
        console.error('[launcher] Port conflict detected. Trying to kill stale process and retry...');
        killPort(port);
        killPort(wsPort);
      }
    }
  });

  return proc;
}

export function cleanupBackend(proc: ChildProcess | null): void {
  if (!proc) return;
  console.log('\n[jwcode] Shutting down...');
  try { process.kill(-proc.pid!, 'SIGTERM'); } catch {}
  proc.kill('SIGTERM');
  setTimeout(() => {
    if (proc && !proc.killed) {
      try { process.kill(-proc.pid!, 'SIGKILL'); } catch {}
      proc.kill('SIGKILL');
    }
  }, 5000);
}
