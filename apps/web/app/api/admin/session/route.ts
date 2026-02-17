import { NextRequest, NextResponse } from 'next/server';
import { getApiBaseUrl } from '@/lib/env';

const ADMIN_COOKIE = 'pr4y_admin_token';

function isAdmin(role: string) {
  return role === 'admin' || role === 'super_admin';
}

/**
 * POST: establece la sesión admin (cookie) si el token es de un usuario con rol admin en la BD.
 * Quién es administrador se valida en la API (auth/me) según User.role en la base de datos; nada hardcodeado en la web.
 * La app móvil no usa esta ruta: cualquier usuario puede usar la app con su cuenta Google.
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const token = typeof body?.token === 'string' ? body.token : '';
    if (!token) {
      return NextResponse.json({ error: 'token required' }, { status: 400 });
    }
    const apiBase = getApiBaseUrl();
    if (!apiBase) {
      return NextResponse.json({ error: 'api not configured' }, { status: 503 });
    }
    const me = await fetch(`${apiBase}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!me.ok) {
      return NextResponse.json({ error: 'invalid token' }, { status: 401 });
    }
    const user = await me.json();
    if (!isAdmin(user?.role)) {
      return NextResponse.json({ error: 'admin required' }, { status: 403 });
    }
    const res = NextResponse.json({ ok: true });
    res.cookies.set(ADMIN_COOKIE, token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 60 * 60 * 24, // 24 h — persistencia de sesión al refrescar
      path: '/',
    });
    return res;
  } catch (e) {
    return NextResponse.json({ error: 'server error' }, { status: 500 });
  }
}

/** DELETE: cierra sesión admin (borra la cookie). */
export async function DELETE() {
  const res = NextResponse.json({ ok: true });
  res.cookies.set(ADMIN_COOKIE, '', { path: '/', maxAge: 0 });
  return res;
}
