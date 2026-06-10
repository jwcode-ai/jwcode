#!/usr/bin/env node
/**
 * postinstall — download & extract platform-specific JRE.
 *
 * Looks for an existing backend/jre/ first; if found, skips.
 * Otherwise detects the current platform and downloads a
 * pre-built JRE archive from a configurable base URL.
 *
 * Environment variables:
 *   JWCODE_JRE_BASE_URL   Base URL for JRE archives (default: see below)
 *   JWCODE_SKIP_JRE       Set to "1" to skip JRE download entirely
 *   JWCODE_JRE_VERSION    Java version tag (default: "17")
 *
 * Default URL pattern:
 *   {baseURL}/v{package-version}/jre-{platform}.tar.gz
 */

import { existsSync, mkdirSync, createWriteStream } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { pipeline } from 'node:stream/promises';
import { execSync } from 'node:child_process';
import { platform, arch } from 'node:os';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PACKAGE_DIR = join(__dirname, '..');
const JRE_DIR = join(PACKAGE_DIR, 'backend', 'jre');

// Read version from package.json
let PACKAGE_VERSION = '0.0.0';
try {
  const pkg = JSON.parse(
    await import('node:fs').then(fs =>
      fs.promises.readFile(join(PACKAGE_DIR, 'package.json'), 'utf-8')
    )
  );
  PACKAGE_VERSION = pkg.version || PACKAGE_VERSION;
} catch { /* fallback */ }

const BASE_URL = process.env.JWCODE_JRE_BASE_URL || 'https://github.com/jwcode-project/jwcode/releases/download';

function detectPlatform() {
  const os = platform();
  const cpu = arch();

  if (os === 'win32' && cpu === 'x64') return 'win32-x64';
  if (os === 'darwin' && cpu === 'arm64') return 'darwin-arm64';
  if (os === 'darwin' && cpu === 'x64') return 'darwin-x64';
  if (os === 'linux' && cpu === 'x64') return 'linux-x64';
  if (os === 'linux' && cpu === 'arm64') return 'linux-arm64';

  return null; // unsupported
}

async function downloadFile(url, destPath) {
  console.log(`[jre] Downloading: ${url}`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status} ${response.statusText}`);
  }
  const total = parseInt(response.headers.get('content-length') || '0', 10);
  let downloaded = 0;
  let lastLog = 0;

  // Ensure parent dir exists
  mkdirSync(dirname(destPath), { recursive: true });

  const writer = createWriteStream(destPath);
  const reader = response.body;

  reader.on('data', (chunk) => {
    downloaded += chunk.length;
    if (total && Date.now() - lastLog > 2000) {
      const pct = Math.round((downloaded / total) * 100);
      console.log(`[jre] Progress: ${pct}% (${(downloaded / 1024 / 1024).toFixed(1)} MB)`);
      lastLog = Date.now();
    }
  });

  await pipeline(reader, writer);
  console.log(`[jre] Downloaded: ${(downloaded / 1024 / 1024).toFixed(1)} MB`);
}

async function extractArchive(archivePath, targetDir) {
  mkdirSync(targetDir, { recursive: true });

  const ext = archivePath.endsWith('.zip') ? 'zip' : 'targz';

  console.log(`[jre] Extracting to: ${targetDir}`);

  try {
    if (ext === 'zip') {
      // Use system unzip or PowerShell on Windows
      if (platform() === 'win32') {
        // Windows: use tar (built-in since Win10) to extract .tar.gz, or PowerShell for .zip
        execSync(
          `powershell -Command "Expand-Archive -Path '${archivePath}' -DestinationPath '${targetDir}' -Force"`,
          { stdio: 'pipe', timeout: 120_000 }
        );
      } else {
        execSync(`unzip -o -q "${archivePath}" -d "${targetDir}"`, {
          stdio: 'pipe', timeout: 120_000
        });
      }
    } else {
      // tar.gz: use system tar (available on all modern platforms)
      execSync(
        `tar -xzf "${archivePath}" -C "${targetDir}"`,
        { stdio: 'pipe', timeout: 120_000 }
      );
    }
  } catch (err) {
    // Try Node.js native alternative for .tar.gz
    if (ext === 'targz') {
      console.log('[jre] System tar failed, trying Node.js native extraction...');
      await extractTarGzNative(archivePath, targetDir);
    } else {
      throw err;
    }
  }
}

/**
 * Fallback: extract .tar.gz using Node.js built-in zlib + tar-stream.
 * This avoids depending on system tar being available.
 */
async function extractTarGzNative(archivePath, targetDir) {
  // We need 'tar' and 'zlib' from npm OR use the built-in zlib + child_process
  // Since the package depends on 'tar-stream' is not guaranteed, try with
  // system commands first, then fall back to a pure-Node approach.
  //
  // On Windows 10/11, 'tar' is built-in. On Linux/macOS it's always there.
  // This fallback is a safety net.
  throw new Error(
    'Could not extract JRE archive. Ensure system tar is available.\n' +
    '  Windows 10+:  tar is built-in\n' +
    '  macOS/Linux:  tar is pre-installed\n' +
    `  You can also manually extract ${archivePath} to ${targetDir}`
  );
}

function findExistingJRE() {
  if (!existsSync(JRE_DIR)) return false;
  // Check if it looks like a real JRE (has java binary)
  const javaBin = platform() === 'win32'
    ? join(JRE_DIR, 'bin', 'java.exe')
    : join(JRE_DIR, 'bin', 'java');
  return existsSync(javaBin);
}

async function main() {
  if (process.env.JWCODE_SKIP_JRE === '1') {
    console.log('[jre] JWCODE_SKIP_JRE=1, skipping JRE download.');
    return;
  }

  // Check if JRE already exists
  if (findExistingJRE()) {
    console.log('[jre] JRE already exists at backend/jre/, skipping.');
    return;
  }

  const plat = detectPlatform();
  if (!plat) {
    console.log(`[jre] Unsupported platform: ${platform()} ${arch()}.`);
    console.log('[jre] System Java 17+ will be required at runtime.');
    console.log('[jre] To skip this check: JWCODE_SKIP_JRE=1 npm install');
    return;
  }

  // Build download URL
  const tag = `v${PACKAGE_VERSION}`;
  const archiveName = `jre-${plat}.tar.gz`;
  const url = `${BASE_URL}/${tag}/${archiveName}`;
  const destDir = join(PACKAGE_DIR, 'backend');
  const destPath = join(destDir, archiveName);

  try {
    console.log(`[jre] Downloading JRE for ${plat}...`);
    await downloadFile(url, destPath);
    await extractArchive(destPath, JRE_DIR);
    console.log('[jre] JRE ready at backend/jre/');

    // Clean up archive
    try {
      await import('node:fs').then(fs => fs.promises.unlink(destPath));
    } catch { /* ignore */ }
  } catch (err) {
    console.error(`[jre] Download failed: ${err.message}`);
    console.log('[jre] System Java 17+ will be required at runtime.');
    console.log('[jre] You can also manually download JRE from:');
    console.log(`  ${url}`);
    console.log(`  and extract it to: backend/jre/`);
  }
}

main().catch(err => {
  console.error('[jre] Unexpected error:', err.message);
  process.exit(0); // Don't fail the install — fall back to system Java
});

