import { prisma } from '../lib/db';

/** Lista de usuarios para backoffice: sin contenido sensible, solo metadatos para soporte. */
export interface AdminUserRow {
  id: string;
  email: string;
  role: string;
  createdAt: string;
  lastLoginAt: string | null;
  hasDek: boolean;
  recordCount: number;
}

export async function listUsers(): Promise<AdminUserRow[]> {
  const users = await prisma.user.findMany({
    orderBy: { createdAt: 'desc' },
    select: {
      id: true,
      email: true,
      role: true,
      createdAt: true,
      lastLoginAt: true,
      wrappedDek: { select: { id: true } },
      _count: { select: { records: true } },
    },
  });
  return users.map((u) => ({
    id: u.id,
    email: u.email,
    role: u.role,
    createdAt: u.createdAt.toISOString(),
    lastLoginAt: u.lastLoginAt?.toISOString() ?? null,
    hasDek: !!u.wrappedDek,
    recordCount: u._count.records,
  }));
}

export interface AdminMetrics {
  totalUsers: number;
  dau: number;
  wau: number;
  newUsersLast7Days: number;
  newUsersLast30Days: number;
  totalRecords: number;
}

export async function getMetrics(): Promise<AdminMetrics> {
  const now = new Date();
  const dayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

  const [totalUsers, dau, wau, newUsersLast7Days, newUsersLast30Days, totalRecords] = await Promise.all([
    prisma.user.count(),
    prisma.user.count({ where: { lastLoginAt: { gte: dayAgo } } }),
    prisma.user.count({ where: { lastLoginAt: { gte: weekAgo } } }),
    prisma.user.count({ where: { createdAt: { gte: weekAgo } } }),
    prisma.user.count({ where: { createdAt: { gte: thirtyDaysAgo } } }),
    prisma.record.count(),
  ]);

  return {
    totalUsers,
    dau,
    wau,
    newUsersLast7Days,
    newUsersLast30Days,
    totalRecords,
  };
}
