/**
 * Crea o actualiza un usuario de prueba en la base de datos.
 * Uso: npx ts-node scripts/create-test-user.ts
 * Requiere DATABASE_URL en apps/api/.env
 */
import 'dotenv/config';
import * as argon2 from 'argon2';
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

const TEST_EMAIL = process.env.TEST_EMAIL ?? 'crherrera@c4a.cl';
const TEST_PASSWORD = process.env.TEST_PASSWORD ?? 'pr4y.2026!';
const SUPER_ADMIN = process.env.SUPER_ADMIN === 'true';

async function main() {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString?.trim()) {
    console.error('Falta DATABASE_URL. Añádela a apps/api/.env');
    process.exit(1);
  }

  const pool = new Pool({ connectionString });
  const adapter = new PrismaPg(pool);
  const prisma = new PrismaClient({ adapter });

  const email = TEST_EMAIL.toLowerCase();
  const passwordHash = await argon2.hash(TEST_PASSWORD, { type: argon2.argon2id });

  const existing = await prisma.user.findUnique({ where: { email } });
  const role = SUPER_ADMIN ? 'super_admin' : 'user';
  if (existing) {
    await prisma.user.update({
      where: { id: existing.id },
      data: { passwordHash, role },
    });
    console.log('Usuario actualizado:', email, 'role:', role);
  } else {
    await prisma.user.create({
      data: { email, passwordHash, role },
    });
    console.log('Usuario creado:', email, 'role:', role);
  }

  await prisma.$disconnect();
  pool.end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
