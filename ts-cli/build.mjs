/**
 * Bundle script — produces single dist/cli.js with esbuild.
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
  logLevel: 'info',
  minify: false,
  keepNames: true,
});

console.log('[build] Bundle: dist/cli.js');
