'use client';

import { useState, Suspense, useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import Script from 'next/script';
import { toast } from 'sonner';
import { Loader2, ArrowLeft } from 'lucide-react';
import { getApiBaseUrl, getGoogleWebClientId } from '@/lib/env';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
            auto_select?: boolean;
          }) => void;
          renderButton: (
            parent: HTMLElement,
            options: { type?: string; theme?: string; size?: string; width?: number }
          ) => void;
        };
      };
    };
  }
}

function AdminLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [scriptReady, setScriptReady] = useState(false);
  const buttonRef = useRef<HTMLDivElement>(null);
  const clientId = getGoogleWebClientId();
  const apiBase = getApiBaseUrl();

  useEffect(() => {
    if (!scriptReady || !clientId || !buttonRef.current || !window.google?.accounts?.id) return;
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: async (response: { credential: string }) => {
        if (!apiBase) {
          toast.error('Configuración: falta NEXT_PUBLIC_API_URL');
          return;
        }
        setLoading(true);
        try {
          const authRes = await fetch(`${apiBase}/auth/google`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ idToken: response.credential }),
          });
          const authData = await authRes.json();
          if (!authRes.ok) {
            toast.error(authData?.error?.message ?? 'Error al validar con Google');
            setLoading(false);
            return;
          }
          const token = authData?.accessToken;
          if (!token) {
            toast.error('Respuesta inválida del servidor');
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
            toast.error(
              err?.error === 'admin required'
                ? 'No tienes rol de administrador'
                : 'Error al iniciar sesión'
            );
            setLoading(false);
            return;
          }
          toast.success('Sesión iniciada');
          router.push('/admin');
          router.refresh();
        } catch {
          toast.error('Error de conexión. Revisa la API.');
        }
        setLoading(false);
      },
    });
    window.google.accounts.id.renderButton(buttonRef.current, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      width: 320,
    });
  }, [scriptReady, clientId, apiBase, router]);

  useEffect(() => {
    if (searchParams.get('error') === 'forbidden') {
      toast.error('Sesión no autorizada. Inicia sesión como admin.');
    }
  }, [searchParams]);

  const showGoogleButton = clientId && apiBase;
  const showConfigError = !clientId || !apiBase;

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <Card className="w-full max-w-md border-border shadow-lg">
        <CardHeader className="space-y-1 text-center">
          <CardTitle className="text-2xl font-semibold">Admin PR4Y</CardTitle>
          <CardDescription>
            Inicia sesión con tu cuenta de Google (solo administradores)
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {showConfigError && (
            <p className="text-sm text-destructive text-center">
              Falta configuración: define NEXT_PUBLIC_API_URL y NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID en
              Vercel.
            </p>
          )}
          {showGoogleButton && (
            <>
              <Script
                src="https://accounts.google.com/gsi/client"
                strategy="afterInteractive"
                onLoad={() => setScriptReady(true)}
              />
              <div className="flex flex-col items-center gap-4">
                {loading ? (
                  <div className="flex h-10 w-[320px] items-center justify-center rounded-md border border-input bg-background">
                    <Loader2 className="size-5 animate-spin text-muted-foreground" />
                  </div>
                ) : (
                  <div ref={buttonRef} />
                )}
              </div>
            </>
          )}
          <p className="text-center">
            <Button variant="ghost" size="sm" asChild className="text-muted-foreground">
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

export default function AdminLoginPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center bg-background">
          <Loader2 className="size-8 animate-spin text-muted-foreground" />
        </div>
      }
    >
      <AdminLoginForm />
    </Suspense>
  );
}
