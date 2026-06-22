/**
 * Bundle script -- produces single dist/cli.js with esbuild.
 * If a Maven-built fat JAR is available, copies it to backend/ for local dev use.
 */
import * as esbuild from 'esbuild';
import { readFileSync, existsSync, mkdirSync, copyFileSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const pkg = JSON.parse(readFileSync(join(__dirname, 'package.json'), 'utf-8'));

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
  define: {
    'process.env.APP_VERSION': JSON.stringify(pkg.version),
  },
});

console.log('[build] Bundle: dist/cli.js');

// --publish flag: fail if JAR is missing (used by prepublishOnly)
const isPublish = process.argv.includes('--publish');

// Copy fat JAR from Maven build to backend/ so it gets bundled in the npm package
const MIN_JAR_BYTES = 200 * 1024;
const fatJar = join(__dirname, '..', 'jwcode-web', 'target', 'jwcode-web-1.0.0-SNAPSHOT.jar');
const backendDir = join(__dirname, 'backend');

if (existsSync(fatJar) && statSync(fatJar).size >= MIN_JAR_BYTES) {
  if (!existsSync(backendDir)) mkdirSync(backendDir, { recursive: true });
  copyFileSync(fatJar, join(backendDir, 'jwcode-web.jar'));
  const size = (statSync(fatJar).size / 1024 / 1024).toFixed(1);
  console.log(`[build] Backend JAR (${size} MB) copied to backend/jwcode-web.jar`);
} else if (isPublish) {
  console.error('[build] ERROR: Backend JAR not found or too small.');
  console.error('[build]   Build the Maven fat JAR first:');
  console.error('[build]     npm run build:backend');
  process.exit(1);
} else {
  console.log('[build] Maven fat JAR not found — backend/jwcode-web.jar not updated.');
  console.log('[build]   For dev mode, the backend will be built on first run.');
  console.log('[build]   For publishing, run: npm run build:backend');
}
