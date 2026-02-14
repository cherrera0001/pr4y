'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Loader2 } from 'lucide-react';

export default function AdminGatePage() {
  const router = useRouter();
  const [token, setToken] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await fetch('/api/admin/gate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(data?.error === 'invalid token' ? 'Token incorrecto' : 'Error. Intenta de nuevo.');
        setLoading(false);
        return;
      }
      router.push('/admin/login');
      router.refresh();
    } catch {
      setError('Error de conexión');
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-950 px-4">
      <Card className="w-full max-w-md glass-card border-slate-700/50 shadow-xl">
        <CardHeader className="space-y-1 text-center">
          <CardTitle className="text-xl font-semibold text-slate-100">
            Acceso al panel de administración
          </CardTitle>
          <CardDescription className="text-slate-400">
            Introduce el token de administrador definido en el servidor.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="gate-token" className="text-slate-200">
                Token de administrador
              </Label>
              <Input
                id="gate-token"
                type="password"
                autoComplete="off"
                value={token}
                onChange={(e) => setToken(e.target.value)}
                required
                disabled={loading}
                className="bg-slate-900/50 border-slate-600 text-slate-100 placeholder:text-slate-500"
                placeholder="••••••••"
              />
            </div>
            {error && (
              <p className="text-sm text-destructive" role="alert">
                {error}
              </p>
            )}
            <Button
              type="submit"
              className="w-full bg-sky-500 hover:bg-sky-600 text-slate-950 font-semibold"
              disabled={loading}
            >
              {loading ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  Verificando…
                </>
              ) : (
                'Continuar'
              )}
            </Button>
          </form>
          <p className="mt-6 text-center">
            <Button variant="ghost" size="sm" asChild className="text-slate-400 hover:text-slate-200">
              <Link href="/">
                <ArrowLeft className="size-4" />
                Volver al inicio
              </Link>
            </Button>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
