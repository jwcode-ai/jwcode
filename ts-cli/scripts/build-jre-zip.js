#!/usr/bin/env node
/**
 * Build JRE archive for the current platform.
 *
 * Usage:
 *   node scripts/build-jre-zip.js
 *
 * This runs jlink to produce a minimal JRE in backend/jre/,
 * then packs it into a compressed archive you can upload
 * to your download site.
 *
 * Environment:
 *   JAVA_HOME       Path to JDK 17+ (default: from env or PATH)
 *   JWCODE_JRE_MODULES   Comma-separated JRE modules (see build.mjs)
 *   JWCODE_JRE_VERSION   JRE version tag (default: "17")
 *
 * Prerequisites:
 *   JDK 17+ with jmods (jlink + jmods directory must exist)
 */
import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { platform } from 'node:os';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PACKAGE_DIR = join(__dirname, '..');
const JRE_DIR = join(PACKAGE_DIR, 'backend', 'jre');
const OUTPUT_DIR = join(PACKAGE_DIR, 'dist');

const MODULES = [
  'java.base', 'java.net.http', 'java.logging', 'java.sql',
  'java.xml', 'java.management', 'java.naming', 'jdk.unsupported',
  'java.compiler', 'java.instrument', 'jdk.httpserver', 'jdk.crypto.ec',
  'java.security.sasl', 'java.transaction.xa',
];

function getJavaHome() {
  return process.env.JAVA_HOME || process.env.JDK_HOME || '';
}

function findJlink() {
  const home = getJavaHome();
  const ext = platform() === 'win32' ? '.exe' : '';
  if (home) {
    const p = join(home, 'bin', 'jlink' + ext);
    if (existsSync(p)) return p;
  }
  // Try PATH
  try {
    const which = execSync(
      platform() === 'win32' ? 'where jlink' : 'which jlink',
      { encoding: 'utf-8' }
    ).trim().split('\n')[0];
    if (which) return which;
  } catch { /* not in PATH */ }
  return null;
}

function findJmods() {
  const home = getJavaHome();
  if (!home) return null;
  for (const dir of [join(home, 'jmods'), join(home, 'lib', 'jmods')]) {
    if (existsSync(dir)) return dir;
  }
  return null;
}

function getDirSize(dir) {
  let size = 0;
  try {
    const entries = readdirSync(dir, { recursive: true, withFileTypes: true });
    for (const entry of entries) {
      if (entry.isFile()) {
        size += statSync(join(entry.parentPath || entry.path, entry.name)).size;
      }
    }
  } catch {}
  return size;
}

function detectPlatform() {
  const os = platform();
  const { arch } = await import('node:os');
  const cpu = arch();

  if (os === 'win32' && cpu === 'x64') return 'win32-x64';
  if (os === 'darwin' && cpu === 'arm64') return 'darwin-arm64';
  if (os === 'darwin' && cpu === 'x64') return 'darwin-x64';
  if (os === 'linux' && cpu === 'x64') return 'linux-x64';
  if (os === 'linux' && cpu === 'arm64') return 'linux-arm64';

  return `${os}-${cpu}`; // unknown, try anyway
}

async function main() {
  const [nodeMajor] = process.versions.node.split('.').map(Number);
  if (nodeMajor < 18) {
    console.error('Node.js 18+ required.');
    process.exit(1);
  }

  // Read package version
  let version = '0.0.0';
  try {
    const pkg = JSON.parse(
      await import('node:fs').then(fs =>
        fs.promises.readFile(join(PACKAGE_DIR, 'package.json'), 'utf-8')
      )
    );
    version = pkg.version || version;
  } catch {}

  const jlinkPath = findJlink();
  const jmodsPath = findJmods();

  if (!jlinkPath) {
    console.error('jlink not found. Set JAVA_HOME to JDK 17+.');
    process.exit(1);
  }
  if (!jmodsPath) {
    console.error('jmods directory not found. Ensure JAVA_HOME points to a full JDK (not JRE).');
    process.exit(1);
  }

  console.log('=== Building JRE ===');
  console.log(`  JDK:      ${getJavaHome() || '(from PATH)'}`);
  console.log(`  jlink:    ${jlinkPath}`);
  console.log(`  jmods:    ${jmodsPath}`);
  console.log(`  output:   ${JRE_DIR}`);
  console.log(`  modules:  ${MODULES.length}`);

  // Step 1: jlink
  mkdirSync(JRE_DIR, { recursive: true });
  console.log('\n[1/3] Running jlink...');
  execSync(
    `"${jlinkPath}" --module-path "${jmodsPath}"` +
    ` --add-modules ${MODULES.join(',')}` +
    ` --output "${JRE_DIR}"` +
    ` --strip-debug --compress 2 --no-header-files --no-man-pages --vm server`,
    { stdio: 'inherit', cwd: PACKAGE_DIR }
  );

  // Verify
  const javaBin = join(JRE_DIR, 'bin', platform() === 'win32' ? 'java.exe' : 'java');
  if (!existsSync(javaBin)) {
    console.error('jlink failed: java binary not found in output.');
    process.exit(1);
  }
  const verOut = execSync(`"${javaBin}" -version`, { encoding: 'utf-8', stderr: 'pipe' });
  console.log('  ' + verOut.replace(/\n/g, ' ').trim());
  console.log(`  Size: ~${Math.round(getDirSize(JRE_DIR) / 1024 / 1024)} MB`);

  // Step 2: pack into archive
  const plat = detectPlatform();
  mkdirSync(OUTPUT_DIR, { recursive: true });

  const archiveName = `jre-${plat}.tar.gz`;
  const archivePath = join(OUTPUT_DIR, archiveName);

  console.log(`\n[2/3] Packing: ${archiveName}...`);
  execSync(
    `tar -czf "${archivePath}" -C "${join(PACKAGE_DIR, 'backend')}" jre`,
    { stdio: 'inherit', cwd: PACKAGE_DIR }
  );
  const archiveSize = statSync(archivePath).size;
  console.log(`  Created: ${archiveName} (${(archiveSize / 1024 / 1024).toFixed(1)} MB)`);

  // Step 3: print summary
  console.log(`\n[3/3] Done.`);
  console.log(`  Archive: ${archivePath}`);
  console.log(`  Upload this to your download site as:`);
  console.log(`    ${BASE_URL}/v${version}/${archiveName}`);
  console.log(`\n  Or set JWCODE_JRE_BASE_URL for custom URL.`);
  console.log(`  Upload URL:`);
  console.log(`    (your download site)/v${version}/${archiveName}`);
}

// Read base URL for the summary message
let BASE_URL = process.env.JWCODE_JRE_BASE_URL || 'https://github.com/jwcode-project/jwcode/releases/download';

main().catch(err => {
  console.error('Build failed:', err);
  process.exit(1);
});
#!/usr/bin/env node
/**
 * Build JRE archive for the current platform.
 *
 * Usage:
 *   node scripts/build-jre-zip.js
 *
 * This runs jlink to produce a minimal JRE in backend/jre/,
 * then packs it into a compressed archive you can upload
 * to your download site.
 *
 * Environment:
 *   JAVA_HOME       Path to JDK 17+ (default: from env or PATH)
 *
 * Prerequisites:
 *   JDK 17+ with jmods (jlink + jmods directory must exist)
 */
import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { platform, arch } from 'node:os';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PACKAGE_DIR = join(__dirname, '..');
const JRE_DIR = join(PACKAGE_DIR, 'backend', 'jre');
const OUTPUT_DIR = join(PACKAGE_DIR, 'dist');

const MODULES = [
  'java.base', 'java.net.http', 'java.logging', 'java.sql',
  'java.xml', 'java.management', 'java.naming', 'jdk.unsupported',
  'java.compiler', 'java.instrument', 'jdk.httpserver', 'jdk.crypto.ec',
  'java.security.sasl', 'java.transaction.xa',
];

function getJavaHome() {
  return process.env.JAVA_HOME || process.env.JDK_HOME || '';
}

function findJlink() {
  const home = getJavaHome();
  const ext = platform() === 'win32' ? '.exe' : '';
  if (home) {
    const p = join(home, 'bin', 'jlink' + ext);
    if (existsSync(p)) return p;
  }
  try {
    const which = execSync(
      platform() === 'win32' ? 'where jlink' : 'which jlink',
      { encoding: 'utf-8' }
    ).trim().split('\n')[0];
    if (which) return which;
  } catch { /* not in PATH */ }
  return null;
}

function findJmods() {
  const home = getJavaHome();
  if (!home) return null;
  for (const dir of [join(home, 'jmods'), join(home, 'lib', 'jmods')]) {
    if (existsSync(dir)) return dir;
  }
  return null;
}

function getDirSize(dir) {
  let size = 0;
  try {
    const entries = readdirSync(dir, { recursive: true, withFileTypes: true });
    for (const entry of entries) {
      if (entry.isFile()) {
        size += statSync(join(entry.parentPath || entry.path, entry.name)).size;
      }
    }
  } catch {}
  return size;
}

function detectPlatform() {
  const os = platform();
  const cpu = arch();
  if (os === 'win32' && cpu === 'x64') return 'win32-x64';
  if (os === 'darwin' && cpu === 'arm64') return 'darwin-arm64';
  if (os === 'darwin' && cpu === 'x64') return 'darwin-x64';
  if (os === 'linux' && cpu === 'x64') return 'linux-x64';
  if (os === 'linux' && cpu === 'arm64') return 'linux-arm64';
  return `${os}-${cpu}`;
}

async function main() {
  const [nodeMajor] = process.versions.node.split('.').map(Number);
  if (nodeMajor < 18) {
    console.error('Node.js 18+ required.');
    process.exit(1);
  }

  let version = '0.0.0';
  try {
    const pkg = JSON.parse(
      await import('node:fs').then(fs =>
        fs.promises.readFile(join(PACKAGE_DIR, 'package.json'), 'utf-8')
      )
    );
    version = pkg.version || version;
  } catch {}

  const jlinkPath = findJlink();
  const jmodsPath = findJmods();

  if (!jlinkPath) {
    console.error('jlink not found. Set JAVA_HOME to JDK 17+.');
    process.exit(1);
  }
  if (!jmodsPath) {
    console.error('jmods directory not found. Ensure JAVA_HOME points to a full JDK (not JRE).');
    process.exit(1);
  }

  console.log('=== Building JRE ===');
  console.log(`  JDK:      ${getJavaHome() || '(from PATH)'}`);
  console.log(`  jlink:    ${jlinkPath}`);
  console.log(`  jmods:    ${jmodsPath}`);
  console.log(`  output:   ${JRE_DIR}`);
  console.log(`  modules:  ${MODULES.length}`);

  // Step 1: jlink
  mkdirSync(JRE_DIR, { recursive: true });
  console.log('\n[1/3] Running jlink...');
  execSync(
    `"${jlinkPath}" --module-path "${jmodsPath}"` +
    ` --add-modules ${MODULES.join(',')}` +
    ` --output "${JRE_DIR}"` +
    ` --strip-debug --compress 2 --no-header-files --no-man-pages --vm server`,
    { stdio: 'inherit', cwd: PACKAGE_DIR }
  );

  const javaBin = join(JRE_DIR, 'bin', platform() === 'win32' ? 'java.exe' : 'java');
  if (!existsSync(javaBin)) {
    console.error('jlink failed: java binary not found in output.');
    process.exit(1);
  }
  const verOut = execSync(`"${javaBin}" -version`, { encoding: 'utf-8', stderr: 'pipe' });
  console.log('  ' + verOut.replace(/\n/g, ' ').trim());
  console.log(`  Size: ~${Math.round(getDirSize(JRE_DIR) / 1024 / 1024)} MB`);

  // Step 2: pack into archive
  const plat = detectPlatform();
  mkdirSync(OUTPUT_DIR, { recursive: true });

  const archiveName = `jre-${plat}.tar.gz`;
  const archivePath = join(OUTPUT_DIR, archiveName);

  console.log(`\n[2/3] Packing: ${archiveName}...`);
  execSync(
    `tar -czf "${archivePath}" -C "${join(PACKAGE_DIR, 'backend')}" jre`,
    { stdio: 'inherit', cwd: PACKAGE_DIR }
  );
  const archiveSize = statSync(archivePath).size;
  console.log(`  Created: ${archiveName} (${(archiveSize / 1024 / 1024).toFixed(1)} MB)`);

  // Step 3: print summary
  const BASE_URL = process.env.JWCODE_JRE_BASE_URL || 'https://github.com/jwcode-project/jwcode/releases/download';
  console.log(`\n[3/3] Done.`);
  console.log(`  Archive: ${archivePath}`);
  console.log(`  Upload this to your download site as:`);
  console.log(`    (your-site)/downloads/v${version}/${archiveName}`);
  console.log(`\n  Or set JWCODE_JRE_BASE_URL for custom URL.`);
}

main().catch(err => {
  console.error('Build failed:', err);
  process.exit(1);
});

