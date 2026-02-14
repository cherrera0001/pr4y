import 'dotenv/config';
import { PrismaClient, Prisma } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

const globalForPrisma = globalThis as unknown as { prisma: PrismaClient };

/**
 * URL de Postgres. Toma la variable de entorno DATABASE_URL (dinámica).
 * En Railway: Variables → DATABASE_URL = ${{Postgres.DATABASE_URL}} para referenciar el servicio Postgres.
 * La conexión real ocurre en el primer uso; el servidor hace un health check al arranque (prisma.$queryRaw)
 * y debe reportar en log cualquier fallo de conexión (no excepciones silenciosas).
 */
function getConnectionString(): string | undefined {
  const raw =
    process.env.DATABASE_URL ??
    process.env.POSTGRES_PRIVATE_URL ??
    process.env.DATABASE_PUBLIC_URL ??
    process.env.POSTGRES_URL ??
    process.env.DIRECT_URL;
  return typeof raw === 'string' && raw.trim().length > 0 ? raw.trim() : undefined;
}

function createPrisma(): PrismaClient {
  const connectionString = getConnectionString();
  const logOptions: Prisma.LogLevel[] =
    process.env.NODE_ENV === 'development' ? ['error', 'warn'] : ['error'];

  // Prisma 7 con engine "client" exige adapter o accelerateUrl; sin URL no podemos crear el cliente.
  if (!connectionString) {
    if (process.env.NODE_ENV === 'production') {
      throw new Error(
        'PR4Y: Falta DATABASE_URL. En Railway → servicio @pr4y/api → Variables: crea una variable de nombre exacto "DATABASE_URL" y pega la URL de Postgres (o usa referencia al servicio Postgres).'
      );
    }
    throw new Error(
      'DATABASE_URL es obligatoria. Añádela a apps/api/.env para desarrollo local.'
    );
  }

  const pool = new Pool({ connectionString });
  const adapter = new PrismaPg(pool);
  return new PrismaClient({ adapter, log: logOptions });
}

export const prisma = globalForPrisma.prisma ?? createPrisma();

if (process.env.NODE_ENV !== 'production') globalForPrisma.prisma = prisma;
