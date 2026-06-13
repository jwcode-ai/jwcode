#!/usr/bin/env node
/**
 * publish-all.mjs — publish the same CLI to 15 npm orgs in one pass.
 *
 * Reads canonical metadata from ./package.json, stages a synthetic
 * package per org under .publish-staging/<org>/, and runs
 * `npm publish --access=public --ignore-scripts` in each.
 *
 * Usage:
 *   node scripts/publish-all.mjs [--dry-run] [--only=org1,org2,...]
 *
 * Auth:
 *   Reads NODE_AUTH_TOKEN from env. Mirrors the GitHub Actions secret.
 *
 * Exit codes:
 *   0 — all targeted orgs published (or skipped by --only)
 *   1 — one or more publishes failed; summary printed
 *   2 — invalid arguments
 */

import {
  readFileSync,
  existsSync,
  mkdirSync,
  rmSync,
  cpSync,
  copyFileSync,
  chmodSync,
  writeFileSync,
} from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PKG_DIR = join(__dirname, '..');
const REPO_ROOT = resolve(PKG_DIR, '..');
const STAGING_ROOT = join(PKG_DIR, '.publish-staging');

const ORGS = [
  'jwcode',
  'zhipucode',
  'aliclaw',
  'zhupuclaw',
  'kimicode',
  'minimaxcode',
  'alicode',
  'huaweiyun',
  'tencentclaw',
  'deepseekclaw',
  'tencentcode',
  'deepseekcode',
  'deepclaw',
  'minimaxclaw',
  'hyclaw',
  'mimocode',
];

// --- CLI arg parsing -------------------------------------------------------
const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const onlyArg = args.find((a) => a.startsWith('--only='));
const onlyOrgs = onlyArg
  ? new Set(
      onlyArg
        .slice('--only='.length)
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    )
  : null;
const targetOrgs = onlyOrgs ? ORGS.filter((o) => onlyOrgs.has(o)) : ORGS;

if (onlyOrgs) {
  for (const o of onlyOrgs) {
    if (!ORGS.includes(o)) {
      console.error(`[publish-all] unknown org in --only: ${o}`);
      console.error(`[publish-all] valid orgs: ${ORGS.join(', ')}`);
      process.exit(2);
    }
  }
  if (targetOrgs.length === 0) {
    console.error(`[publish-all] --only matched no orgs`);
    process.exit(2);
  }
}

// --- Load canonical package.json -------------------------------------------
const srcPkg = JSON.parse(readFileSync(join(PKG_DIR, 'package.json'), 'utf8'));
const { version, description, type, main, engines, dependencies, publishConfig } = srcPkg;

if (!version || !dependencies) {
  console.error('[publish-all] source package.json is missing required fields (version, dependencies)');
  process.exit(2);
}

// --- Helpers ---------------------------------------------------------------
function stageOrg(org) {
  const dir = join(STAGING_ROOT, org);
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });

  // dist/ and backend/ — recursive copies
  cpSync(join(PKG_DIR, 'dist'), join(dir, 'dist'), { recursive: true });
  cpSync(join(PKG_DIR, 'backend'), join(dir, 'backend'), { recursive: true });

  // Remove platform-specific JRE from package — it's downloaded by postinstall
  const jreDir = join(dir, 'backend', 'jre');
  if (existsSync(jreDir)) rmSync(jreDir, { recursive: true, force: true });

  // Safety check: backend/jwcode-web.jar is REQUIRED in the published package
  const jarPath = join(dir, 'backend', 'jwcode-web.jar');
  if (!existsSync(jarPath)) {
    console.error(`[publish-all] FATAL: backend/jwcode-web.jar not found for ${org}.`);
    console.error('[publish-all] Run "node build.mjs --publish" first to bundle the backend JAR.');
    process.exit(1);
  }

  // scripts/ — REQUIRED for end-user postinstall.
  // The ts-cli/.npmignore excludes scripts/ but we ship them explicitly
  // via the synthetic `files` field, so the published tarball always contains them.
  mkdirSync(join(dir, 'scripts'), { recursive: true });
  for (const script of ['download-jre.js', 'download-jar.js']) {
    copyFileSync(
      join(PKG_DIR, 'scripts', script),
      join(dir, 'scripts', script),
    );
    chmodSync(join(dir, 'scripts', script), 0o755);
  }

  // Optional: README + LICENSE
  const readmeSrc = join(PKG_DIR, 'README.md');
  if (existsSync(readmeSrc)) copyFileSync(readmeSrc, join(dir, 'README.md'));

  const licenseSrc = join(REPO_ROOT, 'LICENSE');
  if (existsSync(licenseSrc)) copyFileSync(licenseSrc, join(dir, 'LICENSE'));

  // Synthetic package.json
  const files = [
    'dist/',
    'backend/',
    'scripts/download-jre.js',
    'scripts/download-jar.js',
  ];
  if (existsSync(join(dir, 'README.md'))) files.push('README.md');
  if (existsSync(join(dir, 'LICENSE'))) files.push('LICENSE');

  const synth = {
    name: `@${org}/cli`,
    version,
    description: description || '',
    type: type || 'commonjs',
    main: main || 'dist/cli.js',
    bin: { [org]: './dist/cli.js' },
    scripts: {
      postinstall: 'node scripts/download-jar.js && node scripts/download-jre.js',
      // NO prepublishOnly — staging dir has no build.mjs
    },
    dependencies: { ...dependencies },
    files,
    engines: engines || { node: '>=18' },
    publishConfig: publishConfig || { access: 'public' },
  };

  writeFileSync(join(dir, 'package.json'), JSON.stringify(synth, null, 2) + '\n');
  return dir;
}

function publishOne(org) {
  const dir = stageOrg(org);
  const name = `@${org}/cli`;

  if (dryRun) {
    return { org, name, version, status: 'dry-run', error: null };
  }

  if (!process.env.NODE_AUTH_TOKEN) {
    return {
      org,
      name,
      version,
      status: 'failed',
      error: 'NODE_AUTH_TOKEN env var not set',
    };
  }

  const r = spawnSync(
    'npm',
    ['publish', '--access=public', '--ignore-scripts'],
    { cwd: dir, env: process.env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
  );

  if (r.status === 0) {
    return { org, name, version, status: 'ok', error: null };
  }

  const out = ((r.stdout || '') + (r.stderr || '')).trim();
  let err = out.split('\n').filter(Boolean).pop() || `npm publish exited ${r.status}`;
  if (/EPUBLISHCONFLICT/.test(out) || /cannot publish over/.test(out)) {
    err = `version ${version} already published`;
  } else if (/ENEEDAUTH/.test(out) || /not authorized/.test(out)) {
    err = `auth failed (check NODE_AUTH_TOKEN and org membership)`;
  } else if (/EOTP/.test(out)) {
    err = `2FA required — token must be a publish token with bypass`;
  } else if (err.length > 200) {
    err = err.slice(0, 200) + '…';
  }
  return { org, name, version, status: 'failed', error: err };
}

// --- Main loop -------------------------------------------------------------
console.log(
  `[publish-all] version=${version} orgs=${targetOrgs.length}${dryRun ? ' (dry-run)' : ''}`,
);
if (!dryRun && !process.env.NODE_AUTH_TOKEN) {
  console.error('[publish-all] WARNING: NODE_AUTH_TOKEN is not set; all publishes will fail');
}

const results = [];
for (const org of targetOrgs) {
  process.stdout.write(`[publish-all] ${org} ... `);
  const r = publishOne(org);
  results.push(r);
  process.stdout.write(`${r.status}${r.error ? ` — ${r.error}` : ''}\n`);
}

// --- Summary ---------------------------------------------------------------
const ok = results.filter((r) => r.status === 'ok' || r.status === 'dry-run').length;
const bad = results.filter((r) => r.status === 'failed').length;
console.log('\n=== publish-all summary ===');
for (const r of results) {
  console.log(
    `  ${r.status.padEnd(7)} ${r.name.padEnd(20)} v${r.version}${r.error ? `  (${r.error})` : ''}`,
  );
}
console.log(`=== ${ok} ok, ${bad} failed ===\n`);

if (!dryRun) {
  if (bad === 0) {
    rmSync(STAGING_ROOT, { recursive: true, force: true });
  } else {
    console.log(`[publish-all] staging kept at ${STAGING_ROOT} for inspection`);
  }
}

process.exit(bad === 0 ? 0 : 1);
