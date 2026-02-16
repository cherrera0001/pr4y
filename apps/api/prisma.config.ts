import 'dotenv/config';
import { defineConfig } from 'prisma/config';

// Fallback para prisma generate (no conecta a DB). Para migrate deploy/start, definir DATABASE_URL en .env.
const databaseUrl = process.env.DATABASE_URL || 'postgresql://localhost:5432/pr4y';

export default defineConfig({
  schema: 'prisma/schema.prisma',
  migrations: {
    path: 'prisma/migrations',
  },
  datasource: {
    url: databaseUrl,
  },
});
