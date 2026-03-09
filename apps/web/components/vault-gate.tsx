'use client';

import { useCallback, useEffect, useState } from 'react';
import Image from 'next/image';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2, Lock, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import {
  isVaultUnlocked,
  unlockVault,
  setupVault,
  fetchWrappedDek,
} from '@/lib/vault';

/**
 * Gate component: blocks children until the vault is unlocked.
 * Shows passphrase input (unlock or first-time setup).
 */
export function VaultGate({ children }: { children: React.ReactNode }) {
  const [unlocked, setUnlocked] = useState(false);
  const [checking, setChecking] = useState(true);
  const [needsSetup, setNeedsSetup] = useState(false);
  const [passphrase, setPassphrase] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isVaultUnlocked()) {
      setUnlocked(true);
      setChecking(false);
      return;
    }
    // Check if vault needs setup
    fetchWrappedDek()
      .then((data) => setNeedsSetup(!data))
      .catch(() => {})
      .finally(() => setChecking(false));
  }, []);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!passphrase) return;
      setLoading(true);
      try {
        if (needsSetup) {
          if (passphrase.length < 6) {
            toast.error('La clave debe tener al menos 6 caracteres');
            setLoading(false);
            return;
          }
          await setupVault(passphrase);
          toast.success('Búnker configurado. Tu clave protege tus datos.');
        } else {
          await unlockVault(passphrase);
        }
        setUnlocked(true);
      } catch (err) {
        const msg =
          err instanceof Error && err.message === 'WRONG_PASSPHRASE'
            ? 'Clave incorrecta. Intenta de nuevo.'
            : err instanceof Error
              ? err.message
              : 'Error al desbloquear';
        toast.error(msg);
      }
      setLoading(false);
    },
    [passphrase, needsSetup]
  );

  if (checking) {
    return (
      <div className="flex items-center justify-center py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (unlocked) {
    return <>{children}</>;
  }

  return (
    <div className="flex flex-col items-center justify-center py-16 px-6">
      <div className="w-full max-w-sm space-y-8 text-center">
        <div>
          <Image
            src="/favicon.png"
            alt="PR4Y"
            width={56}
            height={56}
            className="mx-auto rounded-xl"
          />
          <h2 className="mt-6 text-xl font-bold text-foreground">
            {needsSetup ? 'Protege tu búnker' : 'Acceso privado'}
          </h2>
          <p className="mt-2 text-sm text-muted-foreground">
            {needsSetup
              ? 'Crea una clave de privacidad para cifrar tus oraciones.'
              : 'Introduce tu clave para desbloquear tus datos.'}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2 text-left">
            <Label htmlFor="vault-passphrase">
              {needsSetup ? 'Nueva clave de privacidad' : 'Clave de privacidad'}
            </Label>
            <Input
              id="vault-passphrase"
              type="password"
              value={passphrase}
              onChange={(e) => setPassphrase(e.target.value)}
              placeholder={needsSetup ? 'Mínimo 6 caracteres' : '••••••••'}
              required
              autoComplete={
                needsSetup ? 'new-password' : 'current-password'
              }
              autoFocus
            />
          </div>
          <Button
            type="submit"
            className="w-full"
            disabled={loading}
            size="lg"
          >
            {loading ? (
              <>
                <Loader2 className="size-4 animate-spin" />
                {needsSetup ? 'Configurando…' : 'Desbloqueando…'}
              </>
            ) : needsSetup ? (
              <>
                <ShieldCheck className="size-4" />
                Configurar y entrar
              </>
            ) : (
              <>
                <Lock className="size-4" />
                Desbloquear
              </>
            )}
          </Button>
        </form>
      </div>
    </div>
  );
}
