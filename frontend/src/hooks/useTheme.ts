import { useEffect, useState } from 'react';

export type Theme = 'light' | 'dark';

// index.html reads this key too, to apply the theme before the first paint.
const STORAGE_KEY = 'flipfinder.theme';

/** The theme index.html applied: the saved choice, otherwise the system's. */
function initialTheme(): Theme {
  const applied = document.documentElement.dataset.theme;
  if (applied === 'light' || applied === 'dark') {
    return applied;
  }
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/** Light or dark: the system setting until the viewer switches, then their choice. */
export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState(initialTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  const toggle = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Not remembering the theme is harmless.
    }
  };

  return [theme, toggle];
}
