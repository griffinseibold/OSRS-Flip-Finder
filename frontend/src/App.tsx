import { useState } from 'react';

import { Filters } from './components/Filters';
import { FlipTable } from './components/FlipTable';
import { Segmented } from './components/Segmented';
import { Summary } from './components/Summary';
import { useDebouncedValue } from './hooks/useDebouncedValue';
import { useFlips } from './hooks/useFlips';
import { useNow } from './hooks/useNow';
import { useTheme, type Theme } from './hooks/useTheme';
import { BUYING_LIMITS_URL, type SortKey } from './lib/api';
import { formatAge, formatClock, formatGp, parseCoins } from './lib/format';
import { useSettings } from './settings';

const PAGE_SIZE = 50;

export function App() {
  const [settings, update] = useSettings();
  const [theme, setTheme] = useTheme();
  const [search, setSearch] = useState('');
  // Typed fields only query the server once typing pauses.
  const debouncedSearch = useDebouncedValue(search.trim(), 250);
  const budget = parseCoins(useDebouncedValue(settings.budget, 250));
  const now = useNow();

  const filters = {
    sort: settings.sortKey,
    direction: settings.sortDirection,
    basis: settings.basis,
    search: debouncedSearch,
    membership: settings.membership,
    maxTradeAgeMinutes: settings.maxTradeAgeMinutes,
    minVolume5m: settings.minVolume5m,
    budget,
  };
  // Changing any filter or the sort goes back to the first page.
  const filterKey = JSON.stringify(filters);
  const [page, setPage] = useState(0);
  const [pagedFilterKey, setPagedFilterKey] = useState(filterKey);
  if (pagedFilterKey !== filterKey) {
    setPagedFilterKey(filterKey);
    setPage(0);
  }

  const { data, error, loading, reload } = useFlips({ ...filters, page, size: PAGE_SIZE });

  const hiddenMatches = data?.searchMatches != null ? data.searchMatches - data.total : 0;

  const onSort = (key: SortKey) =>
    update(
      key === settings.sortKey
        ? { sortDirection: settings.sortDirection === 'asc' ? 'desc' : 'asc' }
        : { sortKey: key, sortDirection: key === 'name' ? 'asc' : 'desc' },
    );

  return (
    <div className="app">
      <header className="masthead">
        <div className="brand">
          <img src="/favicon.svg" alt="" width={36} height={36} />
          <div>
            <h1>Flip Finder</h1>
            <p>Old School RuneScape Grand Exchange flips ranked by profit after tax</p>
          </div>
        </div>
        <div className="status">
          {data?.pricesAsOf != null && (
            <span>
              Prices as of {formatClock(data.pricesAsOf)}{' '}
              <span className="muted">({formatAge(data.pricesAsOf, now)})</span>
            </span>
          )}
          <button type="button" className="button" onClick={reload} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
          <Segmented<Theme>
            legend="Theme"
            legendHidden
            className="segmented theme-switch"
            name="theme"
            value={theme}
            options={[
              { value: 'auto', label: 'Auto' },
              { value: 'light', label: 'Light' },
              { value: 'dark', label: 'Dark' },
            ]}
            onChange={setTheme}
          />
        </div>
      </header>

      <main>
        {error && data && (
          <p className="banner banner-warning" role="alert">
            Couldn&rsquo;t load prices ({error.message}). Showing the last results; retrying shortly.
          </p>
        )}

        {!data && error && (
          <div className="state" role="alert">
            <h2>Can&rsquo;t reach the Flip Finder API</h2>
            <p>{error.message}</p>
            <p className="muted">
              Check that the backend is running. Locally, start it with <code>./mvnw spring-boot:run</code> in{' '}
              <code>backend/</code>.
            </p>
            <button type="button" className="button" onClick={reload}>
              Try again
            </button>
          </div>
        )}

        {!data && !error && (
          <div className="state" aria-busy="true">
            <p>Loading Grand Exchange prices&hellip;</p>
          </div>
        )}

        {data?.itemCount === 0 && (
          <div className="state">
            <h2>No prices yet</h2>
            <p className="muted">
              The server imports prices from the RuneScape Wiki when it starts and every five minutes. This page checks
              again automatically.
            </p>
          </div>
        )}

        {data && data.itemCount > 0 && (
          <>
            <Filters
              settings={settings}
              onChange={update}
              search={search}
              onSearchChange={setSearch}
            />
            <Summary page={data} budget={budget} />
            {hiddenMatches > 0 && (
              <p className="banner">
                {formatGp(hiddenMatches)} {data.total > 0 && 'more '}
                {hiddenMatches === 1 ? 'item matches' : 'items match'} &ldquo;{debouncedSearch}&rdquo; but{' '}
                {hiddenMatches === 1 ? 'is' : 'are'} hidden by your filters.{' '}
                <button
                  type="button"
                  className="link-button"
                  onClick={() => update({ membership: 'all', maxTradeAgeMinutes: 0, minVolume5m: 0, budget: '' })}
                >
                  Clear filters
                </button>
              </p>
            )}
            <FlipTable
              page={data}
              basis={settings.basis}
              sortKey={settings.sortKey}
              sortDirection={settings.sortDirection}
              onSort={onSort}
              onPageChange={setPage}
              loading={loading}
              nowSeconds={now}
            />
            <Method />
          </>
        )}
      </main>

      <footer className="footer">
        Prices come from the{' '}
        <a href="https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices" target="_blank" rel="noreferrer">
          RuneScape Wiki real-time prices API
        </a>{' '}
        through this server&rsquo;s <a href="/swagger-ui.html">flips API</a>.
      </footer>
    </div>
  );
}

function Method() {
  return (
    <details className="method">
      <summary>How flips are calculated</summary>
      <ul>
        <li>
          <strong>Buy at</strong> is the latest instant-sell (low) price and <strong>sell at</strong> is the latest
          instant-buy (high) price: place a buy offer at the first and a sell offer at the second. The 5-minute average
          option uses the average prices traded in the latest five-minute window instead.
        </li>
        <li>
          <strong>Margin</strong> is the sell price minus Grand Exchange tax minus the buy price. The seller pays 2% of the
          sale price, rounded down and capped at 5M per item. Sales under 50 gp and{' '}
          <a href="https://oldschool.runescape.wiki/w/Grand_Exchange" target="_blank" rel="noreferrer">
            exempt items
          </a>{' '}
          such as bonds pay nothing. <strong>ROI</strong> is the margin as a share of the buy price.
        </li>
        <li>
          <strong>Potential</strong> is the margin across one whole buy limit, which resets every four hours, or fewer
          items if your budget can&rsquo;t afford the whole limit. Limits come from the wiki&rsquo;s{' '}
          <a href={BUYING_LIMITS_URL} target="_blank" rel="noreferrer">
            buying limits
          </a>{' '}
          list. About 500 items have no known limit; they get no potential or estimated profit and sort last, since
          assuming no limit would overstate them.
        </li>
        <li>
          <strong>5-min volume</strong> is how many items traded at the low and at the high price in the latest
          five-minute window. Your buy offer fills from trades at the low price and your sell offer from trades at the
          high price, so the slower side sets the pace. The bar shows how much of the quantity that pace fills in four
          hours.
        </li>
        <li>
          <strong>Est. profit</strong> is the margin across what could fill in four hours (48 five-minute windows) at
          that pace, capped by the buy limit and your budget. It assumes you get every trade at your price, so treat it as
          an upper bound: other flippers compete for the same volume.
        </li>
      </ul>
    </details>
  );
}
