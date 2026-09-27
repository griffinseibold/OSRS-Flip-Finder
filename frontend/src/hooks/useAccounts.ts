import { useEffect, useState } from 'react';

import { fetchAccounts, type Account } from '../lib/api';

// The plugin reports within seconds of a change, so check often.
const REFRESH_INTERVAL_MS = 30_000;

/** Accounts the RuneLite plugin has reported, most recent first; null until loaded. */
export function useAccounts(enabled: boolean): Account[] | null {
  const [accounts, setAccounts] = useState<Account[] | null>(null);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let current = true;
    const load = () =>
      fetchAccounts().then(
        (result) => current && setAccounts(result),
        () => current && setAccounts((previous) => previous ?? []),
      );
    load();
    const timer = window.setInterval(load, REFRESH_INTERVAL_MS);
    return () => {
      current = false;
      window.clearInterval(timer);
    };
  }, [enabled]);

  return accounts;
}
