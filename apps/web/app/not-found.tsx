import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { ArrowLeft } from 'lucide-react';

export default function NotFound() {
  return (
    <main className="min-h-screen bg-background flex items-center justify-center px-6">
      <div className="text-center max-w-md">
        <h1 className="text-7xl font-bold text-foreground tracking-tight">404</h1>
        <p className="mt-4 text-lg text-muted-foreground">
          Esta página no existe o fue movida.
        </p>
        <Button asChild className="mt-8 rounded-full px-8" size="lg">
          <Link href="/">
            <ArrowLeft className="size-4 mr-2" />
            Volver al inicio
          </Link>
        </Button>
      </div>
    </main>
  );
}
