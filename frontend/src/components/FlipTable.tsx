import { Fragment, useState } from 'react';

import { wikiImageUrl, type Flip, type FlipPage, type PriceBasis, type SortDirection, type SortKey } from '../lib/api';
import { formatAge, formatCompact, formatGp, formatRoi, formatSignedGp, tone } from '../lib/format';
import { fillNote, isTaxExempt, ItemDetails } from './ItemDetails';

interface Column {
  key: SortKey;
  label: string;
  detail: (basis: PriceBasis) => string;
}

const COLUMNS: Column[] = [
  { key: 'name', label: 'Item', detail: () => '' },
  { key: 'buyPrice', label: 'Buy at', detail: (basis) => (basis === 'latest' ? 'low price' : 'avg low') },
  { key: 'sellPrice', label: 'Sell at', detail: (basis) => (basis === 'latest' ? 'high price' : 'avg high') },
  { key: 'margin', label: 'Margin', detail: () => 'after tax' },
  { key: 'roi', label: 'ROI', detail: () => 'margin / buy' },
  { key: 'buyLimit', label: 'Buy limit', detail: () => 'per 4 hours' },
  { key: 'potentialProfit', label: 'Potential', detail: () => 'whole limit' },
  { key: 'volume5m', label: '5-min volume', detail: () => 'at low / high' },
  { key: 'estimatedProfit', label: 'Est. profit', detail: () => '4h at that pace' },
  { key: 'lastTradeTime', label: 'Last trade', detail: () => 'older side' },
];

interface FlipTableProps {
  page: FlipPage;
  basis: PriceBasis;
  sortKey: SortKey;
  sortDirection: SortDirection;
  onSort: (key: SortKey) => void;
  onPageChange: (page: number) => void;
  loading: boolean;
  nowSeconds: number;
  /** Whether the server keeps trading history to compare with. */
  history: boolean;
}

export function FlipTable({
  page,
  basis,
  sortKey,
  sortDirection,
  onSort,
  onPageChange,
  loading,
  nowSeconds,
  history,
}: FlipTableProps) {
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const toggle = (id: number) => setExpandedId((current) => (current === id ? null : id));
  const first = page.page * page.size;

  return (
    <section className={loading ? 'results loading' : 'results'} aria-label="Flips" aria-busy={loading}>
      <div className="table-wrap">
        <table className="flips">
          <thead>
            <tr>
              {COLUMNS.map((column) => {
                const active = sortKey === column.key;
                const detail = column.detail(basis);
                return (
                  <th
                    key={column.key}
                    scope="col"
                    className={[
                      column.key === 'name' ? 'col-item' : 'numeric',
                      column.key === 'estimatedProfit' ? 'col-estimate' : '',
                    ].join(' ')}
                    aria-sort={active ? (sortDirection === 'asc' ? 'ascending' : 'descending') : undefined}
                  >
                    <button type="button" onClick={() => onSort(column.key)} className={active ? 'active' : undefined}>
                      <span className="th-label">
                        {column.label}
                        <span className="sort-arrow" aria-hidden="true">
                          {active && sortDirection === 'asc' ? '▲' : '▼'}
                        </span>
                      </span>
                      {detail && <span className="th-detail">{detail}</span>}
                    </button>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {page.items.map((flip) => {
              const { item } = flip;
              const expanded = expandedId === item.id;
              const detailsId = `item-details-${item.id}`;
              return (
                <Fragment key={item.id}>
                  <tr className={expanded ? 'expanded' : undefined} onClick={() => toggle(item.id)}>
                    <th scope="row" className="col-item">
                      <span className="item-cell">
                        {item.icon ? (
                          <img
                            src={wikiImageUrl(item.icon)}
                            alt=""
                            width={24}
                            height={24}
                            loading="lazy"
                            decoding="async"
                            onError={(event) => {
                              event.currentTarget.style.visibility = 'hidden';
                            }}
                          />
                        ) : (
                          <span className="icon-placeholder" />
                        )}
                        <button
                          type="button"
                          className="item-name"
                          aria-expanded={expanded}
                          aria-controls={expanded ? detailsId : undefined}
                          onClick={(event) => {
                            event.stopPropagation();
                            toggle(item.id);
                          }}
                        >
                          {item.name}
                        </button>
                        {item.members && (
                          <span className="tag tag-members" title="Members item">
                            <span aria-hidden="true">{'★'}</span>
                            <span className="visually-hidden">Members item</span>
                          </span>
                        )}
                        {isTaxExempt(flip) && <span className="tag">no tax</span>}
                        {flip.warning && (
                          <span className="tag tag-warning" title={`Trading far from usual: ${flip.warning}`}>
                            unusual
                            <span className="visually-hidden">: {flip.warning}</span>
                          </span>
                        )}
                      </span>
                    </th>
                    <td className="numeric">{formatGp(flip.buyPrice)}</td>
                    <td className="numeric">{formatGp(flip.sellPrice)}</td>
                    <td className={`numeric strong ${tone(flip.margin)}`}>{formatSignedGp(flip.margin)}</td>
                    <td className={`numeric ${tone(flip.roi)}`}>{formatRoi(flip.roi)}</td>
                    <td className="numeric">
                      {item.buyLimit === null ? <span className="secondary">unknown</span> : formatGp(item.buyLimit)}
                    </td>
                    <td
                      className={`numeric ${tone(flip.potentialProfit)}`}
                      title={flip.potentialProfit === null ? undefined : `${formatSignedGp(flip.potentialProfit)} gp`}
                    >
                      {formatCompact(flip.potentialProfit, { signed: true })}
                    </td>
                    <VolumeCell flip={flip} />
                    <td
                      className={`numeric col-estimate ${tone(flip.estimatedProfit)}`}
                      title={flip.estimatedProfit === null ? undefined : `${formatSignedGp(flip.estimatedProfit)} gp`}
                    >
                      <span className="estimate">{formatCompact(flip.estimatedProfit, { signed: true })}</span>
                      {item.buyLimit === null ? (
                        <span className="cell-note">unknown limit</span>
                      ) : (
                        flip.estimatedProfit !== null && <span className="cell-note">{fillNote(flip)}</span>
                      )}
                    </td>
                    <td className="numeric secondary">{formatAge(flip.lastTradeTime, nowSeconds)}</td>
                  </tr>
                  {expanded && (
                    <tr className="details-row" id={detailsId}>
                      <td colSpan={COLUMNS.length}>
                        <ItemDetails flip={flip} basis={basis} nowSeconds={nowSeconds} history={history} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
        {page.total === 0 && <p className="empty">No items match these filters.</p>}
      </div>

      {page.totalPages > 1 && (
        <nav className="pagination" aria-label="Pages">
          <span>
            {formatGp(first + 1)}&ndash;{formatGp(first + page.items.length)} of {formatGp(page.total)}
          </span>
          <button type="button" onClick={() => onPageChange(page.page - 1)} disabled={page.page === 0}>
            Previous
          </button>
          <span aria-current="page">
            Page {formatGp(page.page + 1)} of {formatGp(page.totalPages)}
          </span>
          <button type="button" onClick={() => onPageChange(page.page + 1)} disabled={page.page >= page.totalPages - 1}>
            Next
          </button>
        </nav>
      )}
    </section>
  );
}

/**
 * Trades at each price in the latest five minutes. The meter shows how much of
 * the quantity could fill in four hours at that pace.
 */
function VolumeCell({ flip }: { flip: Flip }) {
  const { item } = flip;
  const low = item.lowPriceVolume5m ?? 0;
  const high = item.highPriceVolume5m ?? 0;
  const coverage =
    flip.quantity && flip.fillableQuantity !== null ? Math.min(flip.fillableQuantity / flip.quantity, 1) : null;
  const title =
    `${formatGp(low)} traded at the low price and ${formatGp(high)} at the high price in the last 5 minutes.` +
    (coverage === null
      ? ''
      : ` At that pace about ${formatGp(flip.fillableQuantity)} of ${formatGp(flip.quantity)} fill in 4 hours.`);

  return (
    <td className="numeric volume" title={title}>
      {flip.volume5m === null ? (
        <span className="secondary">none</span>
      ) : (
        <span>
          {formatCompact(low)} <span className="secondary">/</span> {formatCompact(high)}
        </span>
      )}
      {coverage !== null && (
        <span className="meter" aria-hidden="true">
          <span style={{ width: `${Math.max(coverage * 100, coverage > 0 ? 4 : 0)}%` }} />
        </span>
      )}
    </td>
  );
}
