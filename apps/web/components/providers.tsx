'use client';

import { ThemeProvider } from 'next-themes';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Toaster } from '@/components/ui/sonner';
import { DisplayPrefsProvider } from '@/components/display-prefs-provider';

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
    >
      <DisplayPrefsProvider>
        <TooltipProvider delayDuration={0}>
          {children}
          <Toaster theme="system" position="top-center" richColors closeButton />
        </TooltipProvider>
      </DisplayPrefsProvider>
    </ThemeProvider>
  );
}
