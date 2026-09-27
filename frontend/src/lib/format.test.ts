import { describe, expect, it } from 'vitest';

import { formatAge, formatCompact, formatGp, formatRoi, formatSignedGp, parseCoins } from './format';

describe('formatting', () => {
  it('formats coins with separators and signs', () => {
    expect(formatGp(1_234_567)).toBe('1,234,567');
    expect(formatGp(null)).toBe('—');
    expect(formatSignedGp(3_436)).toBe('+3,436');
    expect(formatSignedGp(-12)).toBe('−12');
    expect(formatSignedGp(0)).toBe('0');
  });

  it('abbreviates large amounts', () => {
    expect(formatCompact(9_999)).toBe('9,999');
    expect(formatCompact(37_796_000, { signed: true })).toBe('+37.8M');
    expect(formatCompact(-1_250_000_000, { signed: true })).toBe('−1.3B');
  });

  it('formats return on investment', () => {
    expect(formatRoi(0.025)).toBe('+2.5%');
    expect(formatRoi(-0.1)).toBe('−10.0%');
    expect(formatRoi(752.846)).toBe('+75,285%');
  });

  it('formats elapsed time', () => {
    expect(formatAge(1_000, 1_030)).toBe('just now');
    expect(formatAge(1_000, 1_000 + 5 * 60)).toBe('5m ago');
    expect(formatAge(1_000, 1_000 + 3 * 3600)).toBe('3h ago');
    expect(formatAge(1_000, 1_000 + 2 * 86_400)).toBe('2d ago');
  });
});

describe('parseCoins', () => {
  it('reads the amounts players type', () => {
    expect(parseCoins('250000')).toBe(250_000);
    expect(parseCoins('250,000')).toBe(250_000);
    expect(parseCoins('500k')).toBe(500_000);
    expect(parseCoins('10M')).toBe(10_000_000);
    expect(parseCoins(' 1.5b ')).toBe(1_500_000_000);
    expect(parseCoins('1.005k')).toBe(1_005);
  });

  it('rejects blank, invalid and zero amounts', () => {
    expect(parseCoins('')).toBeNull();
    expect(parseCoins('lots')).toBeNull();
    expect(parseCoins('10x')).toBeNull();
    expect(parseCoins('0')).toBeNull();
  });
});
