/**
 * Java backend launcher.
 *
 * Production mode (npm global install):
 *   Uses bundled fat JAR at <installDir>/backend/jwcode-web.jar,
 *   launched via `java -jar`.
 *
 * Development mode (repo clone):
 *   Walks up from script dir to find pom.xml, builds with Maven,
 *   launches via `java -jar` from target/.
 */
import { spawn, spawnSync, execSync, type ChildProcess } from 'node:child_process';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { homedir } from 'node:os';
import { fileURLToPath } from 'node:url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

/**
 * Find the installation directory where jwcode's backend JAR lives.
 *
 * Looks for backend/jwcode-web.jar relative to the script (npm global install).
 * Falls back to the dev repo root (contains pom.xml).
 * Final fallback is process.cwd().
 */
export function findInstallDir(): string {
  // Production: look for bundled JAR relative to dist/cli.js
  // dist/cli.js → ../backend/jwcode-web.jar
  const bundledJar = join(__dirname, '..', 'backend', 'jwcode-web.jar');
  if (existsSync(bundledJar)) {
    return join(__dirname, '..');
  }

  // Development: walk up from script looking for pom.xml (monorepo root)
  let dir = join(__dirname, '..', '..');
  while (dir !== dirname(dir)) {
    if (existsSync(join(dir, 'pom.xml'))) return dir;
    dir = dirname(dir);
  }

  return process.cwd();
}

/**
 * Find the backend JAR path.
 * Returns the fat JAR if it exists (production or pre-built dev),
 * otherwise null (caller should build first).
 */
export function findJar(installDir: string): string | null {
  // Production: bundled JAR
  const bundledJar = join(installDir, 'backend', 'jwcode-web.jar');
  if (existsSync(bundledJar)) return bundledJar;

  // Development: Maven-built JAR in target/
  const devJar = join(installDir, 'jwcode-web', 'target', 'jwcode-web.jar');
  if (existsSync(devJar)) return devJar;

  // Also check for classifier variant from maven-assembly-plugin
  const targetDir = join(installDir, 'jwcode-web', 'target');
  if (existsSync(targetDir)) {
    try {
      const jars = readdirSync(targetDir)
        .filter(f => f.startsWith('jwcode-web') && f.endsWith('.jar'))
        .map(f => ({ name: f, mtime: statSync(join(targetDir, f)).mtimeMs }))
        .sort((a, b) => b.mtime - a.mtime);
      if (jars.length > 0) return join(targetDir, jars[0].name);
    } catch { /* ignore */ }
  }

  return null;
}

export function findMvn(): string {
  const paths = process.env.PATH?.split(path.delimiter) || [];
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

export function findJava(installDir?: string): string {
  // 1. Bundled JRE (npm global install)
  if (installDir) {
    const bundled = join(installDir, 'backend', 'jre', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    if (existsSync(bundled)) return bundled;
  }

  // 2. PATH
  const paths = process.env.PATH?.split(';') || [];
  for (const dir of paths) {
    for (const name of ['java.exe', 'java']) {
      const full = join(dir, name);
      if (existsSync(full)) return full;
    }
  }

  // 3. Common install locations (Windows)
  for (const root of ['C:\\Program Files\\Java', 'C:\\Program Files (x86)\\Java', homedir()]) {
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        if (entry.isDirectory() && (entry.name.startsWith('jdk') || entry.name.startsWith('openjdk'))) {
          const java = join(root, entry.name, 'bin', 'java.exe');
          if (existsSync(java)) return java;
        }
      }
    } catch {}
  }

  return 'java';
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
  if (!findJar(projectRoot)) {
    console.error('[launcher] Build succeeded but jar not found');
    process.exit(1);
  }
}

/**
 * Check whether a port is currently in use (listening).
 */
export function portInUse(port: number): boolean {
  try {
    if (process.platform === 'win32') {
      const out = execSync(`netstat -ano | findstr :${port} | findstr LISTENING`, { encoding: 'utf-8' });
      return out.trim().length > 0;
    } else {
      execSync(`lsof -ti:${port}`, { stdio: 'ignore' });
      return true;
    }
  } catch {
    return false;
  }
}

/**
 * Find a pair of consecutive free ports starting from startPort.
 * Returns [httpPort, wsPort].
 */
export function findAvailablePorts(startPort = 8080, maxAttempts = 20): { httpPort: number; wsPort: number } {
  for (let port = startPort; port < startPort + maxAttempts; port++) {
    if (!portInUse(port) && !portInUse(port + 1)) {
      return { httpPort: port, wsPort: port + 1 };
    }
    // Skip the pair if either port is in use
    if (portInUse(port + 1)) port++;
  }
  throw new Error(`No available port pair found in range ${startPort}-${startPort + maxAttempts}`);
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
        resolve();
        return;
      }
      fetch(url, { signal: AbortSignal.timeout(2000) })
        .then(async r => {
          const text = await r.text();
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

export interface StartOptions {
  installDir: string;
  workspaceDir: string;
  port: number;
  wsPort: number;
  /** Kill existing processes on the ports before starting (default: false). */
  forceKill?: boolean;
}

export function startBackend(opts: StartOptions): ChildProcess {
  const { installDir, workspaceDir, port, wsPort, forceKill } = opts;
  const java = findJava(installDir);
  const jarPath = findJar(installDir);
  if (!jarPath) {
    console.error('[launcher] Backend JAR not found. Run with --build first, or ensure backend/jwcode-web.jar exists.');
    process.exit(1);
  }

  if (forceKill) {
    killPort(port);
    killPort(wsPort);
  }

  console.log(`[launcher] Starting backend: ${java} -jar ${jarPath}`);
  console.log(`[launcher] Workspace: ${workspaceDir}`);

  const args = ['-jar', jarPath, String(port), String(wsPort), workspaceDir];
  const proc = spawn(java, args, {
    cwd: workspaceDir,
    env: { ...process.env, JWCODE_WS_PORT: String(wsPort) },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });

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
    // Graceful shutdown first, then force kill if needed
    try {
      execSync(`taskkill /T /PID ${proc.pid}`, { stdio: 'ignore', timeout: 3000 });
    } catch {
      try {
        execSync(`taskkill /F /T /PID ${proc.pid}`, { stdio: 'ignore', timeout: 3000 });
      } catch {
        try { proc.kill(); } catch {}
      }
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

// --- Daemon mode ---
import { mkdirSync, writeFileSync, readFileSync, unlinkSync } from 'node:fs';

const DAEMON_DIR = join(homedir(), '.jwcode');
const DAEMON_FILE = join(DAEMON_DIR, 'daemon.json');

interface DaemonInfo {
  pid: number;
  httpPort: number;
  wsPort: number;
  workspaceDir: string;
  startedAt: string;
  lastActivity: string;
}

export function writeDaemonInfo(pid: number, httpPort: number, wsPort: number, workspaceDir: string): void {
  try { mkdirSync(DAEMON_DIR, { recursive: true }); } catch {}
  const info: DaemonInfo = {
    pid, httpPort, wsPort, workspaceDir,
    startedAt: new Date().toISOString(),
    lastActivity: new Date().toISOString(),
  };
  writeFileSync(DAEMON_FILE, JSON.stringify(info, null, 2), 'utf-8');
}

export function readDaemonInfo(): DaemonInfo | null {
  try {
    if (!existsSync(DAEMON_FILE)) return null;
    return JSON.parse(readFileSync(DAEMON_FILE, 'utf-8')) as DaemonInfo;
  } catch { return null; }
}

export function clearDaemonInfo(): void {
  try { unlinkSync(DAEMON_FILE); } catch {}
}

export function isDaemonAlive(info: DaemonInfo): boolean {
  try {
    if (process.platform === 'win32') {
      const out = execSync(`tasklist /FI "PID eq ${info.pid}" /NH`, { encoding: 'utf-8', timeout: 3000 });
      return out.includes(String(info.pid));
    } else {
      execSync(`kill -0 ${info.pid}`, { stdio: 'ignore' });
      return true;
    }
  } catch {
    return false;
  }
}

export interface DaemonOptions extends StartOptions {
  idleTimeout?: number;
}

export function startDaemon(opts: DaemonOptions): ChildProcess {
  const { installDir, workspaceDir, port, wsPort, forceKill } = opts;
  const java = findJava(installDir);
  const jarPath = findJar(installDir);
  if (!jarPath) {
    console.error('[launcher] Backend JAR not found. Run: npm install -g @jwcode/cli');
    process.exit(1);
  }

  if (forceKill) {
    killPort(port);
    killPort(wsPort);
  }

  const javaArgs = ['-jar', jarPath, String(port), String(wsPort), workspaceDir];
  const idleSec = opts.idleTimeout ?? 300;

  const proc = spawn(java, javaArgs, {
    cwd: workspaceDir,
    env: {
      ...process.env,
      JWCODE_WS_PORT: String(wsPort),
      JWCODE_DAEMON_IDLE_TIMEOUT: String(idleSec),
    },
    stdio: 'ignore',
    detached: true,
    windowsHide: true,
  });
  proc.unref();

  writeDaemonInfo(proc.pid!, port, wsPort, workspaceDir);
  console.log(`[daemon] Started JWCode daemon (PID ${proc.pid}) on ports ${port}/${wsPort}`);
  return proc;
}

export function findRunningDaemon(workspaceDir?: string): DaemonInfo | null {
  const info = readDaemonInfo();
  if (!info) return null;
  if (!isDaemonAlive(info)) {
    clearDaemonInfo();
    return null;
  }
  if (workspaceDir && info.workspaceDir !== workspaceDir) {
    return null;
  }
  return info;
}
