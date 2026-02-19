import { NextRequest, NextResponse } from 'next/server';
import { getApiBaseUrl } from '@/lib/env';

const ADMIN_COOKIE = 'pr4y_admin_token';

/** Evita caché en edge/serverless para que el callback de Google siempre llegue al handler. */
export const dynamic = 'force-dynamic';

function isAdmin(role: string) {
  return role === 'admin' || role === 'super_admin';
}

function loginPageUrl(request: NextRequest, error?: string) {
  const origin = new URL(request.url).origin;
  const path = error ? `/admin/login?error=${encodeURIComponent(error)}` : '/admin/login';
  return `${origin}${path}`;
}

const COOP_HEADER = { key: 'Cross-Origin-Opener-Policy', value: 'same-origin-allow-popups' } as const;

function redirectToLogin(request: NextRequest, error?: string, status = 302) {
  const res = NextResponse.redirect(loginPageUrl(request, error), status);
  res.headers.set(COOP_HEADER.key, COOP_HEADER.value);
  return res;
}

/**
 * GET /api/admin/login — Redirige al formulario de login (evita "invalid response" si llega GET).
 */
export async function GET(request: NextRequest) {
  return redirectToLogin(request);
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
      return redirectToLogin(request, 'config');
    }
    let authRes: Response;
    let meRes: Response;
    try {
      authRes = await fetch(`${apiBase}/auth/google`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken: credential }),
        cache: 'no-store',
      });
    } catch (fetchErr) {
      try {
        const host = new URL(apiBase).host;
        console.error('[admin/login] API unreachable (auth/google) host=%s error=%s', host, fetchErr instanceof Error ? fetchErr.message : String(fetchErr));
      } catch {
        console.error('[admin/login] API unreachable (auth/google) apiBase invalid error=%s', fetchErr instanceof Error ? fetchErr.message : String(fetchErr));
      }
      return redirectToLogin(request, 'api_unreachable');
    }
    const authData = await authRes.json().catch(() => ({}));
    if (!authRes.ok) {
      const msg = authData?.error?.message ?? 'Error al validar con Google';
      return redirectToLogin(request, msg);
    }
    const token = authData?.accessToken;
    if (!token) {
      return redirectToLogin(request, 'invalid_response');
    }
    try {
      meRes = await fetch(`${apiBase}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
        cache: 'no-store',
      });
    } catch (fetchErr) {
      try {
        const host = new URL(apiBase).host;
        console.error('[admin/login] API unreachable (auth/me) host=%s error=%s', host, fetchErr instanceof Error ? fetchErr.message : String(fetchErr));
      } catch {
        console.error('[admin/login] API unreachable (auth/me) apiBase invalid error=%s', fetchErr instanceof Error ? fetchErr.message : String(fetchErr));
      }
      return redirectToLogin(request, 'api_unreachable');
    }
    if (!meRes.ok) {
      return redirectToLogin(request, 'invalid_token');
    }
    const user = await meRes.json();
    if (!isAdmin(user?.role)) {
      return redirectToLogin(request, 'admin_required');
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
    // Flujo Google: COOP same-origin-allow-popups evita bloquear postMessage al volver al panel
    redirect.headers.set(COOP_HEADER.key, COOP_HEADER.value);
    return redirect;
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    const name = err instanceof Error ? err.name : '';
    const isDev = process.env.NODE_ENV !== 'production';
    if (isDev) {
      const code = message.length > 60 ? 'server' : message.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 60);
      return redirectToLogin(request, code);
    }
    // En producción: log completo para Vercel (causa típica: API inalcanzable, timeout o NEXT_PUBLIC_API_URL sin /v1)
    console.error('[admin/login] POST error:', name, message);
    if (err instanceof Error && err.cause) {
      console.error('[admin/login] cause:', err.cause);
    }
    return redirectToLogin(request, 'server');
  }
}
