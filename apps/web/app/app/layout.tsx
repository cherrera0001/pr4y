import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'PR4Y — Mi Búnker',
};

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
