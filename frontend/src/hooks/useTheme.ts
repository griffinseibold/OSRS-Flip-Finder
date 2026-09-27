import { useEffect, useState } from 'react';

export type Theme = 'auto' | 'light' | 'dark';

// index.html reads this key too, to apply the theme before the first paint.
const STORAGE_KEY = 'flipfinder.theme';

function readTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'auto';
  } catch {
    return 'auto';
  }
}

/** The colour theme: the system's ('auto') or one the viewer picked, remembered in this browser. */
export function useTheme(): [Theme, (theme: Theme) => void] {
  const [theme, setTheme] = useState(readTheme);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'auto') {
      delete root.dataset.theme;
    } else {
      root.dataset.theme = theme;
    }
    try {
      if (theme === 'auto') {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, theme);
      }
    } catch {
      // Not remembering the theme is harmless.
    }
  }, [theme]);

  return [theme, setTheme];
}
