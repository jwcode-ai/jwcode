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
import { spawn, spawnSync, execSync, execFileSync, type ChildProcess } from 'node:child_process';
import { existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname, delimiter } from 'node:path';
import { createRequire } from 'node:module';
import { homedir, platform } from 'node:os';
import { fileURLToPath } from 'node:url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

/**
 * Find the installation directory where jwcode's backend JAR lives.
 *
 * Dev repo clone: walk up looking for pom.xml (monorepo root).
 * npm global install: dist/cli.js → ../package.json (install root).
 * Final fallback is process.cwd().
 */
export function findInstallDir(): string {
  // Development: walk up from script looking for pom.xml (monorepo root)
  let dir = join(__dirname, '..', '..');
  while (dir !== dirname(dir)) {
    if (existsSync(join(dir, 'pom.xml'))) return dir;
    dir = dirname(dir);
  }

  // npm global install: package.json in parent dir
  const parentDir = join(__dirname, '..');
  if (existsSync(join(parentDir, 'package.json'))) {
    return parentDir;
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
  if (existsSync(bundledJar)) {
    if (!isProguardOutput(bundledJar)) return bundledJar;
    // Bundled JAR looks like a broken ProGuard output — fall through to dev
    // location, but only if dev JAR is newer so we don't regress.
  }

  // Development: Maven-built JAR in target/
  // Prefer the Spring Boot repackaged uber-JAR (jwcode-web.jar), then
  // fall back to the plain SNAPSHOT jar.
  const targetDir = join(installDir, 'jwcode-web', 'target');
  const devJarUnversioned = join(targetDir, 'jwcode-web.jar');
  const devJarSnap = join(targetDir, 'jwcode-web-1.0.0-SNAPSHOT.jar');

  if (existsSync(devJarUnversioned) && !isProguardOutput(devJarUnversioned)) return devJarUnversioned;
  if (existsSync(devJarSnap)) return devJarSnap;

  // Also check for any other matching JAR in target/
  if (existsSync(targetDir)) {
    try {
      const jars = readdirSync(targetDir)
        .filter(f => f.startsWith('jwcode-web') && f.endsWith('.jar'))
        .map(f => ({ name: f, mtime: statSync(join(targetDir, f)).mtimeMs }))
        .sort((a, b) => b.mtime - a.mtime);
      for (const j of jars) {
        const p = join(targetDir, j.name);
        if (!isProguardOutput(p)) return p;
      }
    } catch { /* ignore */ }
  }

  return null;
}

/**
 * Detect whether a JAR looks like a broken ProGuard output:
 * has META-INF/proguard/ but no com/jwcode/ classes.
 */
function isProguardOutput(jarPath: string): boolean {
  try {
    const out = execFileSync('unzip', ['-l', jarPath, 'META-INF/proguard/', 'com/jwcode/'], { stdio: ['ignore', 'pipe', 'ignore'] }).toString();
    return out.includes('META-INF/proguard/') && !out.includes('com/jwcode/');
  } catch {
    return false;
  }
}

export function findMvn(): string {
  const pathSep = delimiter;
  const paths = process.env.PATH?.split(pathSep) || [];
  for (const dir of paths) {
    for (const name of ['mvn.cmd', 'mvn.bat', 'mvn']) {
      const full = join(dir, name);
      if (existsSync(full)) return full;
    }
  }
  // Search common install locations across platforms
  const mvnRoots = platform() === 'win32'
    ? ['C:\\Program Files', homedir()]
    : ['/usr/local', '/opt', homedir()];
  for (const root of mvnRoots) {
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        if (entry.isDirectory() && entry.name.startsWith('apache-maven')) {
          const mvn = join(root, entry.name, 'bin', platform() === 'win32' ? 'mvn.cmd' : 'mvn');
          if (existsSync(mvn)) return mvn;
        }
      }
    } catch {}
  }
  return 'mvn';
}

export function findJava(): string {
  const pathSep = delimiter;
  const paths = process.env.PATH?.split(pathSep) || [];
  for (const dir of paths) {
    for (const name of ['java.exe', 'java']) {
      const full = join(dir, name);
      if (existsSync(full)) return full;
    }
  }
  // Common install locations across platforms
  const javaRoots = platform() === 'win32'
    ? ['C:\\Program Files\\Java', 'C:\\Program Files (x86)\\Java', homedir()]
    : ['/usr/lib/jvm', '/usr/local', '/opt', homedir()];
  const javaExe = platform() === 'win32' ? 'java.exe' : 'java';
  for (const root of javaRoots) {
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        if (entry.isDirectory() && (entry.name.startsWith('jdk') || entry.name.startsWith('openjdk') || entry.name.startsWith('java-'))) {
          const java = join(root, entry.name, 'bin', javaExe);
          if (existsSync(java)) return java;
        }
      }
    } catch {}
  }
  return 'java';
}

/**
 * Ensure the backend JAR exists by downloading from GitHub Releases if needed.
 * Returns the JAR path, or null if download fails.
 */
export async function ensureBackendJar(installDir: string): Promise<string | null> {
  const existing = findJar(installDir);
  if (existing) return existing;

  const backendDir = join(installDir, 'backend');
  const jarPath = join(backendDir, 'jwcode-web.jar');

  // Read package version for the release tag
  let version: string;
  try {
    const req = createRequire(import.meta.url);
    const pkg = req('../package.json');
    version = pkg.version;
  } catch {
    console.error('[launcher] Could not read package.json to determine version.');
    return null;
  }

  const urls = [
    `https://ghproxy.com/https://github.com/ngwlh/jwcode/releases/download/v${version}/jwcode-web.jar`,
    `https://github.com/ngwlh/jwcode/releases/download/v${version}/jwcode-web.jar`,
  ];

  console.log(`[launcher] Backend JAR not found. Downloading from GitHub Releases...`);

  mkdirSync(backendDir, { recursive: true });

  for (const url of urls) {
    console.log(`[launcher] Trying: ${url}`);
    try {
      const response = await fetch(url);
      if (!response.ok) {
        console.log(`[launcher] HTTP ${response.status}, trying next source...`);
        continue;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        console.log('[launcher] No response body, trying next source...');
        continue;
      }

      const chunks: Uint8Array[] = [];
      let downloaded = 0;
      const total = Number(response.headers.get('content-length') || 0);

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        downloaded += value.length;
        if (total > 0) {
          const pct = Math.round((downloaded / total) * 100);
          process.stdout.write(`\r[launcher] Downloading... ${pct}% (${(downloaded / 1024 / 1024).toFixed(1)} MB)`);
        }
      }

      const buf = Buffer.concat(chunks);
      writeFileSync(jarPath, buf);
      console.log(`\n[launcher] Download complete: ${jarPath} (${(buf.length / 1024 / 1024).toFixed(1)} MB)`);
      return jarPath;
    } catch (err) {
      console.log(`[launcher] Download failed: ${err instanceof Error ? err.message : String(err)}`);
      console.log('[launcher] Trying next source...');
    }
  }

  console.error('[launcher] All download sources failed.');
  return null;
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
    if (platform() === 'win32') {
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
    if (platform() === 'win32') {
      const out = execSync(`netstat -ano | findstr /C:":${port} " | findstr LISTENING`, { encoding: 'utf-8' });
      for (const line of out.trim().split(/\r?\n/)) {
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
  const java = findJava();
  const jarPath = findJar(installDir);
  if (!jarPath) {
    console.error('[launcher] Backend JAR not found.');
    console.error('[launcher]   Reinstall: npm install -g @jwcode/cli');
    console.error('[launcher]   Or build from source: cd jwcode && mvn package -pl jwcode-web -am -DskipTests');
    process.exit(1);
  }

  // Verify Java is available before spawning
  try {
    execSync(`"${java}" -version 2>&1`, { stdio: 'ignore' });
  } catch {
    console.error('[launcher] Java is required but not found on this system.');
    console.error('[launcher]   Install Java 17+: https://adoptium.net/');
    console.error('[launcher]   Or set JAVA_HOME and ensure java is on your PATH.');
    process.exit(1);
  }

  if (forceKill) {
    killPort(port);
    killPort(wsPort);
  }

  console.log(`[launcher] Starting backend: ${java} -jar ${jarPath}`);
  console.log(`[launcher] Workspace: ${workspaceDir}`);

  const args = [
    '-Dfile.encoding=UTF-8',
    '-Dsun.stdout.encoding=UTF-8',
    '-Dsun.stderr.encoding=UTF-8',
    '-jar',
    jarPath,
    String(port),
    String(wsPort),
    workspaceDir,
  ];
  const proc = spawn(java, args, {
    cwd: workspaceDir,
    env: {
      ...process.env,
      JWCODE_WS_PORT: String(wsPort),
      LANG: process.env.LANG || 'zh_CN.UTF-8',
      LC_ALL: process.env.LC_ALL || 'zh_CN.UTF-8',
    },
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
  const pid = proc.pid;
  if (!pid) { proc.kill(); return; }
  console.log('\n[jwcode] Shutting down...');
  try {
    if (platform() === 'win32') {
      execSync(`taskkill /F /T /PID ${pid}`, { stdio: 'ignore' });
    } else {
      // Kill the entire process group, then force kill synchronously.
      // No setTimeout: it would never fire if process.exit() is called right after.
      try { process.kill(-pid, 'SIGTERM'); } catch {}
      try { proc.kill('SIGTERM'); } catch {}
      try { process.kill(-pid, 'SIGKILL'); } catch {}
      try { proc.kill('SIGKILL'); } catch {}
    }
  } catch {
    try { proc.kill(); } catch {}
  }
}
