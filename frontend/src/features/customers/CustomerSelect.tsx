import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { customerApi } from '@/api/endpoints';
import { queryKeys } from '@/lib/queryClient';
import { useDebounced } from '@/lib/useDebounced';

const PAGE_SIZE = 50;

interface CustomerSelectProps {
  value: string;
  onChange: (customerId: string) => void;
  /** Text of the empty option, e.g. "Select a customer…" or "All customers". */
  emptyLabel: string;
  /** Name of the selected customer if already known (avoids a lookup when editing). */
  selectedName?: string;
  /** "stacked" puts the search box above the list; "inline" puts them side by side. */
  variant?: 'stacked' | 'inline';
  /** Set by <Field>: forwarded to the <select> so its label and error text stay linked. */
  id?: string;
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
  'aria-required'?: boolean;
}

/**
 * A customer dropdown that works for any number of customers: a search box filters the list on
 * the server, and the currently selected customer always stays visible.
 */
export function CustomerSelect({
  value,
  onChange,
  emptyLabel,
  selectedName,
  variant = 'stacked',
  id,
  ...aria
}: CustomerSelectProps) {
  const [search, setSearch] = useState('');
  const debounced = useDebounced(search);
  const params = { search: debounced, size: PAGE_SIZE };
  const results = useQuery({
    queryKey: queryKeys.customerList(params),
    queryFn: () => customerApi.list(params),
    placeholderData: keepPreviousData,
  });
  const items = results.data?.items ?? [];
  const selectedMissing = Boolean(value) && !items.some((c) => c.id === value);
  const selected = useQuery({
    queryKey: [...queryKeys.customers, 'detail', value],
    queryFn: () => customerApi.get(value),
    enabled: selectedMissing && !selectedName,
  });
  const selectedLabel = selectedName ?? selected.data?.name;
  const hiddenCount = (results.data?.totalItems ?? 0) - items.length;

  return (
    <div className={variant === 'inline' ? 'flex flex-col gap-2 sm:flex-row' : 'space-y-2'}>
      <input
        type="search"
        className={clsx('input', variant === 'inline' && 'min-w-0 sm:w-2/5')}
        placeholder="Search by name or email"
        aria-label="Filter the list by name or email"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <select
        id={id}
        className="input min-w-0"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        {...aria}
      >
        <option value="">{emptyLabel}</option>
        {selectedMissing && selectedLabel && <option value={value}>{selectedLabel}</option>}
        {items.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </select>
      {variant === 'stacked' && hiddenCount > 0 && (
        <p className="text-xs text-slate-500">
          Showing {items.length} of {results.data?.totalItems}. Type a name to find the others.
        </p>
      )}
    </div>
  );
}
