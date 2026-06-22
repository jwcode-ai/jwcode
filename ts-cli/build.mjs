/**
 * Bundle script -- produces single dist/cli.js with esbuild.
 * Uses packages=external to avoid CJS/ESM interop issues.
 */
import * as esbuild from 'esbuild';

await esbuild.build({
  entryPoints: ['src/main.ts'],
  bundle: true,
  platform: 'node',
  target: 'node18',
  format: 'esm',
  outfile: 'dist/cli.js',
  packages: 'external',
  resolveExtensions: ['.tsx', '.ts', '.js', '.json'],
  minify: true,
  keepNames: true,
  logLevel: 'info',
});

console.log('[build] Bundle: dist/cli.js');

// Copy fat JAR from Maven build if it exists (dev convenience)
import { existsSync, mkdirSync, copyFileSync, unlinkSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));

function isProguardOutput(jarPath) {
  try {
    const out = execFileSync('unzip', ['-l', jarPath, 'META-INF/proguard/', 'com/jwcode/'], { stdio: ['ignore', 'pipe', 'ignore'] }).toString();
    return out.includes('META-INF/proguard/') && !out.includes('com/jwcode/');
  } catch {
    return false;
  }
}
const fatJarUnversioned = join(__dirname, '..', 'jwcode-web', 'target', 'jwcode-web.jar');
const fatJarVersioned = join(__dirname, '..', 'jwcode-web', 'target', 'jwcode-web-1.0.0-SNAPSHOT.jar');
function pickFatJar() {
  if (existsSync(fatJarUnversioned) && !isProguardOutput(fatJarUnversioned)) return fatJarUnversioned;
  if (existsSync(fatJarVersioned)) return fatJarVersioned;
  return null;
}
const fatJarSource = pickFatJar();
const proguardJar = join(__dirname, 'proguard.jar');
const proguardConf = join(__dirname, 'proguard.conf');
const backendDir = join(__dirname, 'backend');
const obfJar = join(backendDir, 'jwcode-web-obf.jar');

if (existsSync(fatJarSource)) {
  if (!existsSync(backendDir)) mkdirSync(backendDir, { recursive: true });

  if (existsSync(proguardJar)) {
    await runProguard(fatJarSource, obfJar);
  } else {
    console.log('[build] ProGuard JAR not found, downloading...');
    try {
      await downloadProguard(proguardJar);
    } catch (err) {
      console.error('[build] Download failed:', err.message);
    }
    if (existsSync(proguardJar)) {
      await runProguard(fatJarSource, obfJar);
    } else {
      console.error('[build] Failed to download ProGuard, copying un-obfuscated JAR.');
      copyFileSync(fatJarSource, join(backendDir, 'jwcode-web.jar'));
    }
  }
} else {
  console.log('[build] JAR not found (skip Maven build first, or use --build flag).');
}

function downloadProguard(dest) {
  const version = '7.9.1';
  const url = `https://github.com/Guardsquare/proguard/releases/download/v${version}/proguard-${version}.zip`;
  const zipDest = dest + '.zip';

  console.log('[build] Downloading ProGuard ZIP from:', url);
  try {
    execFileSync('curl', ['-skL', '--connect-timeout', '30', '--retry', '3', '--retry-delay', '10', '-o', zipDest, url], { stdio: 'inherit' });
  } catch {
    if (existsSync(zipDest)) unlinkSync(zipDest);
    throw new Error('curl download failed — check network connectivity to GitHub');
  }

  console.log('[build] Extracting proguard.jar...');
  const extractDir = join(dirname(dest), '.proguard_extract');
  try {
    if (existsSync(extractDir)) rmRecursive(extractDir);
    // Use powershell on Windows, unzip on Linux/macOS
    if (process.platform === 'win32') {
      execFileSync('powershell', [
        '-Command',
        `Expand-Archive -Path '${zipDest.replace(/'/g, "''")}' -DestinationPath '${extractDir.replace(/'/g, "''")}' -Force`
      ], { stdio: 'inherit' });
    } else {
      execFileSync('unzip', ['-o', zipDest, '-d', extractDir], { stdio: 'inherit' });
    }
    // Search for proguard.jar anywhere in the extracted tree (ZIP structure varies by version)
    const found = findFileRecursive(extractDir, 'proguard.jar');
    if (!found) throw new Error('proguard.jar not found in extracted ZIP');
    copyFileSync(found, dest);
    console.log('[build] ProGuard JAR ready:', dest);
  } finally {
    if (existsSync(zipDest)) unlinkSync(zipDest);
    if (existsSync(extractDir)) rmRecursive(extractDir);
  }
}

function findFileRecursive(dir, filename) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      const found = findFileRecursive(full, filename);
      if (found) return found;
    } else if (entry.name === filename) {
      return full;
    }
  }
  return null;
}

function rmRecursive(dir) {
  try {
    if (process.platform === 'win32') {
      execFileSync('powershell', ['-Command', `Remove-Item -Recurse -Force '${dir.replace(/'/g, "''")}'`], { stdio: 'ignore' });
    } else {
      execFileSync('rm', ['-rf', dir], { stdio: 'ignore' });
    }
  } catch { /* best effort */ }
}

async function runProguard(fatJarSource, obfJar) {
  console.log('[build] Running ProGuard obfuscation...');
  try {
    const javaHome = process.env.JAVA_HOME || '';
    // JDK 9+ uses .jmod files instead of rt.jar; list all available jmods for ProGuard
    const jmodDir = join(javaHome, 'jmods');
    const libArgs = [];
    if (existsSync(jmodDir)) {
      for (const f of readdirSync(jmodDir)) {
        libArgs.push('-libraryjars', join(jmodDir, f) + '(!**.jar;!module-info.class)');
      }
    } else {
      libArgs.push('-libraryjars', join(javaHome, 'jre', 'lib', 'rt.jar'));
    }
    execFileSync('java', [
      '-jar', proguardJar,
      '-injars', fatJarSource,
      '-outjars', obfJar,
      ...libArgs,
      '@' + proguardConf,
      '-printmapping', join(backendDir, 'proguard.map')
    ], { stdio: 'inherit' });
    console.log('[build] ProGuard obfuscation completed: backend/jwcode-web-obf.jar');
    const finalJar = join(backendDir, 'jwcode-web.jar');
    try {
      if (existsSync(finalJar)) unlinkSync(finalJar);
      copyFileSync(obfJar, finalJar);
    } catch (err) {
      console.warn('[build] Could not replace jwcode-web.jar (file may be locked):', err.message);
      console.warn('[build] Obfuscated JAR available at:', obfJar);
    }
    console.log('[build] Final JAR: backend/jwcode-web.jar');
  } catch (err) {
    console.error('[build] ProGuard failed, falling back to un-obfuscated JAR:', err.message);
    copyFileSync(fatJarSource, join(backendDir, 'jwcode-web.jar'));
  }
}
