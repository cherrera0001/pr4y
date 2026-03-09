import Link from 'next/link';
import Image from 'next/image';

export function PublicHeader() {
  return (
    <header className="sticky top-0 z-50 w-full border-b border-border/40 bg-background/80 backdrop-blur-xl">
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-2 font-semibold text-foreground">
          <Image src="/favicon.png" alt="PR4Y" width={28} height={28} className="rounded-md" />
          <span className="tracking-tight">PR4Y</span>
        </Link>
        <nav className="flex items-center gap-6 text-sm text-muted-foreground">
          <Link href="/pedir-oracion" className="hover:text-foreground transition-colors">
            Pedir oración
          </Link>
          <Link href="/privacy" className="hover:text-foreground transition-colors">
            Privacidad
          </Link>
          <Link href="/contact" className="hover:text-foreground transition-colors">
            Contacto
          </Link>
        </nav>
      </div>
    </header>
  );
}
