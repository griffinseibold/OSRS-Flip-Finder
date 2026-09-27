import type { FlipPage } from '../lib/api';
import { formatCompact, formatGp } from '../lib/format';

export function Summary({ page, budget }: { page: FlipPage; budget: number | null }) {
  const share = (count: number) => (page.total ? `${Math.round((count / page.total) * 100)}% of shown` : '—');
  const top = page.topFlip;

  return (
    <dl className="summary">
      <div className="tile">
        <dt>{budget === null ? 'Items shown' : `Affordable on ${formatCompact(budget)}`}</dt>
        <dd className="tile-value">{formatGp(page.total)}</dd>
        <dd className="tile-detail">of {formatGp(page.itemCount)} items</dd>
      </div>
      <div className="tile">
        <dt>Profitable</dt>
        <dd className="tile-value">{formatGp(page.profitable)}</dd>
        <dd className="tile-detail">{share(page.profitable)}</dd>
      </div>
      <div className="tile">
        <dt>Losing after tax</dt>
        <dd className="tile-value">{formatGp(page.losing)}</dd>
        <dd className="tile-detail">{share(page.losing)}</dd>
      </div>
      <div className="tile">
        <dt>Top pick, est. 4h profit</dt>
        <dd className="tile-value profit" title={top ? `${formatGp(top.estimatedProfit)} gp` : undefined}>
          {top ? formatCompact(top.estimatedProfit, { signed: true }) : '—'}
        </dd>
        <dd className="tile-detail">{top ? top.item.name : 'Nothing profitable is trading'}</dd>
      </div>
    </dl>
  );
}
