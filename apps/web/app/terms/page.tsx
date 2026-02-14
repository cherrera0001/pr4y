import type { Metadata } from 'next';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowLeft } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Términos de Uso — PR4Y',
  description: 'Términos de uso del servicio PR4Y.',
};

export default function TermsPage() {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-12">
      <div className="mx-auto max-w-3xl">
        <Button variant="ghost" size="sm" asChild className="text-slate-400 hover:text-slate-200 mb-8">
          <Link href="/">
            <ArrowLeft className="size-4" />
            Volver al inicio
          </Link>
        </Button>
        <Card className="bg-slate-900/60 border-slate-700/50 backdrop-blur-xl">
          <CardHeader>
            <CardTitle className="text-slate-100 text-2xl">Términos de Uso</CardTitle>
            <p className="text-slate-400 text-sm">Última actualización: febrero de 2025</p>
          </CardHeader>
          <CardContent className="prose prose-invert prose-slate max-w-none">
            <p className="text-slate-300">
              Al utilizar el sitio web pr4y.cl y la aplicación PR4Y aceptas estos términos. El servicio se ofrece &quot;tal cual&quot;.
              Eres responsable del uso de tu cuenta y de mantener la confidencialidad de tu contraseña. Nos reservamos el derecho de
              suspender cuentas que infrinjan estos términos o la ley aplicable.
            </p>
            <p className="text-slate-400 text-sm mt-6">
              Para consultas, contacta a través de la página de <Link href="/contact" className="text-sky-400 hover:underline">Contacto</Link>.
            </p>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
