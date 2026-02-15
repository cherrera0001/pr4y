/**
 * Verificación post-migración: imprime en stdout el número de tablas en public.
 * Uso: desde apps/api, tras `npx prisma migrate deploy`, para validar en logs de Railway:
 *   node scripts/verify-db.mjs
 * Requiere DATABASE_URL (inyectada por Railway en producción o en .env en local).
 */
import { config } from 'dotenv';
import { fileURLToPath } from 'url';
import { join, dirname } from 'path';
import pg from 'pg';

const __dirname = dirname(fileURLToPath(import.meta.url));
config({ path: join(__dirname, '..', '.env') });

const DATABASE_URL = process.env.DATABASE_URL;

async function main() {
  if (!DATABASE_URL?.trim()) {
    console.error('[PR4Y] verify-db: DATABASE_URL no definida. No se puede verificar la base de datos.');
    process.exit(1);
  }

  const pool = new pg.Pool({ connectionString: DATABASE_URL });
  try {
    const result = await pool.query(
      "SELECT count(*) AS count FROM information_schema.tables WHERE table_schema = 'public'"
    );
    const count = result.rows[0]?.count ?? '0';
    console.log('[PR4Y] Post-migración: public tables count =', String(count));
  } finally {
    await pool.end();
  }
}

main().catch((err) => {
  console.error('[PR4Y] verify-db error:', err.message);
  process.exit(1);
});
