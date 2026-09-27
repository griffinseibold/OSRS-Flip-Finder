const MINUS = '−';
const MISSING = '—';

const wholeNumber = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const compactNumber = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 });
const timeOfDay = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });

/** 1234567 -> "1,234,567". */
export function formatGp(value: number | null): string {
  return value === null ? MISSING : sign(value, '') + wholeNumber.format(Math.abs(value));
}

/** 1234 -> "+1,234", -1234 -> "−1,234". */
export function formatSignedGp(value: number | null): string {
  return value === null ? MISSING : sign(value, '+') + wholeNumber.format(Math.abs(value));
}

/** Exact below 10,000, then "12.5K", "37.8M", "1.2B". */
export function formatCompact(value: number | null, { signed = false } = {}): string {
  if (value === null) {
    return MISSING;
  }
  const magnitude = Math.abs(value);
  const digits = magnitude < 10_000 ? wholeNumber.format(magnitude) : compactNumber.format(magnitude);
  return sign(value, signed ? '+' : '') + digits;
}

/** 0.025 -> "+2.5%"; ratios of 10 (1,000%) or more drop the decimal. */
export function formatRoi(ratio: number | null): string {
  if (ratio === null) {
    return MISSING;
  }
  const percent = Math.abs(ratio * 100);
  const digits = percent >= 1000 ? wholeNumber.format(percent) : percent.toFixed(1);
  return `${sign(ratio, '+')}${digits}%`;
}

/** CSS class colouring a gain or a loss. */
export function tone(value: number | null): string {
  if (value === null || value === 0) return '';
  return value > 0 ? 'profit' : 'loss';
}

function sign(value: number, positive: string): string {
  if (value < 0) return MINUS;
  if (value > 0) return positive;
  return '';
}

/** Elapsed time since a Unix-seconds timestamp, e.g. "4m ago". */
export function formatAge(timestamp: number | null, nowSeconds: number): string {
  if (timestamp === null) {
    return MISSING;
  }
  const seconds = Math.max(nowSeconds - timestamp, 0);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86_400)}d ago`;
}

export function formatClock(timestamp: number): string {
  return timeOfDay.format(new Date(timestamp * 1000));
}

const MULTIPLIERS: Record<string, number> = { k: 1e3, m: 1e6, b: 1e9 };

/**
 * Parses a coin amount the way players write it: "250000", "250,000",
 * "500k", "10m" or "1.5b". Returns null when blank, invalid or not positive.
 */
export function parseCoins(input: string): number | null {
  const match = /^(\d+(?:\.\d+)?)([kmb])?$/i.exec(input.replace(/[\s,_]/g, ''));
  if (!match?.[1]) {
    return null;
  }
  const suffix = match[2]?.toLowerCase();
  const coins = Math.round(Number(match[1]) * (suffix ? (MULTIPLIERS[suffix] ?? 1) : 1));
  return coins > 0 ? coins : null;
}
