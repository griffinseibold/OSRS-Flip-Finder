import { wikiImageUrl, type ChatEvent } from '../lib/api';
import { formatCompact, formatGp, formatSignedGp, tone } from '../lib/format';
import { fillNote } from './ItemDetails';

type Lookup = Extract<ChatEvent, { type: 'flips' }>;

/** The flips the language model looked up for an answer, so its numbers can be checked. */
export function FlipLookup({ lookup }: { lookup: Lookup }) {
  const subject = lookup.search
    ? `matching “${lookup.search}”`
    : lookup.budget !== null
      ? `for ${formatCompact(lookup.budget)} gp`
      : 'right now';

  return (
    <details className="flip-lookup">
      <summary>
        Based on {lookup.items.length === 1 ? '1 flip' : `${lookup.items.length} flips`} {subject}
      </summary>
      {lookup.items.length === 0 ? (
        <p className="muted">Nothing matched.</p>
      ) : (
        <div className="table-wrap">
          <table className="flips flips-compact">
            <thead>
              <tr>
                <th scope="col">Item</th>
                <th scope="col" className="numeric">Buy at</th>
                <th scope="col" className="numeric">Sell at</th>
                <th scope="col" className="numeric">Margin</th>
                <th scope="col" className="numeric">Est. profit</th>
              </tr>
            </thead>
            <tbody>
              {lookup.items.map((flip) => (
                <tr key={flip.item.id}>
                  <th scope="row">
                    <span className="item-cell">
                      {flip.item.icon && (
                        <img src={wikiImageUrl(flip.item.icon)} alt="" width={20} height={20} loading="lazy" />
                      )}
                      {flip.item.name}
                    </span>
                  </th>
                  <td className="numeric">{formatGp(flip.buyPrice)}</td>
                  <td className="numeric">{formatGp(flip.sellPrice)}</td>
                  <td className={`numeric ${tone(flip.margin)}`}>{formatSignedGp(flip.margin)}</td>
                  <td className={`numeric ${tone(flip.estimatedProfit)}`}>
                    {formatCompact(flip.estimatedProfit, { signed: true })}
                    {flip.estimatedProfit !== null && <span className="cell-note">{fillNote(flip)}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </details>
  );
}
