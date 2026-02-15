import { prisma } from '../lib/db';

/** Lista de usuarios para backoffice: sin contenido sensible, solo metadatos para soporte. */
export interface AdminUserRow {
  id: string;
  email: string;
  role: string;
  status: string;
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
      status: true,
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
    status: u.status,
    createdAt: u.createdAt.toISOString(),
    lastLoginAt: u.lastLoginAt?.toISOString() ?? null,
    hasDek: !!u.wrappedDek,
    recordCount: u._count.records,
  }));
}

export async function updateUser(
  id: string,
  data: { role?: string; status?: string }
): Promise<AdminUserRow | null> {
  const allowedRoles = ['user', 'admin', 'super_admin'];
  const allowedStatuses = ['active', 'banned'];
  const update: { role?: string; status?: string } = {};
  if (data.role != null && allowedRoles.includes(data.role)) update.role = data.role;
  if (data.status != null && allowedStatuses.includes(data.status)) update.status = data.status;
  if (Object.keys(update).length === 0) return null;

  const user = await prisma.user.update({
    where: { id },
    data: update,
    select: {
      id: true,
      email: true,
      role: true,
      status: true,
      createdAt: true,
      lastLoginAt: true,
      wrappedDek: { select: { id: true } },
      _count: { select: { records: true } },
    },
  });
  return {
    id: user.id,
    email: user.email,
    role: user.role,
    status: user.status,
    createdAt: user.createdAt.toISOString(),
    lastLoginAt: user.lastLoginAt?.toISOString() ?? null,
    hasDek: !!user.wrappedDek,
    recordCount: user._count.records,
  };
}

/** Contenido global para panel admin (oraciones, avisos). */
export interface GlobalContentRow {
  id: string;
  type: string;
  title: string;
  body: string;
  published: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export async function listContent(type?: string): Promise<GlobalContentRow[]> {
  const items = await prisma.globalContent.findMany({
    where: type ? { type } : undefined,
    orderBy: [{ sortOrder: 'asc' }, { createdAt: 'desc' }],
  });
  return items.map((c) => ({
    id: c.id,
    type: c.type,
    title: c.title,
    body: c.body,
    published: c.published,
    sortOrder: c.sortOrder,
    createdAt: c.createdAt.toISOString(),
    updatedAt: c.updatedAt.toISOString(),
  }));
}

export async function createContent(data: {
  type: string;
  title: string;
  body: string;
  published?: boolean;
  sortOrder?: number;
}): Promise<GlobalContentRow> {
  const c = await prisma.globalContent.create({
    data: {
      type: data.type,
      title: data.title,
      body: data.body,
      published: data.published ?? false,
      sortOrder: data.sortOrder ?? 0,
    },
  });
  return {
    id: c.id,
    type: c.type,
    title: c.title,
    body: c.body,
    published: c.published,
    sortOrder: c.sortOrder,
    createdAt: c.createdAt.toISOString(),
    updatedAt: c.updatedAt.toISOString(),
  };
}

export async function updateContent(
  id: string,
  data: { type?: string; title?: string; body?: string; published?: boolean; sortOrder?: number }
): Promise<GlobalContentRow | null> {
  const c = await prisma.globalContent.update({
    where: { id },
    data: {
      ...(data.type != null && { type: data.type }),
      ...(data.title != null && { title: data.title }),
      ...(data.body != null && { body: data.body }),
      ...(data.published != null && { published: data.published }),
      ...(data.sortOrder != null && { sortOrder: data.sortOrder }),
    },
  });
  return {
    id: c.id,
    type: c.type,
    title: c.title,
    body: c.body,
    published: c.published,
    sortOrder: c.sortOrder,
    createdAt: c.createdAt.toISOString(),
    updatedAt: c.updatedAt.toISOString(),
  };
}

export async function deleteContent(id: string): Promise<boolean> {
  await prisma.globalContent.delete({ where: { id } });
  return true;
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
