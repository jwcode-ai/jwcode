/**
 * Bundle script -- produces single dist/cli.js with esbuild.
 * Uses packages=external to avoid CJS/ESM interop issues.
 *
 * Usage:
 *   node build.mjs              # type-check + bundle
 *   node build.mjs --skip-typecheck  # bundle only (fast)
 */
import { spawnSync } from 'node:child_process';
import * as esbuild from 'esbuild';

// ── Step 1: TypeScript type check (optional) ────────────────────────
const skipTypeCheck = process.argv.includes('--skip-typecheck');
if (!skipTypeCheck) {
  console.log('[build] Type-checking with tsc...');
  const tsc = spawnSync('npx', ['tsc', '--noEmit'], {
    stdio: 'inherit',
    shell: true,
  });
  if (tsc.status !== 0) {
    console.error('[build] ❌ Type check failed. Run with --skip-typecheck to bypass.');
    process.exit(1);
  }
  console.log('[build] ✅ Type check passed.');
} else {
  console.log('[build] ⏩ Skipping type check (--skip-typecheck).');
}

// ── Step 2: esbuild bundle ──────────────────────────────────────────
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

console.log('[build] ✅ Bundle: dist/cli.js');

// Copy fat JAR from Maven build if it exists (dev convenience)
import { existsSync, mkdirSync, copyFileSync, unlinkSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const JAR_SRC = join(__dirname, 'backend', 'target', 'jwcode-backend.jar');
const JAR_DST = join(__dirname, 'dist', 'jwcode-backend.jar');

if (existsSync(JAR_SRC)) {
  mkdirSync(join(__dirname, 'dist'), { recursive: true });
  // Remove old JAR before copy to avoid stale files
  if (existsSync(JAR_DST)) unlinkSync(JAR_DST);
  copyFileSync(JAR_SRC, JAR_DST);
  console.log('[build] ✅ Copied backend JAR to dist/');
} else {
  console.log('[build] ℹ️  No backend JAR found at backend/target/jwcode-backend.jar (skip)');
}
