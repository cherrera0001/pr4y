#!/usr/bin/env npx ts-node
/**
 * Auditoría de aislamiento por usuario (userId) en queries Prisma.
 * Regla: Todas las queries que tocan datos de usuario (Record, Answer, Reminder, WrappedDek, UsageLog)
 * deben incluir where: { userId } o where: { record: { userId } } (o equivalente).
 * Excepciones documentadas: rutas admin con requireAdmin que no devuelven datos de un usuario concreto.
 *
 * Uso: desde la raíz del repo: npx ts-node scripts/audit-user-isolation.ts
 * O: cd apps/api && npx ts-node ../../scripts/audit-user-isolation.ts
 */

import * as fs from 'fs';
import * as path from 'path';

const API_SRC = path.join(__dirname, '..', 'apps', 'api', 'src');
const USER_SCOPED_MODELS = ['record', 'answer', 'reminder', 'wrappedDek', 'usageLog'] as const;
const PRISMA_METHODS = ['findMany', 'findFirst', 'findUnique', 'update', 'delete', 'deleteMany', 'upsert'];

interface Finding {
  file: string;
  line: number;
  model: string;
  method: string;
  snippet: string;
  hasUserId: boolean;
  note?: string;
}

function* walkTs(dir: string): Generator<string> {
  if (!fs.existsSync(dir)) return;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory() && e.name !== 'node_modules') {
      yield* walkTs(full);
    } else if (e.isFile() && (e.name.endsWith('.ts') || e.name.endsWith('.tsx'))) {
      yield full;
    }
  }
}

function auditFile(filePath: string): Finding[] {
  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const findings: Finding[] = [];
  const relPath = path.relative(path.join(__dirname, '..'), filePath);

  for (const model of USER_SCOPED_MODELS) {
    const prismaCall = new RegExp(`prisma\\.${model}\\.(${PRISMA_METHODS.join('|')})\\s*\\(`, 'g');
    let m: RegExpExecArray | null;
    while ((m = prismaCall.exec(content)) !== null) {
      const idx = content.indexOf(m[0]);
      const lineNum = content.slice(0, idx).split('\n').length;
      const startLine = Math.max(0, lineNum - 1);
      const endLine = Math.min(lines.length, lineNum + 15);
      const snippet = lines.slice(startLine, endLine).join('\n');
      const block = content.slice(idx, Math.min(content.length, idx + 1200));
      const hasUserId =
        /where:\s*\{[^}]*userId/.test(block) ||
        /where:\s*\{\s*[^}]*record:\s*\{\s*[^}]*userId/.test(block) ||
        /where:\s*\{\s*[^}]*id:\s*[^,}]+,\s*userId/.test(block) ||
        /userId_day:\s*\{\s*userId/.test(block) ||
        /where:\s*\{\s*day:/.test(block); // usageLog by day (admin stats)
      const isAdminOnly =
        relPath.includes('admin') ||
        relPath.includes('usage.ts'); // usage.getUsageStats / getStatsDetail
      const isGlobalContent = model === 'record' && block.includes('orderBy') && block.includes('serverUpdatedAt') && !block.includes('userId');
      const isListUsers = model === 'user' && block.includes('findMany');
      const acceptable = hasUserId || (isAdminOnly && (isGlobalContent || isListUsers || model === 'usageLog'));

      findings.push({
        file: relPath,
        line: lineNum,
        model,
        method: m[1],
        snippet: snippet.slice(0, 400),
        hasUserId: acceptable,
        note: acceptable ? (isAdminOnly ? 'OK (admin/usage)' : 'OK (userId)') : 'REVISAR: sin filtro por usuario',
      });
    }
  }

  // user.findMany sin userId (admin listUsers)
  const userFindMany = /prisma\.user\.findMany\s*\(/g;
  let um: RegExpExecArray | null;
  while ((um = userFindMany.exec(content)) !== null) {
    const idx = content.indexOf(um[0]);
    const lineNum = content.slice(0, idx).split('\n').length;
    const relPath2 = path.relative(path.join(__dirname, '..'), filePath);
    if (!findings.some((f) => f.file === relPath2 && f.line === lineNum && f.model === 'user')) {
      findings.push({
        file: relPath2,
        line: lineNum,
        model: 'user',
        method: 'findMany',
        snippet: lines[lineNum - 1],
        hasUserId: relPath2.includes('admin'),
        note: relPath2.includes('admin') ? 'OK (admin listUsers)' : 'REVISAR: user.findMany sin userId',
      });
    }
  }

  return findings;
}

function main() {
  const allFindings: Finding[] = [];
  for (const file of walkTs(API_SRC)) {
    allFindings.push(...auditFile(file));
  }

  const problematic = allFindings.filter((f) => !f.hasUserId);
  const ok = allFindings.filter((f) => f.hasUserId);

  console.log('=== Auditoría de aislamiento por usuario (Prisma) ===\n');
  console.log('Total llamadas a modelos con datos de usuario:', allFindings.length);
  console.log('Cumplen aislamiento (userId o excepción documentada):', ok.length);
  console.log('Requieren revisión:', problematic.length, '\n');

  if (problematic.length > 0) {
    console.log('--- REQUIEREN REVISIÓN ---');
    for (const f of problematic) {
      console.log(`${f.file}:${f.line}  prisma.${f.model}.${f.method}  → ${f.note ?? 'sin userId'}`);
    }
    console.log('');
  }

  console.log('--- Todas las llamadas (referencia) ---');
  for (const f of allFindings) {
    console.log(`${f.hasUserId ? '[OK]' : '[??]'} ${f.file}:${f.line}  prisma.${f.model}.${f.method}  ${f.note ?? ''}`);
  }

  process.exit(problematic.length > 0 ? 1 : 0);
}

main();
