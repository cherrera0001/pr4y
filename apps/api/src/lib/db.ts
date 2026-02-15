import 'dotenv/config';
import { PrismaClient, Prisma } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

const globalForPrisma = globalThis as unknown as { prisma: PrismaClient };

/**
 * URL de Postgres. Solo se usa DATABASE_URL para evitar conexiones por endpoint público.
 * En Railway: Variables del servicio API → DATABASE_URL = ${{Postgres.DATABASE_URL}}
 * (referencia al servicio Postgres; Railway resuelve la URL por red privada interna, sin egress).
 * No usar DATABASE_PUBLIC_URL ni RAILWAY_TCP_PROXY_DOMAIN: generan egress y latencia.
 */
function getConnectionString(): string | undefined {
  const raw = process.env.DATABASE_URL;
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
        'PR4Y: Falta DATABASE_URL. En Railway → servicio API → Variables: DATABASE_URL = ${{Postgres.DATABASE_URL}} (red privada). No uses DATABASE_PUBLIC_URL.'
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
