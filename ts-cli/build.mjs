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
import { existsSync, mkdirSync, copyFileSync, unlinkSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const fatJarSource = join(__dirname, '..', 'jwcode-web', 'target', 'jwcode-web.jar');
const proguardJar = join(__dirname, 'proguard.jar');
const proguardConf = join(__dirname, 'proguard.conf');
const backendDir = join(__dirname, 'backend');
const obfJar = join(backendDir, 'jwcode-web-obf.jar');

if (existsSync(fatJarSource)) {
  if (!existsSync(backendDir)) mkdirSync(backendDir, { recursive: true });

  // Step 3: ProGuard 混淆 fat JAR
  if (existsSync(proguardJar)) {
    console.log('[build] Running ProGuard obfuscation...');
    try {
      execFileSync('java', [
        '-jar', proguardJar,
        '-injars', fatJarSource,
        '-outjars', obfJar,
        '-libraryjars', `${process.env.JAVA_HOME || ''}/jre/lib/rt.jar`,
        '@' + proguardConf,
        '-printmapping', join(backendDir, 'proguard.map')
      ], { stdio: 'inherit' });
      console.log('[build] ProGuard obfuscation completed: backend/jwcode-web-obf.jar');
      // Rename obfuscated JAR to final name
      const finalJar = join(backendDir, 'jwcode-web.jar');
      if (existsSync(finalJar)) unlinkSync(finalJar);
      copyFileSync(obfJar, finalJar);
      console.log('[build] Final JAR: backend/jwcode-web.jar');
    } catch (err) {
      console.error('[build] ProGuard failed, falling back to un-obfuscated JAR:', err.message);
      copyFileSync(fatJarSource, join(backendDir, 'jwcode-web.jar'));
    }
  } else {
    // Auto-download ProGuard if not present
    const proguardUrl = 'https://github.com/Guardsquare/proguard/releases/download/v7.4.1/proguard-7.4.1.jar';
    console.log('[build] ProGuard JAR not found locally. Downloading from GitHub...');
    try {
      const { createWriteStream } = await import('node:fs');
      const { pipeline } = await import('node:stream/promises');
      const resp = await fetch(proguardUrl);
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      const dest = createWriteStream(proguardJar);
      await pipeline(resp.body, dest);
      console.log('[build] ProGuard downloaded. Running obfuscation...');
      execFileSync('java', [
        '-jar', proguardJar,
        '-injars', fatJarSource,
        '-outjars', obfJar,
        '-libraryjars', String(process.env.JAVA_HOME || '') + '/jre/lib/rt.jar',
        '@' + proguardConf,
        '-printmapping', join(backendDir, 'proguard.map')
      ], { stdio: 'inherit' });
      console.log('[build] ProGuard obfuscation completed.');
      const finalJar = join(backendDir, 'jwcode-web.jar');
      if (existsSync(finalJar)) unlinkSync(finalJar);
      copyFileSync(obfJar, finalJar);
    } catch (err) {
      console.error('[build] ProGuard download/run failed, falling back to un-obfuscated JAR:', err.message);
      copyFileSync(fatJarSource, join(backendDir, 'jwcode-web.jar'));
    }
  }
} else {
  console.log('[build] JAR not found (skip Maven build first, or use --build flag).');
}

// ── JLink: bundle minimal JRE ──────────────────────────────────
function getJavaHome() {
  return process.env.JAVA_HOME || process.env.JDK_HOME || '';
}

function getJlinkPath() {
  const home = getJavaHome();
  const ext = process.platform === 'win32' ? '.exe' : '';
  if (home) {
    const p = join(home, 'bin', 'jlink' + ext);
    if (existsSync(p)) return p;
  }
  // Try PATH
  try {
    const which = execFileSync(process.platform === 'win32' ? 'where' : 'which', ['jlink'], { encoding: 'utf-8' }).trim();
    if (which) return which;
  } catch {}
  return null;
}

function getJmodsPath() {
  const home = getJavaHome();
  const candidates = [
    join(home, 'jmods'),
    join(home, 'lib', 'jmods'),
  ];
  for (const p of candidates) {
    if (existsSync(p)) return p;
  }
  return null;
}

const jlinkPath = getJlinkPath();
const jmodsPath = getJmodsPath();
const jreDir = join(backendDir, 'jre');
const skipJlink = process.argv.includes('--publish');

if (skipJlink) {
  console.log('[build] --publish flag set, skipping JRE bundle.');
} else if (jlinkPath && jmodsPath) {
  // Only build JRE if the JAR step succeeded
  const jarExists = existsSync(join(backendDir, 'jwcode-web.jar'));
  if (!jarExists) {
    console.log('[build] JAR not found, skipping JRE bundle.');
  } else if (!existsSync(jreDir) || !existsSync(join(jreDir, 'release'))) {
    console.log('[build] Bundling minimal JRE with jlink...');
    try {
      execFileSync(jlinkPath, [
        '--module-path', jmodsPath,
        '--add-modules', 'java.base,java.net.http,java.logging,java.sql,java.xml,java.management,java.naming,jdk.unsupported,java.compiler,java.instrument,jdk.httpserver,jdk.crypto.ec',
        '--output', jreDir,
        '--strip-debug',
        '--compress', '2',
        '--no-header-files',
        '--no-man-pages',
        '--vm', 'server',
      ], { stdio: 'inherit' });
      // Verify
      const javaExe = join(jreDir, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
      if (existsSync(javaExe)) {
        const ver = execFileSync(javaExe, ['-version'], { encoding: 'utf-8', stderr: 'pipe' });
        console.log('[build] JRE bundled successfully at backend/jre/');
        console.log('[build]   ' + ver.replace('\n', ' ').trim());
        // Strip the jmods from the output to save space
        console.log('[build]   Size: ~' + Math.round(getDirSize(jreDir) / 1024 / 1024) + ' MB');
      }
    } catch (err) {
      console.error('[build] jlink failed, system Java will be required at runtime:', err.message);
    }
  } else {
    console.log('[build] JRE already exists at backend/jre/, skipping.');
  }
} else {
  const note = !jlinkPath ? 'jlink not found' : 'jmods not found';
  console.log('[build] ' + note + ' — system Java will be required at runtime.');
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