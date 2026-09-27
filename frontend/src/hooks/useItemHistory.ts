import { useEffect, useState } from 'react';

import { fetchItemHistory, type ItemHistory } from '../lib/api';

export type HistoryState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; history: ItemHistory };

/** An item's trading compared with its history, fetched while `enabled`; null when not. */
export function useItemHistory(itemId: number, enabled: boolean): HistoryState | null {
  const [state, setState] = useState<HistoryState>({ status: 'loading' });

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const request = new AbortController();
    setState({ status: 'loading' });
    fetchItemHistory(itemId, request.signal).then(
      (history) => setState({ status: 'ready', history }),
      () => !request.signal.aborted && setState({ status: 'error' }),
    );
    return () => request.abort();
  }, [itemId, enabled]);

  return enabled ? state : null;
}
