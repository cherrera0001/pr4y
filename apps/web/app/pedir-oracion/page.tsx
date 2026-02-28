'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { ArrowLeft, Heart, Loader2 } from 'lucide-react';
import { submitPublicRequest } from '@/lib/api';

const BODY_MIN = 10;
const BODY_MAX = 2000;
const TITLE_MAX = 200;

export default function PedirOracionPage() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    const t = title.trim();
    const b = body.trim();
    if (b.length < BODY_MIN) {
      setError(`Escribe al menos ${BODY_MIN} caracteres para tu pedido.`);
      return;
    }
    if (b.length > BODY_MAX) {
      setError(`El pedido no puede superar ${BODY_MAX} caracteres.`);
      return;
    }
    if (t.length > TITLE_MAX) {
      setError(`El título no puede superar ${TITLE_MAX} caracteres.`);
      return;
    }
    setLoading(true);
    try {
      const result = await submitPublicRequest({
        title: t || undefined,
        body: b,
      });
      setSuccess(result.message ?? 'Tu pedido de oración ha sido recibido.');
      setTitle('');
      setBody('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al enviar. Intenta de nuevo.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-slate-950 px-6 py-12">
      <div className="mx-auto max-w-2xl">
        <Button variant="ghost" size="sm" asChild className="text-slate-400 hover:text-slate-200 mb-8">
          <Link href="/">
            <ArrowLeft className="size-4" />
            Volver al inicio
          </Link>
        </Button>
        <Card className="bg-slate-900/60 border-slate-700/50 backdrop-blur-xl">
          <CardHeader>
            <CardTitle className="text-slate-100 text-2xl flex items-center gap-2">
              <Heart className="size-5 text-sky-400" />
              Pedir oración
            </CardTitle>
            <CardDescription className="text-slate-400">
              Envía tu pedido de forma anónima. Aparecerá en la Ruleta de la app para que otros oren por ti. No guardamos tu nombre ni correo.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {success && (
              <div className="mb-6 p-4 rounded-lg bg-sky-500/10 border border-sky-500/30 text-sky-200 text-sm">
                {success}
              </div>
            )}
            {error && (
              <div className="mb-6 p-4 rounded-lg bg-red-500/10 border border-red-500/30 text-red-200 text-sm">
                {error}
              </div>
            )}
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label htmlFor="title" className="block text-sm font-medium text-slate-300 mb-1">
                  Título (opcional)
                </label>
                <input
                  id="title"
                  type="text"
                  maxLength={TITLE_MAX}
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Ej: Por mi familia"
                  className="w-full rounded-lg border border-slate-600 bg-slate-800/50 px-4 py-2 text-slate-100 placeholder:text-slate-500 focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
                />
                <p className="mt-1 text-xs text-slate-500">{title.length}/{TITLE_MAX}</p>
              </div>
              <div>
                <label htmlFor="body" className="block text-sm font-medium text-slate-300 mb-1">
                  Tu pedido de oración *
                </label>
                <textarea
                  id="body"
                  required
                  minLength={BODY_MIN}
                  maxLength={BODY_MAX}
                  rows={5}
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  placeholder="Escribe aquí tu petición para que otros oren por ti..."
                  className="w-full rounded-lg border border-slate-600 bg-slate-800/50 px-4 py-2 text-slate-100 placeholder:text-slate-500 focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500 resize-y"
                />
                <p className="mt-1 text-xs text-slate-500">{body.length}/{BODY_MAX} (mín. {BODY_MIN})</p>
              </div>
              <Button
                type="submit"
                disabled={loading}
                className="w-full rounded-full bg-sky-500 hover:bg-sky-600 text-slate-950 font-semibold"
              >
                {loading ? (
                  <>
                    <Loader2 className="size-4 animate-spin mr-2" />
                    Enviando…
                  </>
                ) : (
                  'Enviar pedido de oración'
                )}
              </Button>
            </form>
            <p className="mt-4 text-xs text-slate-500">
              Solo publicamos pedidos respetuosos. No se permiten insultos, contenido ofensivo ni enlaces promocionales.
            </p>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
