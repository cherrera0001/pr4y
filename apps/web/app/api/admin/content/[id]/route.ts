import { NextRequest, NextResponse } from 'next/server';
import { getApiBaseUrl } from '@/lib/env';

const ADMIN_COOKIE = 'pr4y_admin_token';

export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const token = request.cookies.get(ADMIN_COOKIE)?.value;
  if (!token) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const apiBase = getApiBaseUrl();
  if (!apiBase) {
    return NextResponse.json({ error: 'api not configured' }, { status: 503 });
  }
  const { id } = await params;
  const body = await request.json().catch(() => ({}));
  const res = await fetch(`${apiBase}/admin/content/${encodeURIComponent(id)}`, {
    method: 'PATCH',
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
  return NextResponse.json(data);
}

export async function DELETE(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const token = _request.cookies.get(ADMIN_COOKIE)?.value;
  if (!token) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const apiBase = getApiBaseUrl();
  if (!apiBase) {
    return NextResponse.json({ error: 'api not configured' }, { status: 503 });
  }
  const { id } = await params;
  const res = await fetch(`${apiBase}/admin/content/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 204) {
    return new NextResponse(null, { status: 204 });
  }
  const data = await res.json().catch(() => ({}));
  return NextResponse.json(data, { status: res.status });
}
