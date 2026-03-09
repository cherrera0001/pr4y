'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import Image from 'next/image';
import { useAuth } from '@/components/auth-provider';
import { isLoggedIn } from '@/lib/auth-client';
import {
  BookOpen,
  Home,
  Phone,
  Settings,
  Star,
} from 'lucide-react';
import { VaultGate } from '@/components/vault-gate';

const NAV_ITEMS = [
  { href: '/app', icon: Home, label: 'Inicio' },
  { href: '/app/roulette', icon: Phone, label: 'Orar' },
  { href: '/app/journal', icon: BookOpen, label: 'Diario' },
  { href: '/app/victorias', icon: Star, label: 'Victorias' },
  { href: '/app/settings', icon: Settings, label: 'Ajustes' },
] as const;

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, user } = useAuth();

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!isAuthenticated && !isLoggedIn()) {
      router.replace('/app/login');
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated && !isLoggedIn()) {
    return null;
  }

  return (
    <div className="flex min-h-screen flex-col bg-background">
      {/* Top bar */}
      <header className="sticky top-0 z-40 border-b border-border/40 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-lg items-center justify-between px-4">
          <Link href="/app" className="flex items-center gap-2">
            <Image
              src="/favicon.png"
              alt="PR4Y"
              width={28}
              height={28}
              className="rounded-md"
            />
            <span className="font-semibold text-foreground tracking-tight">
              PR4Y
            </span>
          </Link>
          {user && (
            <span className="text-xs text-muted-foreground truncate max-w-[140px]">
              {user.email}
            </span>
          )}
        </div>
      </header>

      {/* Content — protected by vault passphrase */}
      <main className="flex-1 mx-auto w-full max-w-lg px-4 py-6 pb-20">
        <VaultGate>{children}</VaultGate>
      </main>

      {/* Bottom navigation */}
      <nav className="fixed bottom-0 inset-x-0 z-40 border-t border-border/40 bg-background/90 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-lg items-center justify-around px-2">
          {NAV_ITEMS.map(({ href, icon: Icon, label }) => {
            const isActive =
              href === '/app'
                ? pathname === '/app'
                : pathname.startsWith(href);
            return (
              <Link
                key={href}
                href={href}
                className={`flex flex-col items-center gap-1 px-3 py-2 text-xs transition-colors ${
                  isActive
                    ? 'text-primary'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <Icon className="size-5" />
                <span>{label}</span>
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
