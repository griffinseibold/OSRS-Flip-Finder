import { useEffect, useState } from 'react';

import type { Membership, PriceBasis, SortDirection, SortKey } from './lib/api';

export interface Settings {
  basis: PriceBasis;
  membership: Membership;
  maxTradeAgeMinutes: number;
  minVolume5m: number;
  /** Raw text of the budget field, e.g. "10m". */
  budget: string;
  sortKey: SortKey;
  sortDirection: SortDirection;
}

export const TRADE_AGE_OPTIONS = [
  { value: 5, label: '5 minutes' },
  { value: 30, label: '30 minutes' },
  { value: 60, label: '1 hour' },
  { value: 360, label: '6 hours' },
  { value: 1440, label: '24 hours' },
  { value: 0, label: 'Any time' },
] as const;

export const VOLUME_OPTIONS = [
  { value: 0, label: 'Any' },
  { value: 1, label: '1+' },
  { value: 10, label: '10+' },
  { value: 100, label: '100+' },
  { value: 1000, label: '1,000+' },
] as const;

const SORT_KEYS: readonly SortKey[] = [
  'estimatedProfit',
  'potentialProfit',
  'margin',
  'roi',
  'buyPrice',
  'sellPrice',
  'buyLimit',
  'volume5m',
  'lastTradeTime',
  'name',
];

// By default, rank by profit that could fill at the current five-minute pace,
// among items trading actively enough for that pace to mean something.
export const DEFAULT_SETTINGS: Settings = {
  basis: 'latest',
  membership: 'all',
  maxTradeAgeMinutes: 60,
  minVolume5m: 10,
  budget: '',
  sortKey: 'estimatedProfit',
  sortDirection: 'desc',
};

const STORAGE_KEY = 'flipfinder.settings.v2';

function oneOf<T>(value: unknown, allowed: readonly T[], fallback: T): T {
  return allowed.includes(value as T) ? (value as T) : fallback;
}

function readSettings(): Settings {
  let stored: Partial<Record<keyof Settings | 'cashStack', unknown>> = {};
  try {
    stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') ?? {};
  } catch {
    // Storage can be unavailable or hold something unreadable; use defaults.
  }
  const d = DEFAULT_SETTINGS;
  return {
    basis: oneOf(stored.basis, ['latest', 'average5m'], d.basis),
    membership: oneOf(stored.membership, ['all', 'f2p', 'members'], d.membership),
    maxTradeAgeMinutes: oneOf(stored.maxTradeAgeMinutes, TRADE_AGE_OPTIONS.map((o) => o.value), d.maxTradeAgeMinutes),
    minVolume5m: oneOf(stored.minVolume5m, VOLUME_OPTIONS.map((o) => o.value), d.minVolume5m),
    // Earlier versions called the budget a cash stack.
    budget: [stored.budget, stored.cashStack].find((value): value is string => typeof value === 'string') ?? d.budget,
    sortKey: oneOf(stored.sortKey, SORT_KEYS, d.sortKey),
    sortDirection: oneOf(stored.sortDirection, ['asc', 'desc'], d.sortDirection),
  };
}

/** Filter and sort choices, remembered in this browser. */
export function useSettings(): [Settings, (update: Partial<Settings>) => void] {
  const [settings, setSettings] = useState(readSettings);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // Not remembering settings is harmless.
    }
  }, [settings]);

  const update = (changes: Partial<Settings>) => setSettings((current) => ({ ...current, ...changes }));
  return [settings, update];
}
