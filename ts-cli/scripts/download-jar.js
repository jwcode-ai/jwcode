#!/usr/bin/env node
/**
 * postinstall fallback — download backend JAR if not bundled.
 *
 * The backend JAR (jwcode-web.jar) should be bundled in the npm package
 * at backend/jwcode-web.jar. If it's missing (e.g. broken publish),
 * this script downloads it from GitHub Releases as a fallback.
 *
 * Environment variables:
 *   JWCODE_JAR_BASE_URL   Base URL for JAR download (default: GitHub Releases)
 *   JWCODE_SKIP_JAR       Set to "1" to skip JAR download
 */
import { existsSync, mkdirSync, createWriteStream } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { pipeline } from 'node:stream/promises';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PACKAGE_DIR = join(__dirname, '..');
const JAR_PATH = join(PACKAGE_DIR, 'backend', 'jwcode-web.jar');
const BASE_URL = process.env.JWCODE_JAR_BASE_URL || 'https://github.com/jwcode-project/jwcode/releases/download';

let PACKAGE_VERSION = '0.0.0';
try {
  const pkg = JSON.parse(
    await import('node:fs').then(fs =>
      fs.promises.readFile(join(PACKAGE_DIR, 'package.json'), 'utf-8')
    )
  );
  PACKAGE_VERSION = pkg.version || PACKAGE_VERSION;
} catch { /* fallback */ }

async function downloadFile(url, destPath) {
  console.log(`[jar] Downloading: ${url}`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status} ${response.statusText}`);
  }
  const total = parseInt(response.headers.get('content-length') || '0', 10);
  let downloaded = 0;
  let lastLog = 0;

  mkdirSync(dirname(destPath), { recursive: true });
  const writer = createWriteStream(destPath);
  const reader = response.body;

  reader.on('data', (chunk) => {
    downloaded += chunk.length;
    if (total && Date.now() - lastLog > 2000) {
      const pct = Math.round((downloaded / total) * 100);
      console.log(`[jar] Progress: ${pct}% (${(downloaded / 1024 / 1024).toFixed(1)} MB)`);
      lastLog = Date.now();
    }
  });

  await pipeline(reader, writer);
  console.log(`[jar] Downloaded: ${(downloaded / 1024 / 1024).toFixed(1)} MB`);
}

async function main() {
  if (process.env.JWCODE_SKIP_JAR === '1') {
    console.log('[jar] JWCODE_SKIP_JAR=1, skipping.');
    return;
  }

  // Check if JAR already bundled
  if (existsSync(JAR_PATH) && JAR_PATH.endsWith('.jar')) {
    const stats = await import('node:fs').then(fs => fs.promises.stat(JAR_PATH));
    if (stats.size > 1000000) { // > 1MB = real JAR, not a placeholder
      console.log('[jar] Backend JAR already exists, skipping download.');
      return;
    }
  }

  const url = `${BASE_URL}/v${PACKAGE_VERSION}/jwcode-web.jar`;

  try {
    console.log(`[jar] Backend JAR not bundled, downloading from GitHub Releases...`);
    await downloadFile(url, JAR_PATH);
    console.log('[jar] Backend JAR ready at backend/jwcode-web.jar');
  } catch (err) {
    console.error(`[jar] Download failed: ${err.message}`);
    console.log('[jar] System Java and Maven build will be required at runtime.');
    console.log('[jar] You can also manually build the backend:');
    console.log(`  cd jwcode && mvn package -pl jwcode-web -am -DskipTests`);
    console.log(`  cp jwcode-web/target/jwcode-web.jar ts-cli/backend/`);
  }
}

main().catch(err => {
  console.error('[jar] Unexpected error:', err.message);
  process.exit(0); // Don't fail — fall back to Maven build
});
