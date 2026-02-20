/**
 * Restringe admin: solo los correos en ADMIN_EMAILS tienen super_admin; el resto pasa a user.
 * Uso: node scripts/set-admin-users.mjs  (desde apps/api, con DATABASE_URL en .env)
 * O en Railway: railway run --service <api> node scripts/set-admin-users.mjs
 */
import { config } from 'dotenv';
import { fileURLToPath } from 'url';
import { join, dirname } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));
config({ path: join(__dirname, '..', '.env') });

import pkg from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

const { PrismaClient } = pkg;

const ADMIN_EMAILS = [
  'herrera.jara.cristobal@gmail.com',
  'crherrera@c4a.cl',
].map((e) => e.toLowerCase());

const ROLE = 'super_admin';

async function main() {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString?.trim()) {
    console.error('Falta DATABASE_URL. Añádela a apps/api/.env (o usa Railway: railway run ...)');
    process.exit(1);
  }

  const pool = new Pool({ connectionString });
  const adapter = new PrismaPg(pool);
  const prisma = new PrismaClient({ adapter });

  // 1) Quitar admin/super_admin a todos los que no están en la allowlist
  const admins = await prisma.user.findMany({
    where: { role: { in: ['admin', 'super_admin'] } },
    select: { id: true, email: true },
  });
  for (const u of admins) {
    const emailLower = (u.email || '').toLowerCase();
    if (!ADMIN_EMAILS.includes(emailLower)) {
      await prisma.user.update({ where: { id: u.id }, data: { role: 'user' } });
      console.log('Revoked admin:', u.email, '-> user');
    }
  }

  // 2) Asignar super_admin a las cuentas permitidas
  for (const email of ADMIN_EMAILS) {
    const user = await prisma.user.findUnique({ where: { email } });
    if (user) {
      await prisma.user.update({
        where: { id: user.id },
        data: { role: ROLE },
      });
      console.log('OK:', email, '->', ROLE);
    } else {
      console.log('Skip (no existe):', email);
    }
  }

  await prisma.$disconnect();
  await pool.end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
