import type { Metadata } from 'next';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowLeft, Mail } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Contacto — PR4Y',
  description: 'Contacto del proyecto PR4Y.',
};

export default function ContactPage() {
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
            <CardTitle className="text-slate-100 text-2xl flex items-center gap-2">
              <Mail className="size-5 text-sky-400" />
              Contacto
            </CardTitle>
            <p className="text-slate-400 text-sm">
              Para consultas sobre el servicio PR4Y, privacidad o soporte técnico.
            </p>
          </CardHeader>
          <CardContent className="text-slate-300">
            <p>
              Puedes escribirnos a{' '}
              <a
                href="mailto:hola@pr4y.cl"
                className="text-sky-400 hover:underline font-medium"
              >
                hola@pr4y.cl
              </a>
              . Responderemos en la mayor brevedad posible.
            </p>
            <p className="mt-4 text-slate-400 text-sm">
              Para temas relacionados con la app Android (Google Play), utiliza el mismo correo o el formulario de soporte en la ficha de la aplicación.
            </p>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
