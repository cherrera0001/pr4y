'use client';

import { useState, Suspense, useEffect, useRef } from 'react';
import { useSearchParams } from 'next/navigation';
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
            login_uri?: string;
            ux_mode?: 'popup' | 'redirect';
            auto_prompt?: boolean;
          }) => void;
          renderButton: (parent: HTMLElement, options: { type?: string; size?: string; theme?: string; width?: number }) => void;
        };
      };
    };
  }
}

function AdminLoginForm() {
  const searchParams = useSearchParams();
  const [loading] = useState(false);
  const [loginUri, setLoginUri] = useState('');
  const buttonContainerRef = useRef<HTMLDivElement>(null);
  const clientId = getGoogleWebClientId();
  const apiBase = getApiBaseUrl();

  useEffect(() => {
    if (typeof window !== 'undefined') setLoginUri(`${window.location.origin}/api/admin/login`);
  }, []);

  const canShowButton = clientId && apiBase && loginUri;
  const [gsiLoaded, setGsiLoaded] = useState(false);
  const gsiInitialized = useRef(false);

  useEffect(() => {
    if (!gsiLoaded || !loginUri || !clientId || !buttonContainerRef.current || !window.google?.accounts?.id || gsiInitialized.current) return;
    gsiInitialized.current = true;
    window.google.accounts.id.initialize({
      client_id: clientId,
      login_uri: loginUri,
      ux_mode: 'redirect',
      auto_prompt: false,
    });
    window.google.accounts.id.renderButton(buttonContainerRef.current, {
      type: 'standard',
      size: 'large',
      theme: 'outline',
      width: 320,
    });
  }, [gsiLoaded, loginUri, clientId]);

  useEffect(() => {
    const err = searchParams.get('error');
    if (!err) return;
    if (err === 'forbidden') {
      toast.error('Sesión no autorizada. Inicia sesión como admin.');
      return;
    }
    if (err === 'config') {
      toast.error('Configuración: falta NEXT_PUBLIC_API_URL');
      return;
    }
    if (err === 'admin_required') {
      toast.error('No tienes rol de administrador');
      return;
    }
    if (err === 'invalid_token' || err === 'invalid_response') {
      toast.error('Error al validar con Google. Intenta de nuevo.');
      return;
    }
    if (err === 'server') {
      toast.error('Error temporal del servidor. Intenta de nuevo en unos minutos.');
      return;
    }
    toast.error(err.length > 60 ? 'Error al iniciar sesión' : err);
  }, [searchParams]);

  const showConfigError = !clientId || !apiBase;
  const missingApi = !apiBase;
  const missingClientId = !clientId;

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
            <div className="space-y-2 rounded-md border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive">
              <p className="font-medium">Falta configuración en Vercel</p>
              <ul className="list-inside list-disc space-y-1 text-left">
                {missingApi && <li>NEXT_PUBLIC_API_URL o NEXT_PUBLIC_API_BASE_URL (URL de la API, ej. Railway)</li>}
                {missingClientId && <li>NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID (cliente OAuth Web de Google)</li>}
              </ul>
              <p className="pt-1 text-muted-foreground">
                Añádelas en Settings → Environment Variables y haz Redeploy. Ver VERCEL.md en el repo.
              </p>
            </div>
          )}
          {canShowButton && (
            <div className="flex flex-col items-center gap-4">
              <Script
                src="https://accounts.google.com/gsi/client"
                strategy="afterInteractive"
                onLoad={() => setGsiLoaded(true)}
              />
              <div ref={buttonContainerRef} className="flex justify-center" />
              {loading && (
                <div className="flex h-10 w-[320px] items-center justify-center rounded-md border border-input bg-background">
                  <Loader2 className="size-5 animate-spin text-muted-foreground" />
                </div>
              )}
            </div>
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
