import { useEffect, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, Plus, Search } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { invoiceApi } from '@/api/endpoints';
import { INVOICE_STATUSES, type InvoiceQuery, type InvoiceStatus } from '@/api/types';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingState } from '@/components/ui/Spinner';
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/States';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { formatDate, formatMoney, humaniseStatus } from '@/lib/format';
import { CustomerSelect } from '@/features/customers/CustomerSelect';
import { queryKeys } from '@/lib/queryClient';
import { useDebounced } from '@/lib/useDebounced';

type SortKey = 'issueDate' | 'dueDate' | 'number' | 'customer' | 'total' | 'status';

const columns: { key: SortKey; label: string; className?: string }[] = [
  { key: 'number', label: 'Number' },
  { key: 'customer', label: 'Customer' },
  { key: 'issueDate', label: 'Issued', className: 'hidden md:table-cell' },
  { key: 'dueDate', label: 'Due' },
  { key: 'status', label: 'Status' },
  { key: 'total', label: 'Total', className: 'hidden sm:table-cell text-right' },
];

export function InvoicesPage() {
  const [params, setParams] = useSearchParams();
  const [searchText, setSearchText] = useState(params.get('search') ?? '');
  const debouncedSearch = useDebounced(searchText);

  const status = params.get('status') as InvoiceStatus | null;
  const sort = (params.get('sort') as SortKey | null) ?? 'issueDate';
  const direction = (params.get('direction') as 'asc' | 'desc' | null) ?? 'desc';
  const query: InvoiceQuery = {
    status: status ? [status] : undefined,
    customerId: params.get('customerId') ?? undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    search: params.get('search') ?? undefined,
    sort,
    direction,
    page: Number(params.get('page') ?? 0),
    size: 20,
  };

  function update(changes: Record<string, string | null>, resetPage = true) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    if (resetPage) next.delete('page');
    setParams(next, { replace: true });
  }

  useEffect(() => {
    if ((params.get('search') ?? '') !== debouncedSearch) update({ search: debouncedSearch || null });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch]);

  const invoices = useQuery({
    queryKey: queryKeys.invoiceList(query),
    queryFn: () => invoiceApi.list(query),
    placeholderData: keepPreviousData,
  });

  function toggleSort(key: SortKey) {
    const nextDirection = sort === key && direction === 'desc' ? 'asc' : 'desc';
    update({ sort: key, direction: nextDirection });
  }

  const hasFilters = Boolean(status || query.customerId || query.from || query.to || query.search);

  return (
    <>
      <PageHeader
        title="Invoices"
        actions={
          <Link
            to="/invoices/new"
            className="inline-flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" aria-hidden="true" /> New invoice
          </Link>
        }
      />

      <div className="card">
        <form
          role="search"
          aria-label="Filter invoices"
          className="grid gap-3 border-b border-slate-200 p-4 sm:grid-cols-2 lg:grid-cols-6"
          onSubmit={(e) => e.preventDefault()}
        >
          <div className="relative lg:col-span-2">
            <label htmlFor="invoice-search" className="sr-only">
              Search by number or customer
            </label>
            <Search
              className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400"
              aria-hidden="true"
            />
            <input
              id="invoice-search"
              type="search"
              className="input pl-9"
              placeholder="Search number or customer"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="status-filter" className="sr-only">
              Status
            </label>
            <select
              id="status-filter"
              className="input"
              value={status ?? ''}
              onChange={(e) => update({ status: e.target.value || null })}
            >
              <option value="">All statuses</option>
              {INVOICE_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {humaniseStatus(s)}
                </option>
              ))}
            </select>
          </div>
          <div className="sm:col-span-2 lg:col-span-3">
            <label htmlFor="customer-filter" className="sr-only">
              Customer
            </label>
            <CustomerSelect
              id="customer-filter"
              variant="inline"
              value={query.customerId ?? ''}
              onChange={(id) => update({ customerId: id || null })}
              emptyLabel="All customers"
            />
          </div>
          <div className="grid grid-cols-2 gap-2 sm:col-span-2">
            <div className="min-w-0">
              <label htmlFor="from-filter" className="sr-only">
                Issued from
              </label>
              <input
                id="from-filter"
                type="date"
                className="input min-w-0"
                value={query.from ?? ''}
                onChange={(e) => update({ from: e.target.value || null })}
                title="Issued from"
              />
            </div>
            <div className="min-w-0">
              <label htmlFor="to-filter" className="sr-only">
                Issued to
              </label>
              <input
                id="to-filter"
                type="date"
                className="input min-w-0"
                value={query.to ?? ''}
                onChange={(e) => update({ to: e.target.value || null })}
                title="Issued to"
              />
            </div>
          </div>
        </form>

        {invoices.isLoading && <LoadingState />}
        {invoices.error && <ErrorState error={invoices.error} onRetry={() => invoices.refetch()} />}
        {invoices.data && invoices.data.items.length === 0 && (
          <EmptyState
            title={hasFilters ? 'No invoices match these filters' : 'No invoices yet'}
            description={
              hasFilters ? 'Try widening your search.' : 'Create your first invoice to get started.'
            }
            action={
              hasFilters ? (
                <button
                  type="button"
                  className="text-sm font-medium text-brand-600 hover:underline"
                  onClick={() => {
                    setSearchText('');
                    setParams(new URLSearchParams(), { replace: true });
                  }}
                >
                  Clear filters
                </button>
              ) : undefined
            }
          />
        )}
        {invoices.data && invoices.data.items.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200">
                <thead className="bg-slate-50">
                  <tr>
                    {columns.map((col) => (
                      <th
                        key={col.key}
                        scope="col"
                        className={`table-head ${col.className ?? ''}`}
                        aria-sort={
                          sort === col.key ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'
                        }
                      >
                        <button
                          type="button"
                          className="inline-flex items-center gap-1 uppercase hover:text-slate-900"
                          onClick={() => toggleSort(col.key)}
                        >
                          {col.label}
                          {sort === col.key &&
                            (direction === 'asc' ? (
                              <ArrowUp className="h-3 w-3" aria-hidden="true" />
                            ) : (
                              <ArrowDown className="h-3 w-3" aria-hidden="true" />
                            ))}
                        </button>
                      </th>
                    ))}
                    <th scope="col" className="table-head text-right">
                      Balance
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {invoices.data.items.map((invoice) => (
                    <tr key={invoice.id} className="hover:bg-slate-50">
                      <td className="table-cell font-medium">
                        <Link to={`/invoices/${invoice.id}`} className="text-brand-700 hover:underline">
                          {invoice.number}
                        </Link>
                      </td>
                      <td className="table-cell max-w-[12rem] truncate">{invoice.customerName}</td>
                      <td className="table-cell hidden text-slate-600 md:table-cell">
                        {formatDate(invoice.issueDate)}
                      </td>
                      <td className="table-cell text-slate-600">
                        {formatDate(invoice.dueDate)}
                        {invoice.daysOverdue > 0 && (
                          <span className="block text-xs font-medium text-red-600">
                            {invoice.daysOverdue}d overdue
                          </span>
                        )}
                      </td>
                      <td className="table-cell">
                        <StatusBadge status={invoice.status} />
                      </td>
                      <td className="table-cell hidden text-right sm:table-cell">
                        {formatMoney(invoice.total, invoice.currency)}
                      </td>
                      <td className="table-cell text-right font-medium">
                        {formatMoney(invoice.balanceDue, invoice.currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={invoices.data.page}
              totalPages={invoices.data.totalPages}
              totalItems={invoices.data.totalItems}
              onPageChange={(page) => update({ page: String(page) }, false)}
            />
          </>
        )}
      </div>
    </>
  );
}
