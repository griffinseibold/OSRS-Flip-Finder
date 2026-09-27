import { useCallback, useEffect, useState } from 'react';

import { fetchFlips, flipsUrl, type FlipPage, type FlipQuery } from '../lib/api';

// The backend imports new prices every five minutes; checking every minute
// keeps the page at most a minute behind it.
const REFRESH_INTERVAL_MS = 60_000;
// Retry sooner after a failure or while the backend has not imported anything yet.
const RETRY_DELAY_MS = 15_000;

interface FlipsState {
  data: FlipPage | null;
  error: Error | null;
  loading: boolean;
}

/**
 * Loads one page of flips for the query, keeping the previous page on screen
 * while the next loads, and refreshes periodically while the page is visible.
 */
export function useFlips(query: FlipQuery): FlipsState & { reload: () => void } {
  const url = flipsUrl(query);
  const [state, setState] = useState<FlipsState>({ data: null, error: null, loading: true });
  const [reloadCount, setReloadCount] = useState(0);
  const reload = useCallback(() => setReloadCount((count) => count + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setState((current) => ({ ...current, loading: true }));
    fetchFlips(url, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setState({ data, error: null, loading: false });
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setState((current) => ({
            ...current,
            error: error instanceof Error ? error : new Error(String(error)),
            loading: false,
          }));
        }
      },
    );
    return () => controller.abort();
  }, [url, reloadCount]);

  const { loading, error, data } = state;
  useEffect(() => {
    if (loading) {
      return;
    }
    const delay = error || data?.itemCount === 0 ? RETRY_DELAY_MS : REFRESH_INTERVAL_MS;
    let due = false;
    const reloadWhenVisible = () => {
      if (due && document.visibilityState === 'visible') {
        reload();
      }
    };
    const timer = window.setTimeout(() => {
      due = true;
      reloadWhenVisible();
    }, delay);
    document.addEventListener('visibilitychange', reloadWhenVisible);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', reloadWhenVisible);
    };
  }, [loading, error, data, reload]);

  return { ...state, reload };
}
