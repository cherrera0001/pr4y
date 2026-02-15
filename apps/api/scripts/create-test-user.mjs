/**
 * Crea o actualiza un usuario de prueba en la base de datos.
 * Uso: npm run create-test-user  (desde apps/api)
 * Requiere DATABASE_URL en apps/api/.env
 */
import { config } from 'dotenv';
import { fileURLToPath } from 'url';
import { join, dirname } from 'path';
import argon2 from 'argon2';

const __dirname = dirname(fileURLToPath(import.meta.url));
config({ path: join(__dirname, '..', '.env') });
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

const TEST_EMAIL = 'crherrera@c4a.cl';
const TEST_PASSWORD = 'pr4y.2026!';

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
  if (existing) {
    await prisma.user.update({
      where: { id: existing.id },
      data: { passwordHash },
    });
    console.log('Usuario actualizado:', email, '(password actualizada)');
  } else {
    await prisma.user.create({
      data: { email, passwordHash },
    });
    console.log('Usuario creado:', email);
  }

  await prisma.$disconnect();
  await pool.end();
}

main().catch((e) => {
  if (e?.code === 'P1000') {
    console.error('\nNo se pudo conectar a la base de datos. Revisa:');
    console.error('  - DATABASE_URL en apps/api/.env (usuario, contraseña, host, puerto).');
    console.error('  - Que Postgres esté en marcha y acepte conexiones.\n');
  }
  console.error(e);
  process.exit(1);
});
