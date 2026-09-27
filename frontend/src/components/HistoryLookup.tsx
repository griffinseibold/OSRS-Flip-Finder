import type { ChatEvent } from '../lib/api';
import { HistoryFacts, historySpan } from './HistoryFacts';

type Lookup = Extract<ChatEvent, { type: 'history' }>;

/** The trading history the language model checked for an answer, so its claims can be checked. */
export function HistoryLookup({ lookup }: { lookup: Lookup }) {
  const { history } = lookup;
  if (!history) {
    return <p className="muted history-missing">No item is called &ldquo;{lookup.search}&rdquo;.</p>;
  }
  return (
    <details className="flip-lookup">
      <summary>
        Based on {historySpan(history)} of history for {history.name}
      </summary>
      <HistoryFacts history={history} className="history-facts" />
    </details>
  );
}
