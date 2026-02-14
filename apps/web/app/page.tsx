import Link from 'next/link';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Shield, Database, CloudCog, ArrowRight, Download } from 'lucide-react';

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col bg-slate-950">
      {/* Hero */}
      <section className="relative flex-1 overflow-hidden px-6 py-24 sm:py-32">
        <div
          className="absolute inset-0 bg-gradient-to-b from-slate-900/80 via-slate-950 to-slate-950"
          aria-hidden
        />
        <div
          className="absolute inset-0 bg-[radial-gradient(ellipse_80%_50%_at_50%_-20%,rgba(14,165,233,0.18),transparent_50%)]"
          aria-hidden
        />
        <div
          className="absolute bottom-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-sky-500/30 to-transparent"
          aria-hidden
        />
        <div className="relative mx-auto max-w-4xl text-center">
          <h1 className="text-4xl font-bold tracking-tight sm:text-5xl md:text-6xl text-balance text-slate-50">
            Tu comunicación con Dios, protegida por un{' '}
            <span className="text-sky-400">búnker digital</span>
          </h1>
          <p className="mt-8 text-lg text-slate-400 max-w-2xl mx-auto leading-relaxed">
            Cuaderno de oración personal con cifrado E2EE (Zero-Knowledge). Solo tú y Dios. Ni nosotros podemos leer tus oraciones.
          </p>
          <div className="mt-12 flex flex-wrap justify-center gap-4">
            <Button
              asChild
              size="lg"
              className="rounded-full px-8 bg-sky-500 hover:bg-sky-600 text-slate-950 font-semibold shadow-lg shadow-sky-500/25"
            >
              <Link href="/admin">
                <span className="flex items-center gap-2">
                  Panel Admin
                  <ArrowRight className="size-4" />
                </span>
              </Link>
            </Button>
            <Button
              asChild
              variant="outline"
              size="lg"
              className="rounded-full px-8 border-slate-600 text-slate-200 hover:bg-slate-800 hover:text-slate-50"
            >
              <a
                href="https://play.google.com/store/apps/details?id=com.pr4y.app"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span className="flex items-center gap-2">
                  <Download className="size-4" />
                  Descargar App Android
                </span>
              </a>
            </Button>
          </div>
        </div>
      </section>

      {/* Features: E2EE, Railway, Cloudflare — 3 columnas con Cards shadcn */}
      <section className="px-6 py-20 sm:py-28 border-t border-slate-800/50">
        <div className="mx-auto max-w-5xl">
          <h2 className="text-2xl font-semibold text-slate-50 text-center tracking-tight">
            Seguridad y infraestructura de nivel profesional
          </h2>
          <p className="mt-3 text-center text-slate-400 max-w-xl mx-auto">
            Cifrado real, base de datos gestionada y CDN global para tu privacidad.
          </p>
          <div className="mt-16 grid gap-6 sm:grid-cols-3">
            <Card className="bg-slate-900/60 border-slate-700/50 backdrop-blur-xl shadow-xl shadow-black/20">
              <CardHeader>
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-sky-500/20 text-sky-400">
                  <Shield className="size-6" />
                </div>
                <CardTitle className="text-slate-100">Cifrado E2EE</CardTitle>
                <CardDescription className="text-slate-400">
                  Zero-Knowledge: tus oraciones se cifran en el dispositivo. Ni nosotros ni nadie puede leerlas.
                </CardDescription>
              </CardHeader>
            </Card>
            <Card className="bg-slate-900/60 border-slate-700/50 backdrop-blur-xl shadow-xl shadow-black/20">
              <CardHeader>
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-sky-500/20 text-sky-400">
                  <Database className="size-6" />
                </div>
                <CardTitle className="text-slate-100">Base de datos en Railway</CardTitle>
                <CardDescription className="text-slate-400">
                  Postgres gestionado y escalable. Tus datos cifrados se almacenan con la máxima disponibilidad.
                </CardDescription>
              </CardHeader>
            </Card>
            <Card className="bg-slate-900/60 border-slate-700/50 backdrop-blur-xl shadow-xl shadow-black/20">
              <CardHeader>
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-sky-500/20 text-sky-400">
                  <CloudCog className="size-6" />
                </div>
                <CardTitle className="text-slate-100">Búnker en Cloudflare</CardTitle>
                <CardDescription className="text-slate-400">
                  Web y API protegidas por la red global de Cloudflare. Alta velocidad y resistencia.
                </CardDescription>
              </CardHeader>
            </Card>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-800/50 px-6 py-8">
        <div className="mx-auto max-w-4xl flex flex-col sm:flex-row justify-between items-center gap-4 text-sm text-slate-400">
          <span>© PR4Y — Privacidad sagrada</span>
          <nav className="flex flex-wrap items-center justify-center gap-6">
            <Link href="/terms" className="hover:text-slate-200 transition-colors">
              Términos
            </Link>
            <Link href="/privacy" className="hover:text-slate-200 transition-colors">
              Privacidad
            </Link>
            <Link href="/contact" className="hover:text-slate-200 transition-colors">
              Contacto
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
