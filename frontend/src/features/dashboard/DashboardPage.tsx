import { useQuery } from '@tanstack/react-query';
import { AlertCircle, CheckCircle2, Plus, Wallet } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { dashboardApi } from '@/api/endpoints';
import { INVOICE_STATUSES } from '@/api/types';
import type { DashboardSummary } from '@/api/types';
import { LoadingState } from '@/components/ui/Spinner';
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/States';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { DevToolsCard } from '@/features/dev/DevToolsCard';
import { formatDate, formatMoney } from '@/lib/format';
import { queryKeys } from '@/lib/queryClient';

const BUCKET_COLOURS = ['#3b6ef5', '#f59e0b', '#f97316', '#ef4444', '#991b1b'];

export function DashboardPage() {
  const summary = useQuery({ queryKey: queryKeys.dashboard, queryFn: dashboardApi.summary });

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={summary.data ? `As of ${formatDate(summary.data.asOf)}` : undefined}
        actions={
          <Link
            to="/invoices/new"
            className="inline-flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" aria-hidden="true" /> New invoice
          </Link>
        }
      />
      <div className="mb-6">
        <DevToolsCard />
      </div>
      {summary.isLoading && <LoadingState />}
      {summary.error && <ErrorState error={summary.error} onRetry={() => summary.refetch()} />}
      {summary.data && <DashboardContent data={summary.data} />}
    </>
  );
}

function DashboardContent({ data }: { data: DashboardSummary }) {
  const { currency } = data;
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Total outstanding"
          value={formatMoney(data.totalOutstanding, currency)}
          icon={Wallet}
          tone="text-brand-600"
        />
        <StatCard
          label="Total overdue"
          value={formatMoney(data.totalOverdue, currency)}
          icon={AlertCircle}
          tone="text-red-600"
        />
        <StatCard
          label="Paid this month"
          value={formatMoney(data.paidThisMonth, currency)}
          icon={CheckCircle2}
          tone="text-emerald-600"
        />
      </div>

      <section aria-labelledby="status-counts" className="card p-5">
        <h2 id="status-counts" className="text-sm font-semibold text-slate-900">
          Invoices by status
        </h2>
        <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {INVOICE_STATUSES.map((status) => (
            <li key={status}>
              <Link
                to={`/invoices?status=${status}`}
                className="flex flex-col gap-2 rounded-md border border-slate-200 p-3 hover:border-brand-300 hover:bg-brand-50/40"
              >
                <StatusBadge status={status} />
                <span className="text-xl font-semibold">{data.countsByStatus[status] ?? 0}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <div className="grid gap-6 lg:grid-cols-5">
        <section aria-labelledby="ageing-title" className="card p-5 lg:col-span-3">
          <h2 id="ageing-title" className="text-sm font-semibold text-slate-900">
            Outstanding by age
          </h2>
          <div className="mt-4 h-64" role="img" aria-label={ageingDescription(data)}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.ageing} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} tickFormatter={(v: number) => compact(v)} width={56} />
                <Tooltip formatter={(v: number) => formatMoney(v, currency)} />
                <Bar dataKey="amount" name="Outstanding" radius={[4, 4, 0, 0]}>
                  {data.ageing.map((bucket, i) => (
                    <Cell key={bucket.key} fill={BUCKET_COLOURS[i % BUCKET_COLOURS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
          <table className="mt-4 w-full text-sm">
            <caption className="sr-only">Outstanding amounts by age</caption>
            <thead>
              <tr className="text-left text-xs text-slate-500">
                <th className="py-1 font-medium">Bucket</th>
                <th className="py-1 text-right font-medium">Invoices</th>
                <th className="py-1 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody>
              {data.ageing.map((b) => (
                <tr key={b.key} className="border-t border-slate-100">
                  <td className="py-1.5">{b.label}</td>
                  <td className="py-1.5 text-right">{b.count}</td>
                  <td className="py-1.5 text-right font-medium">{formatMoney(b.amount, currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section aria-labelledby="top-overdue" className="card lg:col-span-2">
          <h2 id="top-overdue" className="px-5 pt-5 text-sm font-semibold text-slate-900">
            Most overdue
          </h2>
          {data.topOverdue.length === 0 ? (
            <EmptyState
              title="Nothing overdue"
              description="All open invoices are within their payment terms."
            />
          ) : (
            <ul className="mt-3 divide-y divide-slate-100">
              {data.topOverdue.map((invoice) => (
                <li key={invoice.id}>
                  <Link
                    to={`/invoices/${invoice.id}`}
                    className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-slate-50"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-slate-900">{invoice.customerName}</p>
                      <p className="text-xs text-slate-500">
                        {invoice.number} · due {formatDate(invoice.dueDate)}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-semibold">{formatMoney(invoice.balanceDue, currency)}</p>
                      <p className="text-xs font-medium text-red-600">{invoice.daysOverdue} days overdue</p>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}

function StatCard({
  label,
  value,
  icon: Icon,
  tone,
}: {
  label: string;
  value: string;
  icon: typeof Wallet;
  tone: string;
}) {
  return (
    <div className="card flex items-center gap-4 p-5">
      <div className={`rounded-full bg-slate-50 p-3 ${tone}`}>
        <Icon className="h-5 w-5" aria-hidden="true" />
      </div>
      <div>
        <p className="text-sm text-slate-500">{label}</p>
        <p className="mt-0.5 text-xl font-semibold tracking-tight">{value}</p>
      </div>
    </div>
  );
}

function compact(value: number): string {
  return new Intl.NumberFormat('en-ZA', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

function ageingDescription(data: DashboardSummary): string {
  return `Bar chart of outstanding amounts: ${data.ageing
    .map((b) => `${b.label} ${formatMoney(b.amount, data.currency)}`)
    .join(', ')}`;
}
