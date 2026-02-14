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
  const { searchParams } = new URL(request.url);
  const type = searchParams.get('type') || '';
  const q = type ? `?type=${encodeURIComponent(type)}` : '';
  const res = await fetch(`${apiBase}/admin/content${q}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    return NextResponse.json(data, { status: res.status });
  }
  return NextResponse.json(data);
}

export async function POST(request: NextRequest) {
  const token = request.cookies.get(ADMIN_COOKIE)?.value;
  if (!token) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const body = await request.json().catch(() => ({}));
  const res = await fetch(`${apiBase}/admin/content`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    return NextResponse.json(data, { status: res.status });
  }
  return NextResponse.json(data, { status: 201 });
}
