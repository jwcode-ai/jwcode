/**
 * Java backend launcher — matches python-cli/jwcode/launcher.py behavior.
 * Finds Maven, builds (optional), and starts the Java WebServer as a child process.
 */
import { spawn, execSync } from 'node:child_process';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { homedir } from 'node:os';
import { fileURLToPath } from 'node:url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
export function findProjectRoot() {
    // Start from ts-cli/src, go up to find jwcode-parent/pom.xml
    let dir = join(__dirname, '..', '..');
    while (dir !== dirname(dir)) {
        if (existsSync(join(dir, 'jwcode-parent', 'pom.xml')))
            return dir;
        dir = dirname(dir);
    }
    return process.cwd();
}
export function findMvn() {
    // Check PATH
    const paths = process.env.PATH?.split(';') || [];
    for (const dir of paths) {
        for (const name of ['mvn.cmd', 'mvn.bat', 'mvn']) {
            const full = join(dir, name);
            if (existsSync(full))
                return full;
        }
    }
    // Scan Program Files
    const scanRoots = ['C:\\Program Files', homedir()];
    for (const root of scanRoots) {
        try {
            const entries = readdirSync(root, { withFileTypes: true });
            for (const entry of entries) {
                if (entry.isDirectory() && entry.name.startsWith('apache-maven')) {
                    const mvn = join(root, entry.name, 'bin', 'mvn.cmd');
                    if (existsSync(mvn))
                        return mvn;
                }
            }
        }
        catch { /* skip inaccessible dirs */ }
    }
    return 'mvn';
}
export function jarExists(projectRoot) {
    const targetDir = join(projectRoot, 'jwcode-web', 'target');
    if (!existsSync(targetDir))
        return null;
    try {
        const jars = readdirSync(targetDir)
            .filter(f => f.startsWith('jwcode-web-') && f.endsWith('.jar'))
            .map(f => ({ name: f, mtime: statSync(join(targetDir, f)).mtimeMs }))
            .sort((a, b) => b.mtime - a.mtime);
        return jars.length > 0 ? join(targetDir, jars[0].name) : null;
    }
    catch {
        return null;
    }
}
export function buildBackend(projectRoot) {
    const mvn = findMvn();
    console.log(`[launcher] Building Java backend (${mvn} package -pl jwcode-web -am -q -DskipTests)...`);
    try {
        const cmd = `"${mvn}" package -pl jwcode-web -am -q -DskipTests`;
        execSync(`cmd.exe /d /s /c ${cmd}`, { cwd: projectRoot, stdio: 'pipe' });
    }
    catch (e) {
        const err = e;
        console.error('[launcher] Build failed:');
        console.error(err.stderr?.toString() || err.stdout?.toString() || String(e));
        process.exit(1);
    }
    if (!jarExists(projectRoot)) {
        console.error('[launcher] Build succeeded but jar not found');
        process.exit(1);
    }
}
export function waitForBackend(port, timeout = 30) {
    const start = Date.now();
    const urls = [
        `http://localhost:${port}/api/system/status`,
        `http://localhost:${port}/`,
    ];
    return new Promise((resolve) => {
        function check() {
            if (Date.now() - start > timeout * 1000) {
                console.log(`[launcher] WARNING: Backend not responding after ${timeout}s, continuing...`);
                resolve();
                return;
            }
            const url = urls[0];
            fetch(url, { signal: AbortSignal.timeout(1000) })
                .then(() => {
                console.log(`[launcher] Backend ready on port ${port}`);
                resolve();
            })
                .catch(() => {
                // Try alternate URL
                fetch(urls[1], { signal: AbortSignal.timeout(1000) })
                    .then(() => {
                    console.log(`[launcher] Backend ready on port ${port}`);
                    resolve();
                })
                    .catch(() => setTimeout(check, 1000));
            });
        }
        check();
    });
}
export function startBackend(projectRoot, port, wsPort) {
    const mvn = findMvn();
    const cmd = `"${mvn}" exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher "-Dexec.args=${port} ${wsPort}" -q`;
    console.log(`[launcher] Starting backend: ${cmd}`);
    const isWin = process.platform === 'win32';
    const proc = spawn('cmd.exe', ['/d', '/s', '/c', cmd], {
        cwd: projectRoot,
        env: { ...process.env, JWCODE_WS_PORT: String(wsPort) },
        stdio: ['ignore', 'ignore', 'pipe'],
    });
    proc.stderr?.on('data', (data) => {
        // Suppress Maven/Java noise, only show errors
        const msg = data.toString().trim();
        if (msg && !msg.startsWith('[INFO]') && !msg.startsWith('[')) {
            console.error(`[backend] ${msg}`);
        }
    });
    return proc;
}
export function cleanupBackend(proc) {
    if (!proc)
        return;
    console.log('\n[jwcode] Shutting down...');
    try {
        process.kill(-proc.pid, 'SIGTERM');
    }
    catch { /* fall through */ }
    proc.kill('SIGTERM');
    setTimeout(() => {
        if (proc && !proc.killed) {
            try {
                process.kill(-proc.pid, 'SIGKILL');
            }
            catch { /* ignore */ }
            proc.kill('SIGKILL');
        }
    }, 5000);
}
