import { NextRequest, NextResponse } from 'next/server';

const ADMIN_COOKIE = 'pr4y_admin_token';
const apiBase = process.env.NEXT_PUBLIC_API_URL || 'https://pr4yapi-production.up.railway.app/v1';

export async function GET(request: NextRequest) {
  const token = request.cookies.get(ADMIN_COOKIE)?.value;
  if (!token) {
    return NextResponse.json({ error: 'unauthorized' }, { status: 401 });
  }
  const { searchParams } = new URL(request.url);
  const days = searchParams.get('days') || '7';
  const res = await fetch(`${apiBase.replace(/\/$/, '')}/admin/stats?days=${days}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    return NextResponse.json(data, { status: res.status });
  }
  return NextResponse.json(data);
}
