import { NextRequest, NextResponse } from 'next/server';

const GATE_COOKIE = 'pr4y_admin_gate';

function getAdminSecretKey(): string {
  const v = process.env.ADMIN_SECRET_KEY;
  return typeof v === 'string' ? v.trim() : '';
}

/** POST: valida el token de administrador (ADMIN_SECRET_KEY) y establece la cookie de puerta. */
export async function POST(request: NextRequest) {
  const secretKey = getAdminSecretKey();
  if (!secretKey) {
    return NextResponse.json({ error: 'gate not configured' }, { status: 501 });
  }
  try {
    const body = await request.json();
    const token = typeof body?.token === 'string' ? body.token.trim() : '';
    if (!token) {
      return NextResponse.json({ error: 'token required' }, { status: 400 });
    }
    if (token !== secretKey) {
      return NextResponse.json({ error: 'invalid token' }, { status: 403 });
    }
    const res = NextResponse.json({ ok: true });
    res.cookies.set(GATE_COOKIE, secretKey, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 60 * 60 * 24 * 7, // 7 días
      path: '/',
    });
    return res;
  } catch {
    return NextResponse.json({ error: 'server error' }, { status: 500 });
  }
}
