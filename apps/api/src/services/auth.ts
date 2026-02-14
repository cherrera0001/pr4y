import * as argon2 from 'argon2';
import crypto from 'crypto';
import { prisma } from '../lib/db';

const ACCESS_TOKEN_TTL = '15m';
const REFRESH_TOKEN_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

function hashRefreshToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function generateRefreshToken(): string {
  return crypto.randomBytes(32).toString('base64url');
}

export async function register(
  email: string,
  password: string,
  signAccess: (payload: { sub: string; email: string }) => string
) {
  const existing = await prisma.user.findUnique({ where: { email: email.toLowerCase() } });
  if (existing) {
    return { ok: false as const, conflict: true };
  }
  const passwordHash = await argon2.hash(password, { type: argon2.argon2id });
  const user = await prisma.user.create({
    data: {
      email: email.toLowerCase(),
      passwordHash,
    },
  });
  const accessToken = signAccess({ sub: user.id, email: user.email });
  const refreshToken = generateRefreshToken();
  const refreshHash = hashRefreshToken(refreshToken);
  await prisma.refreshToken.create({
    data: {
      userId: user.id,
      tokenHash: refreshHash,
      expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS),
    },
  });
  return {
    ok: true as const,
    accessToken,
    refreshToken,
    expiresIn: 900,
    user: { id: user.id, email: user.email, createdAt: user.createdAt.toISOString() },
  };
}

export async function login(
  email: string,
  password: string,
  signAccess: (payload: { sub: string; email: string }) => string
) {
  const user = await prisma.user.findUnique({ where: { email: email.toLowerCase() } });
  if (!user) {
    return { ok: false as const, invalidCredentials: true };
  }
  const valid = await argon2.verify(user.passwordHash, password);
  if (!valid) {
    return { ok: false as const, invalidCredentials: true };
  }
  const accessToken = signAccess({ sub: user.id, email: user.email });
  const refreshToken = generateRefreshToken();
  const refreshHash = hashRefreshToken(refreshToken);
  await prisma.refreshToken.create({
    data: {
      userId: user.id,
      tokenHash: refreshHash,
      expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS),
    },
  });
  return {
    ok: true as const,
    accessToken,
    refreshToken,
    expiresIn: 900,
    user: { id: user.id, email: user.email, createdAt: user.createdAt.toISOString() },
  };
}

export async function refresh(
  refreshToken: string,
  signAccess: (payload: { sub: string; email: string }) => string
) {
  const hash = hashRefreshToken(refreshToken);
  const row = await prisma.refreshToken.findFirst({
    where: { tokenHash: hash, revokedAt: null },
    include: { user: true },
  });
  if (!row || row.expiresAt < new Date()) {
    return { ok: false as const, invalidToken: true };
  }
  await prisma.refreshToken.update({
    where: { id: row.id },
    data: { revokedAt: new Date() },
  });
  const user = row.user;
  const accessToken = signAccess({ sub: user.id, email: user.email });
  const newRefreshToken = generateRefreshToken();
  const newRefreshHash = hashRefreshToken(newRefreshToken);
  await prisma.refreshToken.create({
    data: {
      userId: user.id,
      tokenHash: newRefreshHash,
      expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS),
    },
  });
  return {
    ok: true as const,
    accessToken,
    refreshToken: newRefreshToken,
    expiresIn: 900,
    user: { id: user.id, email: user.email, createdAt: user.createdAt.toISOString() },
  };
}

export async function logout(refreshToken: string) {
  const hash = hashRefreshToken(refreshToken);
  await prisma.refreshToken.updateMany({
    where: { tokenHash: hash },
    data: { revokedAt: new Date() },
  });
  return { ok: true as const };
}

export function getAccessTokenTtl(): string {
  return ACCESS_TOKEN_TTL;
}

export function getRefreshTokenTtlMs(): number {
  return REFRESH_TOKEN_TTL_MS;
}
