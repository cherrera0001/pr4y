/**
 * Cliente unificado para la API de PR4Y.
 * Base URL desde NEXT_PUBLIC_API_URL o NEXT_PUBLIC_API_BASE_URL (Vercel). Zero hardcoding.
 */

import { getApiBaseUrl } from './env';

export const apiBaseUrl = getApiBaseUrl();

export interface ApiError {
  error: { code: string; message: string; details?: unknown };
}

function isApiError(body: unknown): body is ApiError {
  return typeof body === 'object' && body !== null && 'error' in body;
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit & { token?: string } = {}
): Promise<T> {
  const { token, ...init } = options;
  const url = `${apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`;
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const res = await fetch(url, { ...init, headers });
  const body = await res.json().catch(() => ({}));

  if (!res.ok) {
    if (isApiError(body)) throw new Error(body.error.message || res.statusText);
    throw new Error(res.statusText || 'Request failed');
  }
  return body as T;
}

// Auth
export interface AuthUser {
  id: string;
  email: string;
  role: string;
  createdAt: string;
}
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUser;
}
export const auth = {
  login: (email: string, password: string) =>
    apiRequest<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  me: (token: string) =>
    apiRequest<{ id: string; email: string; role: string }>('/auth/me', { token }),
};

// Admin (requieren token con role admin)
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
export interface AdminStats {
  totalUsers: number;
  totalRecords: number;
  totalBlobBytes: string;
  syncsToday: number;
  bytesPushedToday: string;
  bytesPulledToday: string;
  byDay: Array<{
    day: string;
    usersActive: number;
    bytesPushed: string;
    bytesPulled: string;
  }>;
}

/** Pulso de la app: DAU y volumen de sincronización (sin contenido de usuario). */
export interface AdminStatsDetail {
  dau: { today: number; byDay: Array<{ day: string; usersActive: number }> };
  syncVolume: {
    syncsToday: number;
    bytesPushedToday: string;
    bytesPulledToday: string;
    byDay: Array<{ day: string; bytesPushed: string; bytesPulled: string }>;
  };
  totals: { totalUsers: number; totalRecords: number; totalBlobBytes: string };
}

export const admin = {
  users: (token: string) => apiRequest<AdminUserRow[]>('/admin/users', { token }),
  stats: (token: string, days = 7) =>
    apiRequest<AdminStats>(`/admin/stats?days=${days}`, { token }),
  statsDetail: (token: string, days = 7) =>
    apiRequest<AdminStatsDetail>(`/admin/stats/detail?days=${days}`, { token }),
};
