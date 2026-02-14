'use client';

import { ThemeProvider } from 'next-themes';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Toaster } from '@/components/ui/sonner';

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="dark"
      enableSystem={false}
      disableTransitionOnChange
    >
      <TooltipProvider delayDuration={0}>
        {children}
        <Toaster theme="dark" position="top-center" richColors closeButton />
      </TooltipProvider>
    </ThemeProvider>
  );
}
