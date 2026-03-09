'use client';

import { useState, useEffect, useCallback } from 'react';
import { useTheme } from 'next-themes';
import {
  DisplayPrefs,
  DEFAULT_DISPLAY_PREFS,
  loadPrefsFromStorage,
  savePrefsToStorage,
  applyPrefsToDocument,
} from '@/lib/display-prefs';

export function useDisplayPrefs() {
  const [prefs, setPrefs] = useState<DisplayPrefs>(DEFAULT_DISPLAY_PREFS);
  const [mounted, setMounted] = useState(false);
  const { setTheme } = useTheme();

  useEffect(() => {
    const loaded = loadPrefsFromStorage();
    setPrefs(loaded);
    setTheme(loaded.theme);
    applyPrefsToDocument(loaded);
    setMounted(true);
  }, [setTheme]);

  const update = useCallback(
    (next: Partial<DisplayPrefs>) => {
      setPrefs((prev) => {
        const updated = { ...prev, ...next };
        savePrefsToStorage(updated);
        applyPrefsToDocument(updated);
        if (next.theme !== undefined) setTheme(next.theme);
        return updated;
      });
    },
    [setTheme]
  );

  return { prefs, update, mounted };
}
