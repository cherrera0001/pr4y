import * as argon2 from 'argon2';
import crypto from 'crypto';
import { OAuth2Client } from 'google-auth-library';
import { prisma } from '../lib/db';

/**
 * OAuth2 / OIDC: el backend acepta id_tokens con audience Web (desde la web) o Android (desde la app).
 * - Web: consume GOOGLE_WEB_CLIENT_ID (NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID en Vercel).
 * - Android: consume GOOGLE_ANDROID_CLIENT_ID (la app usa ese como serverClientId).
 */
function getValidationAudiences(): string[] {
  const web = process.env.GOOGLE_WEB_CLIENT_ID?.trim();
  const android = process.env.GOOGLE_ANDROID_CLIENT_ID?.trim();
  return [web, android].filter((id): id is string => typeof id === 'string' && id.length > 0);
}

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
  signAccess: (payload: { sub: string; email: string; role: string }) => string
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
  const accessToken = signAccess({ sub: user.id, email: user.email, role: user.role });
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
    user: { id: user.id, email: user.email, role: user.role, createdAt: user.createdAt.toISOString() },
  };
}

export async function loginWithGoogle(
  idToken: string,
  signAccess: (payload: { sub: string; email: string; role: string }) => string
): Promise<
  | { ok: true; accessToken: string; refreshToken: string; expiresIn: number; user: { id: string; email: string; role: string; createdAt: string } }
  | { ok: false; invalidToken?: boolean; userBanned?: boolean; verifyError?: string }
> {
  const audiences = getValidationAudiences();
  if (audiences.length === 0) {
    return { ok: false as const, invalidToken: true, verifyError: 'GOOGLE_WEB_CLIENT_ID or GOOGLE_ANDROID_CLIENT_ID not set' };
  }
  const client = new OAuth2Client();
  let payload: { email?: string; email_verified?: boolean; sub?: string };
  try {
    const ticket = await client.verifyIdToken({ idToken, audience: audiences });
    payload = ticket.getPayload() ?? {};
  } catch (err) {
    const verifyError = err instanceof Error ? err.message : String(err);
    return { ok: false as const, invalidToken: true, verifyError };
  }
  const email = payload.email?.trim().toLowerCase();
  const googleId = payload.sub;
  if (!email || !googleId || !payload.email_verified) {
    return { ok: false as const, invalidToken: true, verifyError: 'Missing email/sub/email_verified in token payload' };
  }

  let user = await prisma.user.findFirst({
    where: { OR: [{ googleId }, { email }] },
  });
  if (!user) {
    user = await prisma.user.create({
      data: {
        email,
        googleId,
        passwordHash: null,
        role: 'user',
      },
    });
  } else {
    if (user.status === 'banned') {
      return { ok: false as const, userBanned: true };
    }
    await prisma.user.update({
      where: { id: user.id },
      data: {
        ...(user.googleId ? {} : { googleId }),
        lastLoginAt: new Date(),
      },
    });
  }

  const accessToken = signAccess({ sub: user.id, email: user.email, role: user.role });
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
    user: { id: user.id, email: user.email, role: user.role, createdAt: user.createdAt.toISOString() },
  };
}

export async function login(
  email: string,
  password: string,
  signAccess: (payload: { sub: string; email: string; role: string }) => string
) {
  const user = await prisma.user.findUnique({ where: { email: email.toLowerCase() } });
  if (!user) {
    return { ok: false as const, invalidCredentials: true };
  }
  if (user.status === 'banned') {
    return { ok: false as const, invalidCredentials: true };
  }
  if (!user.passwordHash) {
    return { ok: false as const, invalidCredentials: true }; // usuario solo OAuth
  }
  const valid = await argon2.verify(user.passwordHash, password);
  if (!valid) {
    return { ok: false as const, invalidCredentials: true };
  }
  await prisma.user.update({
    where: { id: user.id },
    data: { lastLoginAt: new Date() },
  });
  const accessToken = signAccess({ sub: user.id, email: user.email, role: user.role });
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
    user: { id: user.id, email: user.email, role: user.role, createdAt: user.createdAt.toISOString() },
  };
}

export async function refresh(
  refreshToken: string,
  signAccess: (payload: { sub: string; email: string; role: string }) => string
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
  const accessToken = signAccess({ sub: user.id, email: user.email, role: user.role });
  const newRefreshToken = generateRefreshToken();
  const newRefreshHash = hashRefreshToken(newRefreshToken);
  await prisma.refreshToken.create({
    data: {
      userId: user.id,
      tokenHash: newRefreshHash,
      expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS),
    },
  });
  await prisma.user.update({
    where: { id: user.id },
    data: { lastLoginAt: new Date() },
  });
  return {
    ok: true as const,
    accessToken,
    refreshToken: newRefreshToken,
    expiresIn: 900,
    user: { id: user.id, email: user.email, role: user.role, createdAt: user.createdAt.toISOString() },
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
