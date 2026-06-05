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
    console.log('[build] ProGuard JAR not found, skipping obfuscation. Copying un-obfuscated JAR.');
    copyFileSync(fatJarSource, join(backendDir, 'jwcode-web.jar'));
  }
} else {
  console.log('[build] JAR not found (skip Maven build first, or use --build flag).');
}
