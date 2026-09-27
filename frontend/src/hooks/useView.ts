import { useEffect, useState } from 'react';

export type View = 'chat' | 'flips';

const STORAGE_KEY = 'flipfinder.view';

/** Which homelab tab is open, remembered in this browser. The chat comes first. */
export function useView(): [View, (view: View) => void] {
  const [view, setView] = useState<View>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'flips' ? 'flips' : 'chat';
    } catch {
      return 'chat';
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, view);
    } catch {
      // Not remembering the tab is harmless.
    }
  }, [view]);

  return [view, setView];
}
