import { useId } from 'react';

import type { Membership, PriceBasis } from '../lib/api';
import { formatGp, parseCoins } from '../lib/format';
import { TRADE_AGE_OPTIONS, VOLUME_OPTIONS, type Settings } from '../settings';

interface FiltersProps {
  settings: Settings;
  onChange: (update: Partial<Settings>) => void;
  search: string;
  onSearchChange: (search: string) => void;
}

export function Filters({ settings, onChange, search, onSearchChange }: FiltersProps) {
  const id = useId();
  const budget = parseCoins(settings.budget);
  const budgetInvalid = settings.budget.trim() !== '' && budget === null;

  return (
    <section className="filters" aria-label="Filters">
      <div className="field">
        <label htmlFor={`${id}-budget`}>Budget</label>
        <input
          id={`${id}-budget`}
          inputMode="decimal"
          placeholder="e.g. 10m"
          value={settings.budget}
          onChange={(event) => onChange({ budget: event.target.value })}
          aria-invalid={budgetInvalid}
          aria-describedby={`${id}-budget-hint`}
          autoComplete="off"
          spellCheck={false}
        />
        <p id={`${id}-budget-hint`} className={budgetInvalid ? 'hint hint-error' : 'hint'}>
          {budgetInvalid
            ? 'Try 500k, 10m or 1.5b'
            : budget !== null
              ? `${formatGp(budget)} gp; pricier items hidden`
              : 'No limit on coins'}
        </p>
      </div>

      <div className="field">
        <label htmlFor={`${id}-search`}>Search items</label>
        <input
          id={`${id}-search`}
          type="search"
          placeholder="e.g. Gold leaf"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          autoComplete="off"
          spellCheck={false}
        />
      </div>

      <Segmented<PriceBasis>
        legend="Prices"
        name={`${id}-basis`}
        value={settings.basis}
        options={[
          { value: 'latest', label: 'Latest' },
          { value: 'average5m', label: '5-min avg' },
        ]}
        onChange={(basis) => onChange({ basis })}
      />

      <Segmented<Membership>
        legend="Items"
        name={`${id}-membership`}
        value={settings.membership}
        options={[
          { value: 'all', label: 'All' },
          { value: 'f2p', label: 'F2P' },
          { value: 'members', label: 'Members' },
        ]}
        onChange={(membership) => onChange({ membership })}
      />

      <div className="field">
        <label htmlFor={`${id}-age`}>Both sides traded within</label>
        <select
          id={`${id}-age`}
          value={settings.maxTradeAgeMinutes}
          onChange={(event) => onChange({ maxTradeAgeMinutes: Number(event.target.value) })}
          disabled={settings.basis !== 'latest'}
          aria-describedby={`${id}-age-hint`}
        >
          {TRADE_AGE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <p id={`${id}-age-hint`} className="hint">
          {settings.basis === 'latest' ? 'Older prices are unreliable' : 'Averages are from the last 5 min'}
        </p>
      </div>

      <div className="field">
        <label htmlFor={`${id}-volume`}>Traded in last 5 min</label>
        <select
          id={`${id}-volume`}
          value={settings.minVolume5m}
          onChange={(event) => onChange({ minVolume5m: Number(event.target.value) })}
          aria-describedby={`${id}-volume-hint`}
        >
          {VOLUME_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <p id={`${id}-volume-hint`} className="hint">
          Items bought and sold
        </p>
      </div>
    </section>
  );
}

interface SegmentedProps<T extends string> {
  legend: string;
  name: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}

function Segmented<T extends string>({ legend, name, value, options, onChange }: SegmentedProps<T>) {
  return (
    <fieldset className="field segmented">
      <legend>{legend}</legend>
      <div className="segmented-options">
        {options.map((option) => (
          <label key={option.value} className={option.value === value ? 'selected' : undefined}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={option.value === value}
              onChange={() => onChange(option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
