import { NextRequest, NextResponse } from 'next/server';
import { getApiBaseUrl } from '@/lib/env';

const ADMIN_COOKIE = 'pr4y_admin_token';

export async function GET(request: NextRequest) {
  const token = request.cookies.get(ADMIN_COOKIE)?.value;
  if (!token) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const apiBase = getApiBaseUrl();
  if (!apiBase) {
    return NextResponse.json({ error: 'api not configured' }, { status: 503 });
  }
  const { searchParams } = request.nextUrl;
  const upstream = new URL(`${apiBase}/admin/users`);
  searchParams.forEach((v, k) => upstream.searchParams.set(k, v));
  const res = await fetch(upstream.toString(), {
    headers: { Authorization: `Bearer ${token}` },
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    return NextResponse.json(data, { status: res.status });
  }
  const totalCount = res.headers.get('X-Total-Count');
  const response = NextResponse.json(data);
  if (totalCount) response.headers.set('X-Total-Count', totalCount);
  return response;
}
