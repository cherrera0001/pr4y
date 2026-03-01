'use client';

import { Palette } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useDisplayPrefsContext } from '@/components/display-prefs-provider';
import type { DisplayPrefs } from '@/lib/display-prefs';

// ─── Chip genérico ────────────────────────────────────────────────────────────

function Chip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'rounded-full px-3 py-1 text-xs font-medium border transition-colors',
        active
          ? 'bg-primary text-primary-foreground border-primary'
          : 'bg-transparent text-muted-foreground border-border hover:border-primary/50 hover:text-foreground'
      )}
    >
      {label}
    </button>
  );
}

// ─── Fila de chips ────────────────────────────────────────────────────────────

function ChipRow<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <div className="space-y-2">
      <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{label}</p>
      <div className="flex flex-wrap gap-2">
        {options.map((opt) => (
          <Chip
            key={opt.value}
            label={opt.label}
            active={value === opt.value}
            onClick={() => onChange(opt.value)}
          />
        ))}
      </div>
    </div>
  );
}

// ─── Panel principal ──────────────────────────────────────────────────────────

export function DisplayPrefsPanel() {
  const { prefs, update, mounted } = useDisplayPrefsContext();

  if (!mounted) return null;

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="size-8" title="Apariencia">
          <Palette className="size-4" />
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="w-72">
        <SheetHeader>
          <SheetTitle>Apariencia</SheetTitle>
        </SheetHeader>

        <div className="mt-6 flex flex-col gap-6">
          <ChipRow<DisplayPrefs['theme']>
            label="Tema"
            options={[
              { value: 'system', label: 'Auto' },
              { value: 'light',  label: 'Claro' },
              { value: 'dark',   label: 'Oscuro' },
            ]}
            value={prefs.theme}
            onChange={(v) => update({ theme: v })}
          />

          <ChipRow<DisplayPrefs['fontSize']>
            label="Tamaño de texto"
            options={[
              { value: 'sm', label: 'Pequeño' },
              { value: 'md', label: 'Normal' },
              { value: 'lg', label: 'Grande' },
              { value: 'xl', label: 'Extra' },
            ]}
            value={prefs.fontSize}
            onChange={(v) => update({ fontSize: v })}
          />

          <ChipRow<DisplayPrefs['fontFamily']>
            label="Tipografía"
            options={[
              { value: 'system', label: 'Sans' },
              { value: 'serif',  label: 'Serif' },
              { value: 'mono',   label: 'Mono' },
            ]}
            value={prefs.fontFamily}
            onChange={(v) => update({ fontFamily: v })}
          />

          <ChipRow<DisplayPrefs['lineSpacing']>
            label="Espaciado"
            options={[
              { value: 'compact',  label: 'Compacto' },
              { value: 'normal',   label: 'Normal' },
              { value: 'relaxed',  label: 'Amplio' },
            ]}
            value={prefs.lineSpacing}
            onChange={(v) => update({ lineSpacing: v })}
          />

          {/* Modo Contemplativo */}
          <div className="space-y-2">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
              Modo Contemplativo
            </p>
            <button
              onClick={() => update({ contemplativeMode: !prefs.contemplativeMode })}
              className={cn(
                'w-full rounded-lg border px-4 py-3 text-left text-sm transition-colors',
                prefs.contemplativeMode
                  ? 'border-primary bg-primary/10 text-foreground'
                  : 'border-border bg-transparent text-muted-foreground hover:border-primary/50 hover:text-foreground'
              )}
            >
              <span className="font-medium">
                {prefs.contemplativeMode ? 'Activado' : 'Desactivado'}
              </span>
              <p className="mt-0.5 text-xs opacity-70">
                Paleta cálida y sin distracciones
              </p>
            </button>
          </div>

          {/* Preview de tipografía en tiempo real */}
          <div className="rounded-lg border border-border bg-muted/30 p-4 space-y-1">
            <p className="text-xs text-muted-foreground">Vista previa</p>
            <p className="font-semibold" style={{ fontFamily: 'var(--pr4y-font-heading)' }}>
              Tu oración de hoy
            </p>
            <p className="text-sm text-muted-foreground" style={{ fontFamily: 'var(--pr4y-font-body)' }}>
              Señor, te pido sabiduría y paz en este día.
            </p>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
