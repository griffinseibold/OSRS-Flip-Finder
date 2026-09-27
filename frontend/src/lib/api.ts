/** One Grand Exchange item. Prices are in coins. */
export interface Item {
  id: number;
  name: string;
  examine: string | null;
  members: boolean;
  lowAlchemy: number | null;
  highAlchemy: number | null;
  /** Maximum quantity that can be bought every four hours. */
  buyLimit: number | null;
  value: number | null;
  icon: string | null;
  /** Most recent instant-buy price. */
  highPrice: number | null;
  /** Unix seconds. */
  highPriceTime: number | null;
  /** Most recent instant-sell price. */
  lowPrice: number | null;
  /** Unix seconds. */
  lowPriceTime: number | null;
  averageHighPrice5m: number | null;
  /** Items traded at the high price in the latest five-minute window. */
  highPriceVolume5m: number | null;
  averageLowPrice5m: number | null;
  /** Items traded at the low price in the latest five-minute window. */
  lowPriceVolume5m: number | null;
  /** Start of the five-minute window, in Unix seconds. */
  fiveMinuteTimestamp: number | null;
  updatedAt: string;
}

export type LimitedBy = 'buyLimit' | 'cashStack' | 'volume';

/** An item priced as a flip by GET /api/flips. */
export interface Flip {
  item: Item;
  buyPrice: number | null;
  sellPrice: number | null;
  tax: number | null;
  /** Coins made per item after tax; negative when the flip loses money. */
  margin: number | null;
  roi: number | null;
  /** One buy limit, reduced to what the cash stack affords. */
  quantity: number | null;
  potentialProfit: number | null;
  volume5m: number | null;
  /** Quantity that could fill in four hours at the five-minute pace. */
  fillableQuantity: number | null;
  estimatedProfit: number | null;
  limitedBy: LimitedBy | null;
  /** Unix seconds of the older of the latest instant-buy and instant-sell trades. */
  lastTradeTime: number | null;
}

export interface FlipPage {
  items: Flip[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  itemCount: number;
  profitable: number;
  losing: number;
  searchMatches: number | null;
  pricesAsOf: number | null;
  topFlip: Flip | null;
}

export type PriceBasis = 'latest' | 'average5m';
export type Membership = 'all' | 'f2p' | 'members';
export type SortDirection = 'asc' | 'desc';
export type SortKey =
  | 'estimatedProfit'
  | 'potentialProfit'
  | 'margin'
  | 'roi'
  | 'buyPrice'
  | 'sellPrice'
  | 'buyLimit'
  | 'volume5m'
  | 'lastTradeTime'
  | 'name';

export interface FlipQuery {
  page: number;
  size: number;
  sort: SortKey;
  direction: SortDirection;
  basis: PriceBasis;
  search: string;
  membership: Membership;
  maxTradeAgeMinutes: number;
  minVolume5m: number;
  cashStack: number | null;
}

export function flipsUrl(query: FlipQuery): string {
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (value !== null && value !== '') {
      params.set(name, String(value));
    }
  }
  return `/api/flips?${params}`;
}

export async function fetchFlips(url: string, signal?: AbortSignal): Promise<FlipPage> {
  const response = await fetch(url, { headers: { Accept: 'application/json' }, signal });
  if (!response.ok) {
    throw new Error(`GET /api/flips returned HTTP ${response.status}`);
  }
  return (await response.json()) as FlipPage;
}

export function wikiImageUrl(icon: string): string {
  return `https://oldschool.runescape.wiki/images/${encodeURIComponent(icon.replaceAll(' ', '_'))}`;
}

export function wikiItemUrl(id: number): string {
  return `https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=${id}`;
}

export function priceHistoryUrl(id: number): string {
  return `https://prices.runescape.wiki/osrs/item/${id}`;
}
