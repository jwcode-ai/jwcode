#!/usr/bin/env bun
/**
 * Build script -- produces single dist/cli.js with Bun bundler + Solid JSX transform.
 * Uses @opentui/solid/bun-plugin for Solid JSX compilation.
 * Preserves ProGuard + JLink bundling from the previous esbuild pipeline.
 */
import { existsSync, mkdirSync, copyFileSync, unlinkSync, readdirSync, statSync, createWriteStream, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { pipeline } from 'node:stream/promises';
import { createSolidTransformPlugin } from '@opentui/solid/bun-plugin';

const __dirname = dirname(fileURLToPath(import.meta.url));
const fatJarSource = join(__dirname, '..', 'jwcode-web', 'target', 'jwcode-web.jar');
const proguardJar = join(__dirname, 'proguard.jar');
const proguardConf = join(__dirname, 'proguard.conf');
const backendDir = join(__dirname, 'backend');
const obfJar = join(backendDir, 'jwcode-web-obf.jar');

// Entry point: solid-only build for the new OpenTUI UI.
// Pass --legacy to build the old Ink entry (kept temporarily for fallback).
const useSolid = !process.argv.includes('--legacy');

// Clean dist/ before building to avoid stale artifacts
rmSync(join(__dirname, 'dist'), { recursive: true, force: true });

// ── Step 1: Bundle with Bun + Solid plugin ─────────────────────
if (useSolid) {
  console.log('[build] Bundling Solid + OpenTUI app with Bun ...');
  const buildResult = await Bun.build({
    entrypoints: ['src/main.tsx'],
    outdir: 'dist',
    target: 'bun',
    format: 'esm',
    minify: false,
    naming: 'cli.js',
    packages: 'external',
    plugins: [createSolidTransformPlugin()],
    loader: {
      '.json': 'json',
      '.css': 'text',
    },
  });
  if (!buildResult.success) {
    console.error('[build] Bun build failed:');
    for (const log of buildResult.logs) console.error(log);
    process.exit(1);
  }
  console.log('[build] Bundle: dist/cli.js (Solid+OpenTUI)');
} else {
  // Legacy Ink entry — solid/placeholder.
  console.log('[build] --legacy: bundling Ink entry (Ink fallback)');
  const buildResult = await Bun.build({
    entrypoints: ['src/solid/main.tsx'],
    outdir: 'dist',
    target: 'node',
    format: 'esm',
    minify: false,
    naming: 'cli.ink.js',
    packages: 'external',
  });
  if (!buildResult.success) {
    console.error('[build] Bun legacy build failed:');
    for (const log of buildResult.logs) console.error(log);
    process.exit(1);
  }
  console.log('[build] Bundle: dist/cli.ink.js (legacy Ink)');
}

// ── Step 2: ProGuard obfuscate fat JAR ──────────────────────────
if (existsSync(fatJarSource)) {
  if (!existsSync(backendDir)) mkdirSync(backendDir, { recursive: true });

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
      const finalJar = join(backendDir, 'jwcode-web.jar');
      if (existsSync(finalJar)) unlinkSync(finalJar);
      copyFileSync(obfJar, finalJar);
      console.log('[build] Final JAR: backend/jwcode-web.jar');
    } catch (err) {
      console.error('[build] ProGuard failed, falling back to un-obfuscated JAR:', err.message);
      copyFileSync(fatJarSource, join(backendDir, 'jwcode-web.jar'));
    }
  } else {
    const proguardUrl = 'https://github.com/Guardsquare/proguard/releases/download/v7.6.1/proguard-7.6.1.jar';
    console.log('[build] ProGuard JAR not found locally. Downloading from GitHub...');
    try {
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
      const finalJar = join(backendDir, 'jwcode-web.jar');
      if (existsSync(finalJar)) unlinkSync(finalJar);
      copyFileSync(obfJar, finalJar);
      console.log('[build] ProGuard obfuscation completed.');
    } catch (err) {
      console.error('[build] ProGuard download/run failed, falling back to un-obfuscated JAR:', err.message);
      copyFileSync(fatJarSource, join(backendDir, 'jwcode-web.jar'));
    }
  }
} else {
  console.log('[build] JAR not found (skip Maven build first, or use --build flag).');
}

// In --publish mode, the JAR is REQUIRED — exit with error if missing
if (process.argv.includes('--publish') && !existsSync(join(backendDir, 'jwcode-web.jar'))) {
  console.error('[build] FATAL: backend/jwcode-web.jar is required for npm publish.');
  console.error('[build] Run "mvn package -pl jwcode-web -am -q -DskipTests" first.');
  process.exit(1);
}

// ── Step 3: JLink minimal JRE bundle ─────────────────────────────
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
      const javaExe = join(jreDir, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
      if (existsSync(javaExe)) {
        const ver = execFileSync(javaExe, ['-version'], { encoding: 'utf-8', stderr: 'pipe' });
        console.log('[build] JRE bundled successfully at backend/jre/');
        console.log('[build]   ' + ver.replace('\n', ' ').trim());
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
