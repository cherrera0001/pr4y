/**
 * API proxy: /api/v1/* → backend API.
 *
 * Hides the Railway API URL from the client bundle (VULN-001).
 * The backend URL is only known server-side via NEXT_PUBLIC_API_URL.
 */
import { NextRequest, NextResponse } from 'next/server';

function getBackendBase(): string {
  const raw =
    process.env.NEXT_PUBLIC_API_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? '';
  let url = (typeof raw === 'string' ? raw.trim() : '').replace(/\/$/, '');
  if (!url) return '';
  if (!/^https?:\/\//i.test(url)) url = `https://${url}`;
  if (!/\/v1$/i.test(url)) url = `${url}/v1`;
  return url;
}

const PROXY_TIMEOUT_MS = 30_000;

// Headers to strip from proxied responses
const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'transfer-encoding',
  'te',
  'trailer',
  'upgrade',
]);

async function proxyRequest(req: NextRequest, params: { path: string[] }) {
  const backendBase = getBackendBase();
  if (!backendBase) {
    return NextResponse.json(
      { error: { code: 'config_error', message: 'API not configured' } },
      { status: 502 }
    );
  }

  const subpath = params.path.join('/');
  const search = req.nextUrl.search;
  const targetUrl = `${backendBase}/${subpath}${search}`;

  // Forward headers (strip host, add forwarded-for)
  const headers = new Headers();
  req.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (lower === 'host' || lower === 'connection') return;
    headers.set(key, value);
  });
  headers.set('X-Forwarded-For', req.headers.get('x-forwarded-for') ?? req.ip ?? '');

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), PROXY_TIMEOUT_MS);

  try {
    const upstream = await fetch(targetUrl, {
      method: req.method,
      headers,
      body: req.method !== 'GET' && req.method !== 'HEAD' ? req.body : undefined,
      signal: controller.signal,
      // @ts-expect-error -- Next.js duplex needed for streaming body
      duplex: 'half',
    });

    // Build response headers
    const resHeaders = new Headers();
    upstream.headers.forEach((value, key) => {
      if (!HOP_BY_HOP.has(key.toLowerCase())) {
        resHeaders.set(key, value);
      }
    });

    return new NextResponse(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: resHeaders,
    });
  } catch (err) {
    const isTimeout = err instanceof DOMException && err.name === 'AbortError';
    return NextResponse.json(
      {
        error: {
          code: isTimeout ? 'gateway_timeout' : 'gateway_error',
          message: isTimeout ? 'Backend timeout' : 'Backend unreachable',
        },
      },
      { status: isTimeout ? 504 : 502 }
    );
  } finally {
    clearTimeout(timeout);
  }
}

export async function GET(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return proxyRequest(req, await ctx.params);
}
export async function POST(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return proxyRequest(req, await ctx.params);
}
export async function PUT(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return proxyRequest(req, await ctx.params);
}
export async function PATCH(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return proxyRequest(req, await ctx.params);
}
export async function DELETE(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return proxyRequest(req, await ctx.params);
}
