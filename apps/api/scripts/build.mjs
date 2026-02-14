#!/usr/bin/env node
/**
 * Build con esbuild (poco uso de memoria vs tsc).
 * Genera dist/server.js con dependencias externas (node_modules).
 */
import * as esbuild from 'esbuild';
import { readdirSync, mkdirSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const rootDir = join(__dirname, '..');
const srcDir = join(rootDir, 'src');
const outDir = join(rootDir, 'dist');

mkdirSync(outDir, { recursive: true });

await esbuild.build({
  entryPoints: [join(srcDir, 'server.ts')],
  bundle: true,
  platform: 'node',
  target: 'node18',
  outfile: join(outDir, 'server.js'),
  format: 'cjs',
  sourcemap: true,
  packages: 'external',
  logLevel: 'info',
});

console.log('Build OK: dist/server.js');
