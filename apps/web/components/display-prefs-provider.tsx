'use client';

import { createContext, useContext } from 'react';
import { useDisplayPrefs } from '@/hooks/use-display-prefs';
import { DisplayPrefs, DEFAULT_DISPLAY_PREFS } from '@/lib/display-prefs';

interface DisplayPrefsContextValue {
  prefs: DisplayPrefs;
  update: (next: Partial<DisplayPrefs>) => void;
  mounted: boolean;
}

const DisplayPrefsContext = createContext<DisplayPrefsContextValue>({
  prefs: DEFAULT_DISPLAY_PREFS,
  update: () => {},
  mounted: false,
});

export function DisplayPrefsProvider({ children }: { children: React.ReactNode }) {
  const value = useDisplayPrefs();
  return (
    <DisplayPrefsContext.Provider value={value}>
      {children}
    </DisplayPrefsContext.Provider>
  );
}

export function useDisplayPrefsContext() {
  return useContext(DisplayPrefsContext);
}
