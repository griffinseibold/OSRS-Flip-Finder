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

export type LimitedBy = 'buyLimit' | 'budget' | 'volume';

/** An item priced as a flip by GET /api/flips. */
export interface Flip {
  item: Item;
  buyPrice: number | null;
  sellPrice: number | null;
  tax: number | null;
  /** Coins made per item after tax; negative when the flip loses money. */
  margin: number | null;
  roi: number | null;
  /** One buy limit, reduced to what the budget affords. */
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
  /** Coins available to flip with. */
  budget: number | null;
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

export interface Features {
  /** Answers questions with the homelab's language model. */
  chat: boolean;
  /** Accepts account data from the RuneLite plugin. */
  runelite: boolean;
}

export async function fetchFeatures(): Promise<Features> {
  const response = await fetch('/api/features', { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`GET /api/features returned HTTP ${response.status}`);
  }
  return (await response.json()) as Features;
}

/** An account as the RuneLite plugin last reported it. */
export interface Account {
  accountHash: number;
  displayName: string | null;
  members: boolean;
  membershipDays: number;
  ironman: boolean;
  inventoryCoins: number;
  bankCoins: number | null;
  bankCoinsSeenAt: number | null;
  /** Inventory and bank together; null until the bank has been seen. */
  coins: number | null;
  geOffers: {
    slot: number;
    itemId: number;
    name: string | null;
    state: string;
    price: number;
    totalQuantity: number;
    quantityTraded: number;
    spent: number;
  }[];
  buyLimits: {
    itemId: number;
    name: string | null;
    limit: number | null;
    bought: number;
    remaining: number | null;
    /** Unix seconds. */
    resetsAt: number;
  }[];
  /** Unix seconds. */
  capturedAt: number;
}

export async function fetchAccounts(): Promise<Account[]> {
  const response = await fetch('/api/runelite/accounts', { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`GET /api/runelite/accounts returned HTTP ${response.status}`);
  }
  return (await response.json()) as Account[];
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

/** What /api/chat streams back, one JSON object per line. */
export type ChatEvent =
  | { type: 'tool'; name: string; search: string; budget: number | null }
  | { type: 'flips'; search: string; budget: number | null; sort: SortKey; total: number; items: Flip[] }
  | { type: 'text'; text: string }
  | { type: 'error'; message: string }
  | { type: 'done' };

/** Asks the homelab's language model, passing each event to onEvent as it arrives. */
export async function streamChat(
  account: number | null,
  messages: ChatMessage[],
  onEvent: (event: ChatEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/x-ndjson' },
    body: JSON.stringify({ account, messages }),
    signal,
  });
  if (!response.ok || !response.body) {
    throw new Error(`POST /api/chat returned HTTP ${response.status}`);
  }
  await readLines(response.body, (line) => onEvent(JSON.parse(line) as ChatEvent));
}

/** Calls onLine for each line of a streamed body, however the lines are split into chunks. */
export async function readLines(body: ReadableStream<Uint8Array>, onLine: (line: string) => void): Promise<void> {
  const reader = body.getReader();
  // Streaming mode keeps a character split between chunks intact.
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      buffer += decoder.decode();
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let newline = buffer.indexOf('\n');
    while (newline >= 0) {
      const line = buffer.slice(0, newline).trim();
      buffer = buffer.slice(newline + 1);
      if (line) {
        onLine(line);
      }
      newline = buffer.indexOf('\n');
    }
  }
  if (buffer.trim()) {
    onLine(buffer.trim());
  }
}

export function wikiImageUrl(icon: string): string {
  return `https://oldschool.runescape.wiki/images/${encodeURIComponent(icon.replaceAll(' ', '_'))}`;
}

export function wikiItemUrl(id: number): string {
  return `https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=${id}`;
}

export const BUYING_LIMITS_URL = 'https://oldschool.runescape.wiki/w/Grand_Exchange/Buying_limits';

export function priceHistoryUrl(id: number): string {
  return `https://prices.runescape.wiki/osrs/item/${id}`;
}
