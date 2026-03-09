/**
 * Client-side auth utilities for user sessions.
 * Tokens are stored in memory + localStorage for persistence across reloads.
 * Never stores passphrase or DEK — only JWT tokens.
 */

const TOKEN_KEY = 'pr4y_access_token';
const REFRESH_KEY = 'pr4y_refresh_token';
const USER_KEY = 'pr4y_user';

export interface AuthUser {
  id: string;
  email: string;
  role: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUser;
}

let memoryToken: string | null = null;

export function getAccessToken(): string | null {
  if (memoryToken) return memoryToken;
  if (typeof window === 'undefined') return null;
  const stored = localStorage.getItem(TOKEN_KEY);
  if (stored) memoryToken = stored;
  return stored;
}

export function getRefreshToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(REFRESH_KEY);
}

export function getStoredUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function setAuthTokens(tokens: AuthTokens): void {
  memoryToken = tokens.accessToken;
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(tokens.user));
}

export function clearAuth(): void {
  memoryToken = null;
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}

export function isLoggedIn(): boolean {
  return !!getAccessToken();
}

/**
 * Authenticated fetch wrapper. Uses proxy route (/api/v1/*) to avoid exposing backend URL.
 * Automatically injects Authorization header.
 */
export async function authFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const token = getAccessToken();
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json');
  }

  const url = `/api/v1${path.startsWith('/') ? path : `/${path}`}`;
  const res = await fetch(url, { ...options, headers });

  // If 401, try refresh
  if (res.status === 401) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      const newHeaders = new Headers(options.headers);
      newHeaders.set('Authorization', `Bearer ${getAccessToken()}`);
      if (!newHeaders.has('Content-Type') && options.body) {
        newHeaders.set('Content-Type', 'application/json');
      }
      return fetch(url, { ...options, headers: newHeaders });
    }
    clearAuth();
  }

  return res;
}

async function tryRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;

  try {
    const res = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = await res.json();
    if (data.accessToken) {
      setAuthTokens(data);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}
