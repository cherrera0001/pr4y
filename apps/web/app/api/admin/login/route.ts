import { NextRequest, NextResponse } from 'next/server';
import { getApiBaseUrl } from '@/lib/env';

const ADMIN_COOKIE = 'pr4y_admin_token';

function isAdmin(role: string) {
  return role === 'admin' || role === 'super_admin';
}

function loginPageUrl(request: NextRequest, error?: string) {
  const origin = new URL(request.url).origin;
  const path = error ? `/admin/login?error=${encodeURIComponent(error)}` : '/admin/login';
  return `${origin}${path}`;
}

/**
 * GET /api/admin/login — Redirige al formulario de login (evita "invalid response" si llega GET).
 */
export async function GET(request: NextRequest) {
  return NextResponse.redirect(loginPageUrl(request), 302);
}

/**
 * POST /api/admin/login — Recibe el redirect de Google (ux_mode: redirect).
 * Body: credential (id token), normalmente application/x-www-form-urlencoded.
 * Valida con la API, establece cookie y redirige a /admin.
 * Sin credential responde 200 para evitar 405.
 */
export async function POST(request: NextRequest) {
  try {
    let credential = '';
    const contentType = request.headers.get('content-type') ?? '';
    try {
      if (contentType.includes('application/x-www-form-urlencoded')) {
        const form = await request.formData();
        credential = (form.get('credential') as string) ?? '';
      } else if (contentType.includes('application/json')) {
        const body = await request.json();
        credential = typeof body?.credential === 'string' ? body.credential : '';
      } else {
        const text = await request.text();
        if (text) {
          try {
            const parsed = JSON.parse(text);
            credential = typeof parsed?.credential === 'string' ? parsed.credential : '';
          } catch {
            const params = new URLSearchParams(text);
            credential = params.get('credential') ?? '';
          }
        }
      }
    } catch {
      return NextResponse.json({ ok: true }, { status: 200 });
    }

    if (!credential) {
      return NextResponse.json({ ok: true }, { status: 200 });
    }

    const apiBase = getApiBaseUrl();
    if (!apiBase) {
      return NextResponse.redirect(loginPageUrl(request, 'config'), 302);
    }
    const authRes = await fetch(`${apiBase}/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken: credential }),
    });
    const authData = await authRes.json().catch(() => ({}));
    if (!authRes.ok) {
      const msg = authData?.error?.message ?? 'Error al validar con Google';
      return NextResponse.redirect(loginPageUrl(request, msg), 302);
    }
    const token = authData?.accessToken;
    if (!token) {
      return NextResponse.redirect(loginPageUrl(request, 'invalid_response'), 302);
    }
    const meRes = await fetch(`${apiBase}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!meRes.ok) {
      return NextResponse.redirect(loginPageUrl(request, 'invalid_token'), 302);
    }
    const user = await meRes.json();
    if (!isAdmin(user?.role)) {
      return NextResponse.redirect(loginPageUrl(request, 'admin_required'), 302);
    }
    const origin = new URL(request.url).origin;
    // 303 See Other: correcto tras POST; el navegador hace GET a /admin y envía la cookie en esa petición
    const redirect = NextResponse.redirect(`${origin}/admin`, 303);
    redirect.cookies.set(ADMIN_COOKIE, token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 60 * 60 * 24,
      path: '/',
    });
    redirect.headers.set('Cache-Control', 'no-store');
    return redirect;
  } catch (err) {
    return NextResponse.redirect(loginPageUrl(request, 'server'), 302);
  }
}
