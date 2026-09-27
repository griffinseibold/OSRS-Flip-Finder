import type { ItemHistory } from '../lib/api';
import { formatGp, formatRoi, tone } from '../lib/format';

/** How an item's latest trading compares with its stored history, as a list of facts. */
export function HistoryFacts({ history, className }: { history: ItemHistory; className?: string }) {
  return (
    <dl className={className}>
      <dt>Last 5 minutes</dt>
      <dd>{volumeLine(history)}</dd>
      {history.volume.lastHour !== null && history.volume.typicalHour !== null && (
        <>
          <dt>Last hour</dt>
          <dd>
            {formatGp(history.volume.lastHour)} traded, typically {formatGp(Math.round(history.volume.typicalHour))}
          </dd>
        </>
      )}
      {history.price.current !== null && (
        <>
          <dt>Average price</dt>
          <dd>
            {formatGp(Math.round(history.price.current))} gp
            <Change percent={history.price.change24hPercent} label="a day" />
            <Change percent={history.price.change7dPercent} label="a week" />
            <Change percent={history.price.change30dPercent} label="30 days" />
          </dd>
        </>
      )}
      {history.price.low !== null && history.price.high !== null && (
        <>
          <dt>Price range</dt>
          <dd>
            {formatGp(history.price.low)} to {formatGp(history.price.high)} gp
          </dd>
        </>
      )}
      {history.margin.current !== null && (
        <>
          <dt>Margin before tax</dt>
          <dd>
            {formatGp(history.margin.current)} gp
            {history.margin.typical !== null && `, typically ${formatGp(Math.round(history.margin.typical))}`}
            {history.margin.verdict && <span className="muted"> &middot; {history.margin.verdict}</span>}
          </dd>
        </>
      )}
    </dl>
  );
}

/** How much history a comparison is based on, like "2 days" or "6 hours". */
export function historySpan(history: ItemHistory): string {
  if (history.hourlyDays >= 2) {
    return `${Math.round(history.hourlyDays)} days`;
  }
  const hours = Math.round(Math.max(history.fiveMinuteHours, history.hourlyDays * 24));
  return hours === 1 ? '1 hour' : `${hours} hours`;
}

function Change({ percent, label }: { percent: number | null; label: string }) {
  if (percent === null) {
    return null;
  }
  return (
    <span className="history-change">
      <span className={tone(percent)}>{formatRoi(percent / 100)}</span> in {label}
    </span>
  );
}

function volumeLine(history: ItemHistory): string {
  const { volume } = history;
  if (volume.last5m === null) {
    return 'No trades stored yet';
  }
  const typical = volume.typical5mThisHour ?? volume.typical5m;
  if (typical === null || volume.verdict === null) {
    return `${formatGp(volume.last5m)} traded; not enough history to compare yet`;
  }
  const ratio = volume.ratio !== null ? ` (${volume.ratio}×)` : '';
  return `${formatGp(volume.last5m)} traded, typically ${formatGp(Math.round(typical))}${ratio}: ${volume.verdict}`;
}
