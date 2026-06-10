/**
 * Auto-update checker — polls GitHub releases for new versions.
 * Uses the daemon's existing health infrastructure for monitoring.
 */
import { execSync } from 'node:child_process';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';

const GITHUB_API_RELEASES = 'https://api.github.com/repos/jwcode/jwcode/releases/latest';
const UPDATE_CHECK_FILE = join(homedir(), '.jwcode', 'last_update_check.json');
const CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000; // 6 hours

interface UpdateInfo {
  latest: string;
  current: string;
  url: string;
  body: string;
  publishedAt: string;
  updateAvailable: boolean;
}

interface CheckCache {
  lastCheck: number;
  latestVersion: string;
}

function readCache(): CheckCache | null {
  try {
    if (!existsSync(UPDATE_CHECK_FILE)) return null;
    return JSON.parse(readFileSync(UPDATE_CHECK_FILE, 'utf-8'));
  } catch { return null; }
}

function writeCache(version: string): void {
  try {
    mkdirSync(join(homedir(), '.jwcode'), { recursive: true });
    writeFileSync(UPDATE_CHECK_FILE, JSON.stringify({ lastCheck: Date.now(), latestVersion: version }));
  } catch { /* ignore */ }
}

export async function checkForUpdates(currentVersion: string): Promise<UpdateInfo | null> {
  // Check cache first
  const cache = readCache();
  if (cache && (Date.now() - cache.lastCheck) < CHECK_INTERVAL_MS) {
    return {
      latest: cache.latestVersion,
      current: currentVersion,
      url: '',
      body: '',
      publishedAt: '',
      updateAvailable: cache.latestVersion !== currentVersion,
    };
  }

  try {
    const resp = await fetch(GITHUB_API_RELEASES, {
      headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'JWCode-CLI' },
    });
    if (!resp.ok) return null;
    const release = await resp.json() as any;
    const latest = (release.tag_name || '').replace(/^v/, '');
    writeCache(latest);

    return {
      latest,
      current: currentVersion,
      url: release.html_url || '',
      body: release.body || '',
      publishedAt: release.published_at || '',
      updateAvailable: latest !== currentVersion,
    };
  } catch { return null; }
}

/** Perform npm update of the globally installed package. */
export function runNpmUpdate(): { ok: boolean; message: string } {
  try {
    const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
    const result = execSync(`"${npm}" install -g @jwcode/cli@latest`, {
      encoding: 'utf-8',
      stdio: 'pipe',
      timeout: 120_000,
    });
    return { ok: true, message: result.trim() };
  } catch (e: any) {
    return { ok: false, message: String(e.stderr || e.message || e) };
  }
}

/** Check daemon health by hitting the status endpoint. */
export async function checkDaemonHealth(port: number): Promise<boolean> {
  try {
    const resp = await fetch(`http://localhost:${port}/api/system/status`, {
      signal: AbortSignal.timeout(5000),
    });
    if (resp.status === 200) {
      const data = await resp.json() as any;
      return data?.status === 'running' || data?.success === true;
    }
    return false;
  } catch { return false; }
}
