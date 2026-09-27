import { describe, expect, it } from 'vitest';

import { flipsUrl, type FlipQuery } from './api';

const QUERY: FlipQuery = {
  page: 2,
  size: 50,
  sort: 'estimatedProfit',
  direction: 'desc',
  basis: 'latest',
  search: '',
  membership: 'all',
  maxTradeAgeMinutes: 60,
  minVolume5m: 10,
  cashStack: null,
};

describe('flipsUrl', () => {
  it('sends every set parameter', () => {
    expect(flipsUrl({ ...QUERY, search: 'gold leaf', cashStack: 10_000_000 })).toBe(
      '/api/flips?page=2&size=50&sort=estimatedProfit&direction=desc&basis=latest&search=gold+leaf' +
        '&membership=all&maxTradeAgeMinutes=60&minVolume5m=10&cashStack=10000000',
    );
  });

  it('leaves out an empty search and cash stack', () => {
    const url = flipsUrl(QUERY);

    expect(url).not.toContain('search=');
    expect(url).not.toContain('cashStack=');
  });
});
