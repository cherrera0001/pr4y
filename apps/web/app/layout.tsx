import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { Providers } from '@/components/providers';

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-sans',
});

const siteUrl = 'https://pr4y.cl';

export const metadata: Metadata = {
  title: 'PR4Y — Tu comunicación con Dios, protegida por un búnker digital',
  description:
    'Cuaderno de oración personal con cifrado E2EE (Zero-Knowledge). Ni nosotros podemos leer tus oraciones. Solo tú y Dios.',
  keywords: ['oración', 'privacidad', 'E2EE', 'cifrado', 'diario espiritual', 'app oración'],
  authors: [{ name: 'PR4Y', url: siteUrl }],
  openGraph: {
    title: 'PR4Y — Tu comunicación con Dios, protegida por un búnker digital',
    description: 'Cifrado de extremo a extremo. Zero-Knowledge. Solo tú y Dios.',
    url: siteUrl,
    siteName: 'PR4Y',
    locale: 'es_CL',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'PR4Y — Búnker digital para tus oraciones',
    description: 'E2EE. Zero-Knowledge. Solo tú y Dios.',
  },
  robots: { index: true, follow: true },
  metadataBase: new URL(siteUrl),
  icons: {
    icon: '/favicon.png',
    shortcut: '/favicon.png',
    apple: '/favicon.png',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="es" className="dark" suppressHydrationWarning>
      <body className={`${inter.variable} font-sans min-h-screen bg-background text-foreground antialiased`}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
