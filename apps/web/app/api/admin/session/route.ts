import { NextRequest, NextResponse } from 'next/server';

const ADMIN_COOKIE = 'pr4y_admin_token';
const apiBase = process.env.NEXT_PUBLIC_API_URL || 'https://pr4yapi-production.up.railway.app/v1';

function isAdmin(role: string) {
  return role === 'admin' || role === 'super_admin';
}

/** POST: establece la sesión admin (cookie) si el token es de un admin. */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const token = typeof body?.token === 'string' ? body.token : '';
    if (!token) {
      return NextResponse.json({ error: 'token required' }, { status: 400 });
    }
    const me = await fetch(`${apiBase.replace(/\/$/, '')}/auth/me`, {
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
      maxAge: 60 * 15, // 15 min, igual que access token
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
