/**
 * Java backend launcher.
 */
import { spawn, spawnSync, execSync, type ChildProcess } from 'node:child_process';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { homedir } from 'node:os';
import { fileURLToPath } from 'node:url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export function findProjectRoot(): string {
  let dir = join(__dirname, '..', '..');
  while (dir !== dirname(dir)) {
    if (existsSync(join(dir, 'pom.xml'))) return dir;
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
  const cmd = `"${mvn}" package -pl jwcode-web -am -q -DskipTests`;
  console.log(`[launcher] Building: ${cmd}`);
  try {
    const result = spawnSync(cmd, [], {
      cwd: projectRoot,
      stdio: 'pipe',
      shell: true,
      windowsHide: true,
    });
    if (result.status !== 0) {
      throw new Error(result.stderr.toString() || result.stdout.toString());
    }
  } catch (e: unknown) {
    console.error('[launcher] Build failed:', String(e));
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
      const out = execSync(`netstat -ano | findstr :${port} | findstr LISTENING`, { encoding: 'utf-8' });
      for (const line of out.trim().split('\n')) {
        const pid = line.trim().split(/\s+/).pop();
        if (pid) {
          try {
            execSync(`taskkill /F /PID ${pid}`, { stdio: 'ignore' });
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

  return new Promise((resolve) => {
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

export function startBackend(
  projectRoot: string,
  port: number,
  wsPort: number,
): ChildProcess {
  const mvn = findMvn();
  console.log(`[launcher] Starting backend: ${mvn} exec:java ...`);

  // Kill any stale process on the port first
  killPort(port);
  killPort(wsPort);

  const cmd = `"${mvn}" exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher "-Dexec.args=${port} ${wsPort}" -q`;
  const proc = spawn(cmd, [], {
    cwd: projectRoot,
    env: { ...process.env, JWCODE_WS_PORT: String(wsPort) },
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: true,
    windowsHide: true,
  });

  // Suppress all output — backend readiness is detected via HTTP health check.
  // stdout must be consumed to prevent the pipe from blocking the process.
  proc.stdout?.on('data', () => {});
  proc.stderr?.on('data', (data: Buffer) => {
    const msg = data.toString('utf-8').trim();
    if (!msg) return;
    if (msg.includes('Address already in use') || msg.includes('BindException')) {
      console.error(`[backend] ERROR: Port ${port} is in use.`);
    }
  });

  proc.on('exit', (code) => {
    if (code !== 0 && code !== null) {
      console.error(`[launcher] Backend process exited with code ${code}`);
    }
  });

  return proc;
}

export function cleanupBackend(proc: ChildProcess | null): void {
  if (!proc) return;
  console.log('\n[jwcode] Shutting down...');
  if (process.platform === 'win32') {
    // Windows: use taskkill /T to kill the entire process tree (cmd.exe → java)
    try {
      execSync(`taskkill /F /T /PID ${proc.pid}`, { stdio: 'ignore' });
    } catch {
      // Fallback: try direct termination
      try { proc.kill(); } catch {}
    }
  } else {
    try { process.kill(-proc.pid!, 'SIGTERM'); } catch {}
    proc.kill('SIGTERM');
    setTimeout(() => {
      if (proc && !proc.killed) {
        try { process.kill(-proc.pid!, 'SIGKILL'); } catch {}
        proc.kill('SIGKILL');
      }
    }, 5000);
  }
}
