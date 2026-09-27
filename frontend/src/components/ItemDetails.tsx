import { priceHistoryUrl, wikiItemUrl, type Flip, type PriceBasis } from '../lib/api';
import { formatAge, formatClock, formatGp, formatSignedGp, tone } from '../lib/format';

interface ItemDetailsProps {
  flip: Flip;
  basis: PriceBasis;
  nowSeconds: number;
}

const TAX_CAP = 5_000_000;

export function ItemDetails({ flip, basis, nowSeconds }: ItemDetailsProps) {
  const { item } = flip;
  const traded = (price: number | null, time: number | null) =>
    price === null ? 'No trades seen' : `${formatGp(price)} gp · ${formatAge(time, nowSeconds)}`;
  const averaged = (price: number | null, volume: number | null) =>
    price === null ? 'No trades in window' : `${formatGp(Math.round(price))} gp · ${formatGp(volume ?? 0)} traded`;

  return (
    <div className="details">
      {item.examine && <p className="examine">{item.examine}</p>}

      <div className="details-grid">
        <section>
          <h3>Flip {basis === 'latest' ? '(latest prices)' : '(5-min averages)'}</h3>
          <dl>
            <dt>Sell at</dt>
            <dd>{formatGp(flip.sellPrice)}</dd>
            <dt>GE tax</dt>
            <dd>
              {flip.tax === null ? '—' : flip.tax === 0 ? '0' : `−${formatGp(flip.tax)}`}
              <span className="muted"> {taxNote(flip)}</span>
            </dd>
            <dt>Buy at</dt>
            <dd>{flip.buyPrice === null ? '—' : `−${formatGp(flip.buyPrice)}`}</dd>
            <dt className="total">Margin per item</dt>
            <dd className={`total ${tone(flip.margin)}`}>{formatSignedGp(flip.margin)}</dd>
          </dl>

          <h3>Four-hour estimate</h3>
          <dl>
            <dt>Quantity</dt>
            <dd>
              {flip.quantity === null ? 'Unknown buy limit' : formatGp(flip.quantity)}
              {flip.quantity !== null && (
                <span className="muted"> {flip.quantity === item.buyLimit ? 'buy limit' : 'cash stack'}</span>
              )}
            </dd>
            <dt>Traded in 5 min</dt>
            <dd>
              {formatGp(item.lowPriceVolume5m ?? 0)} at low &middot; {formatGp(item.highPriceVolume5m ?? 0)} at high
            </dd>
            <dt>Fills in 4 hours</dt>
            <dd>
              {formatGp(flip.fillableQuantity)}
              <span className="muted"> {fillNote(flip)}</span>
            </dd>
            <dt>Potential</dt>
            <dd className={tone(flip.potentialProfit)}>{formatSignedGp(flip.potentialProfit)}</dd>
            <dt className="total">Est. profit</dt>
            <dd className={`total ${tone(flip.estimatedProfit)}`}>{formatSignedGp(flip.estimatedProfit)}</dd>
          </dl>
        </section>

        <section>
          <h3>Latest trades</h3>
          <dl>
            <dt>Instant buy (high)</dt>
            <dd>{traded(item.highPrice, item.highPriceTime)}</dd>
            <dt>Instant sell (low)</dt>
            <dd>{traded(item.lowPrice, item.lowPriceTime)}</dd>
          </dl>
          <h3>
            5-minute window
            {item.fiveMinuteTimestamp !== null && (
              <span className="muted"> from {formatClock(item.fiveMinuteTimestamp)}</span>
            )}
          </h3>
          <dl>
            <dt>Average high</dt>
            <dd>{averaged(item.averageHighPrice5m, item.highPriceVolume5m)}</dd>
            <dt>Average low</dt>
            <dd>{averaged(item.averageLowPrice5m, item.lowPriceVolume5m)}</dd>
          </dl>
        </section>

        <section>
          <h3>Item</h3>
          <dl>
            <dt>Type</dt>
            <dd>{item.members ? 'Members' : 'Free-to-play'}</dd>
            <dt>Buy limit</dt>
            <dd>{item.buyLimit === null ? 'Unknown' : `${formatGp(item.buyLimit)} per 4 hours`}</dd>
            <dt>Store value</dt>
            <dd>{formatGp(item.value)}</dd>
            <dt>High alchemy</dt>
            <dd>{formatGp(item.highAlchemy)}</dd>
            <dt>Low alchemy</dt>
            <dd>{formatGp(item.lowAlchemy)}</dd>
            <dt>Item ID</dt>
            <dd>{item.id}</dd>
          </dl>
          <p className="links">
            <a href={wikiItemUrl(item.id)} target="_blank" rel="noreferrer">
              OSRS Wiki
            </a>
            <a href={priceHistoryUrl(item.id)} target="_blank" rel="noreferrer">
              Price history
            </a>
          </p>
        </section>
      </div>
    </div>
  );
}

export function isTaxExempt(flip: Flip): boolean {
  return flip.tax === 0 && flip.sellPrice !== null && flip.sellPrice >= 50;
}

function taxNote(flip: Flip): string {
  if (flip.tax === null || flip.sellPrice === null) return '';
  if (isTaxExempt(flip)) return 'exempt item';
  if (flip.sellPrice < 50) return 'none under 50 gp';
  if (flip.tax === TAX_CAP) return 'capped at 5M';
  return '2% of sale';
}

/** Why the fillable quantity is what it is. */
export function fillNote(flip: Flip): string {
  switch (flip.limitedBy) {
    case 'volume':
      return 'volume-capped';
    case 'cashStack':
      return 'cash-capped';
    case 'buyLimit':
      return 'full limit';
    default:
      return '';
  }
}
