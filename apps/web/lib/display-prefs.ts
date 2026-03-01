export type Theme = 'light' | 'dark' | 'system';
export type FontSize = 'sm' | 'md' | 'lg' | 'xl';
export type FontFamily = 'system' | 'serif' | 'mono';
export type LineSpacing = 'compact' | 'normal' | 'relaxed';

export interface DisplayPrefs {
  theme: Theme;
  fontSize: FontSize;
  fontFamily: FontFamily;
  lineSpacing: LineSpacing;
  contemplativeMode: boolean;
}

export const DEFAULT_DISPLAY_PREFS: DisplayPrefs = {
  theme: 'system',
  fontSize: 'md',
  fontFamily: 'system',
  lineSpacing: 'normal',
  contemplativeMode: false,
};

const STORAGE_KEY = 'pr4y_display_prefs';

export function loadPrefsFromStorage(): DisplayPrefs {
  if (typeof window === 'undefined') return { ...DEFAULT_DISPLAY_PREFS };
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_DISPLAY_PREFS };
    return { ...DEFAULT_DISPLAY_PREFS, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULT_DISPLAY_PREFS };
  }
}

export function savePrefsToStorage(prefs: DisplayPrefs): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
}

// ─── Mapeos a valores CSS ─────────────────────────────────────────────────────

export const FONT_SIZE_SCALE: Record<FontSize, number> = {
  sm: 0.85,
  md: 1.00,
  lg: 1.15,
  xl: 1.30,
};

export const LINE_HEIGHT_SCALE: Record<LineSpacing, number> = {
  compact: 0.90,
  normal:  1.00,
  relaxed: 1.20,
};

export const FONT_FAMILY_BODY: Record<FontFamily, string> = {
  system: 'var(--font-sans)',
  serif:  'Georgia, "Times New Roman", serif',
  mono:   '"Menlo", "Monaco", "Courier New", monospace',
};

export const FONT_FAMILY_HEADING: Record<FontFamily, string> = {
  system: 'Georgia, "Times New Roman", serif',
  serif:  'Georgia, "Times New Roman", serif',
  mono:   '"Menlo", "Monaco", "Courier New", monospace',
};

export function applyPrefsToDocument(prefs: DisplayPrefs): void {
  const root = document.documentElement;
  root.style.setProperty('--pr4y-font-scale',   String(FONT_SIZE_SCALE[prefs.fontSize]));
  root.style.setProperty('--pr4y-line-scale',   String(LINE_HEIGHT_SCALE[prefs.lineSpacing]));
  root.style.setProperty('--pr4y-font-body',    FONT_FAMILY_BODY[prefs.fontFamily]);
  root.style.setProperty('--pr4y-font-heading', FONT_FAMILY_HEADING[prefs.fontFamily]);
  root.classList.toggle('contemplative', prefs.contemplativeMode);
}
