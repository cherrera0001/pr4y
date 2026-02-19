import { prisma } from '../lib/db';

/**
 * Purga todos los datos del usuario en el servidor (derecho al olvido en el búnker).
 * Elimina: registros (y por cascade reminders/answers), wrapped DEK, usage logs.
 * No elimina la cuenta (User) para que el usuario pueda volver a iniciar sesión con búnker vacío.
 */
export async function purgeUserData(userId: string): Promise<{ recordsDeleted: number; usageLogsDeleted: number }> {
  const recordsDeleted = await prisma.record.deleteMany({ where: { userId } });
  await prisma.wrappedDek.deleteMany({ where: { userId } });
  const usageLogsDeleted = await prisma.usageLog.deleteMany({ where: { userId } });
  return {
    recordsDeleted: recordsDeleted.count,
    usageLogsDeleted: usageLogsDeleted.count,
  };
}
