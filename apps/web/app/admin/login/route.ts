import { NextResponse } from 'next/server';

/**
 * El script de Google Identity Services (o el navegador) puede enviar POST a la URL de la página.
 * En App Router, la ruta /admin/login es una página (solo GET); sin este handler, POST devuelve 405.
 * Respondemos 200 para evitar el error en consola; el login real se hace por API (/api/admin/session).
 */
export async function POST() {
  return NextResponse.json({ ok: true }, { status: 200 });
}
