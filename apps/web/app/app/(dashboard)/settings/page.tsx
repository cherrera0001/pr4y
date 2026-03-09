'use client';

import { useRouter } from 'next/navigation';
import { useAuth } from '@/components/auth-provider';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { LogOut, Shield, Palette } from 'lucide-react';
import { DisplayPrefsPanel } from '@/components/display-prefs-panel';

export default function SettingsPage() {
  const router = useRouter();
  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();
    router.replace('/app/login');
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">Ajustes</h1>

      {/* Account info */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Cuenta</CardTitle>
          <CardDescription>{user?.email ?? '—'}</CardDescription>
        </CardHeader>
      </Card>

      {/* Display preferences */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Palette className="size-4 text-muted-foreground" />
            <CardTitle className="text-base">Apariencia</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <DisplayPrefsPanel />
        </CardContent>
      </Card>

      {/* Security */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Shield className="size-4 text-muted-foreground" />
            <CardTitle className="text-base">Seguridad</CardTitle>
          </div>
          <CardDescription>
            Cifrado E2EE activo. Tus datos están protegidos.
          </CardDescription>
        </CardHeader>
      </Card>

      {/* Logout */}
      <Button
        variant="destructive"
        className="w-full"
        onClick={handleLogout}
      >
        <LogOut className="size-4" />
        Cerrar sesión
      </Button>
    </div>
  );
}
