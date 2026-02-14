import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { getApiBaseUrl, getCanonicalHost } from '@/lib/env';

const ADMIN_COOKIE = 'pr4y_admin_token';
const GATE_COOKIE = 'pr4y_admin_gate';

function getAdminSecretKey(): string {
  const v = process.env.ADMIN_SECRET_KEY;
  return typeof v === 'string' ? v.trim() : '';
}

async function validateAdminToken(token: string): Promise<boolean> {
  const apiBase = getApiBaseUrl();
  if (!apiBase) return false;
  try {
    const res = await fetch(`${apiBase}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return false;
    const body = await res.json();
    const role = body?.role;
    return role === 'admin' || role === 'super_admin';
  } catch {
    return false;
  }
}

export async function middleware(request: NextRequest) {
  const url = request.nextUrl;
  const { pathname } = url;

  // Redirección canónica: solo *.vercel.app → host canónico (NEXT_PUBLIC_CANONICAL_HOST). Opcional.
  const canonicalHost = getCanonicalHost();
  const host = request.headers.get('host') || '';
  if (canonicalHost && host.endsWith('vercel.app')) {
    const canonical = new URL(`https://${canonicalHost}${pathname}${url.search}`);
    return NextResponse.redirect(canonical, 308);
  }

  if (!pathname.startsWith('/admin')) {
    return NextResponse.next();
  }

  // Puerta de acceso: si está definido ADMIN_SECRET_KEY, exige cookie pr4y_admin_gate
  if (getAdminSecretKey()) {
    if (pathname === '/admin/gate') {
      return NextResponse.next();
    }
    const gateCookie = request.cookies.get(GATE_COOKIE)?.value;
    if (gateCookie !== getAdminSecretKey()) {
      const gateUrl = new URL('/admin/gate', request.url);
      return NextResponse.redirect(gateUrl);
    }
  }

  if (pathname === '/admin/login' || pathname === '/admin/gate') {
    return NextResponse.next();
  }

  const token = request.cookies.get(ADMIN_COOKIE)?.value;
  if (!token) {
    const login = new URL('/admin/login', request.url);
    return NextResponse.redirect(login);
  }

  const isAdmin = await validateAdminToken(token);
  if (!isAdmin) {
    const res = NextResponse.redirect(new URL('/', request.url));
    res.cookies.delete(ADMIN_COOKIE);
    return res;
  }

  return NextResponse.next();
}

export const config = {
  // Incluir todas las rutas para redirección canónica vercel.app → pr4y.cl
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
};
