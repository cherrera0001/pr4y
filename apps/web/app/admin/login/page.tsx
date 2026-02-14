'use client';

import { useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

function AdminLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const apiBase = process.env.NEXT_PUBLIC_API_URL || 'https://pr4yapi-production.up.railway.app/v1';
      const loginRes = await fetch(`${apiBase.replace(/\/$/, '')}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const loginData = await loginRes.json();
      if (!loginRes.ok) {
        setError(loginData?.error?.message || 'Credenciales incorrectas');
        setLoading(false);
        return;
      }
      const token = loginData?.accessToken;
      if (!token) {
        setError('Respuesta inválida del servidor');
        setLoading(false);
        return;
      }
      const sessionRes = await fetch('/api/admin/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token }),
      });
      if (!sessionRes.ok) {
        const err = await sessionRes.json();
        setError(err?.error === 'admin required' ? 'No tienes rol de administrador' : 'Error al iniciar sesión');
        setLoading(false);
        return;
      }
      router.push('/admin');
      router.refresh();
    } catch (err) {
      setError('Error de conexión. Revisa la API.');
      setLoading(false);
    }
  };

  const qError = searchParams.get('error');
  const showError = error || (qError === 'forbidden' ? 'Sesión no autorizada. Inicia sesión como admin.' : '');

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-paper)] px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold text-[var(--color-ink)] text-center mb-2">
          Admin PR4Y
        </h1>
        <p className="text-[var(--color-muted)] text-center text-sm mb-8">
          Inicia sesión con una cuenta de administrador
        </p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-[var(--color-ink)] mb-1">
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full rounded-lg border border-[var(--color-ink)]/20 bg-white px-3 py-2 text-[var(--color-ink)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-[var(--color-ink)] mb-1">
              Contraseña
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full rounded-lg border border-[var(--color-ink)]/20 bg-white px-3 py-2 text-[var(--color-ink)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]"
            />
          </div>
          {showError && (
            <p className="text-sm text-red-600" role="alert">
              {showError}
            </p>
          )}
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-[var(--color-accent)] px-4 py-2 font-semibold text-white hover:opacity-90 disabled:opacity-50 transition"
          >
            {loading ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
        <p className="mt-6 text-center">
          <a href="/" className="text-sm text-[var(--color-muted)] hover:text-[var(--color-ink)]">
            ← Volver al inicio
          </a>
        </p>
      </div>
    </div>
  );
}

export default function AdminLoginPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-paper)]">
        <p className="text-[var(--color-muted)]">Cargando…</p>
      </div>
    }>
      <AdminLoginForm />
    </Suspense>
  );
}
