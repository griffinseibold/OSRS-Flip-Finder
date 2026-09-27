interface SegmentedProps<T extends string> {
  legend: string;
  /** Keep the legend for screen readers only. */
  legendHidden?: boolean;
  name: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
  className?: string;
}

/** A row of mutually exclusive options, built on radio buttons. */
export function Segmented<T extends string>({
  legend,
  legendHidden = false,
  name,
  value,
  options,
  onChange,
  className = 'field segmented',
}: SegmentedProps<T>) {
  return (
    <fieldset className={className}>
      <legend className={legendHidden ? 'visually-hidden' : undefined}>{legend}</legend>
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
