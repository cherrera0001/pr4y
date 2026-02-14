'use client';

import { useRouter } from 'next/navigation';

export function LogoutButton() {
  const router = useRouter();
  const handleLogout = async () => {
    await fetch('/api/admin/session', { method: 'DELETE' });
    router.push('/admin/login');
    router.refresh();
  };
  return (
    <button
      type="button"
      onClick={handleLogout}
      className="text-sm text-[var(--color-muted)] hover:text-[var(--color-ink)]"
    >
      Cerrar sesión
    </button>
  );
}
