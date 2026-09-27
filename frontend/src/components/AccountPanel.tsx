import type { Account } from '../lib/api';
import { formatAge, formatCompact, formatGp } from '../lib/format';

const PLUGIN_SETUP_URL = 'https://github.com/griffinseibold/Flip-Finder/blob/master/docs/runelite-plugin.md';

interface AccountPanelProps {
  accounts: Account[] | null;
  selected: Account | null;
  onSelect: (accountHash: number) => void;
  nowSeconds: number;
}

/** The RuneLite account the chat answers for, or how to connect one. */
export function AccountPanel({ accounts, selected, onSelect, nowSeconds }: AccountPanelProps) {
  if (accounts === null) {
    return <aside className="account-panel muted">Loading your account&hellip;</aside>;
  }
  if (!selected) {
    return (
      <aside className="account-panel">
        <h2>Connect RuneLite</h2>
        <p>
          Answers can use your coins, membership and buy limits once the Flip Finder RuneLite plugin reports them:
        </p>
        <ol>
          <li>Install <strong>Flip Finder</strong> from RuneLite&rsquo;s Plugin Hub.</li>
          <li>Turn on its <strong>Send account data</strong> setting.</li>
          <li>Log in and open your bank.</li>
        </ol>
        <a href={PLUGIN_SETUP_URL} target="_blank" rel="noreferrer">
          Setup instructions
        </a>
      </aside>
    );
  }

  return (
    <aside className="account-panel">
      <div className="account-heading">
        <h2>{selected.displayName ?? 'Your account'}</h2>
        <span className="tag">{selected.members ? `Member, ${selected.membershipDays}d left` : 'Free-to-play'}</span>
      </div>
      {accounts.length > 1 && (
        <select
          aria-label="Account"
          value={selected.accountHash}
          onChange={(event) => onSelect(Number(event.target.value))}
        >
          {accounts.map((account) => (
            <option key={account.accountHash} value={account.accountHash}>
              {account.displayName ?? account.accountHash}
            </option>
          ))}
        </select>
      )}
      <p className="muted">Reported by RuneLite {formatAge(selected.capturedAt, nowSeconds)}</p>

      {selected.ironman && (
        <p className="banner banner-warning">Ironman accounts cannot use the Grand Exchange.</p>
      )}

      <dl className="account-facts">
        <dt>Coins</dt>
        <dd>
          {selected.coins === null ? (
            <>
              {formatGp(selected.inventoryCoins)} carried
              <span className="muted"> &middot; open your bank to count the rest</span>
            </>
          ) : (
            <span title={`${formatGp(selected.coins)} gp`}>{formatCompact(selected.coins)} gp</span>
          )}
        </dd>
      </dl>

      <h3>Grand Exchange</h3>
      {selected.geOffers.length === 0 ? (
        <p className="muted">No offers.</p>
      ) : (
        <ul className="account-list">
          {selected.geOffers.map((offer) => (
            <li key={offer.slot}>
              <span>{offer.name ?? `Item ${offer.itemId}`}</span>
              <span className="muted">
                {offerVerb(offer.state)} {formatGp(offer.quantityTraded)}/{formatGp(offer.totalQuantity)}
              </span>
            </li>
          ))}
        </ul>
      )}

      <h3>Buy limits in use</h3>
      {selected.buyLimits.length === 0 ? (
        <p className="muted">None in the last four hours.</p>
      ) : (
        <ul className="account-list">
          {selected.buyLimits.map((use) => (
            <li key={use.itemId}>
              <span>{use.name ?? `Item ${use.itemId}`}</span>
              <span className="muted">
                {formatGp(use.bought)}
                {use.limit !== null && `/${formatGp(use.limit)}`} &middot; resets in{' '}
                {formatDuration(use.resetsAt - nowSeconds)}
              </span>
              {use.limit !== null && (
                <span className="meter" aria-hidden="true">
                  <span style={{ width: `${Math.min((use.bought / use.limit) * 100, 100)}%` }} />
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}

function offerVerb(state: string): string {
  switch (state) {
    case 'BUYING':
      return 'Buying';
    case 'BOUGHT':
      return 'Bought';
    case 'SELLING':
      return 'Selling';
    case 'SOLD':
      return 'Sold';
    default:
      return 'Cancelled';
  }
}

function formatDuration(seconds: number): string {
  const minutes = Math.max(Math.floor(seconds / 60), 0);
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes}m`;
}
