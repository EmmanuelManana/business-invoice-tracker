import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Ban, Download, Pencil, Send, Wallet } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { invoiceApi } from '@/api/endpoints';
import type { InvoiceDetail, PaymentInput } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Field } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { LoadingState } from '@/components/ui/Spinner';
import { ErrorState } from '@/components/ui/States';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { errorMessage } from '@/lib/errors';
import { formatDate, formatDateTime, formatMoney, humaniseStatus } from '@/lib/format';
import { queryKeys } from '@/lib/queryClient';
import { useBusinessToday } from '@/lib/useBusinessToday';
import { PaymentForm } from './PaymentForm';

export function InvoiceDetailPage() {
  const { id = '' } = useParams();
  const queryClient = useQueryClient();
  const invoice = useQuery({ queryKey: queryKeys.invoice(id), queryFn: () => invoiceApi.get(id) });
  const activity = useQuery({
    queryKey: queryKeys.invoiceActivity(id),
    queryFn: () => invoiceApi.activity(id),
  });
  const { today: businessToday } = useBusinessToday();

  const [paymentKey, setPaymentKey] = useState<string | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [downloading, setDownloading] = useState(false);

  function onChanged(updated: InvoiceDetail) {
    queryClient.setQueryData(queryKeys.invoice(id), updated);
    queryClient.invalidateQueries({ queryKey: queryKeys.invoiceActivity(id) });
    queryClient.invalidateQueries({ queryKey: queryKeys.invoices, refetchType: 'none' });
    queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
  }

  const send = useMutation({
    mutationFn: () => invoiceApi.send(id),
    onSuccess: (updated) => {
      onChanged(updated);
      toast.success(`${updated.number} marked as sent`);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const cancel = useMutation({
    mutationFn: () => invoiceApi.cancel(id, cancelReason.trim() || undefined),
    onSuccess: (updated) => {
      onChanged(updated);
      setCancelOpen(false);
      toast.success(`${updated.number} cancelled`);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const pay = useMutation({
    mutationFn: (input: PaymentInput) => invoiceApi.recordPayment(id, paymentKey!, input),
    onSuccess: (outcome) => {
      onChanged(outcome.invoice);
      setPaymentKey(null);
      toast.success(outcome.replayed ? 'Payment was already recorded' : 'Payment recorded');
    },
  });

  async function download(detail: InvoiceDetail) {
    setDownloading(true);
    try {
      await invoiceApi.downloadPdf(detail.id, `${detail.number}.pdf`);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setDownloading(false);
    }
  }

  if (invoice.isLoading) return <LoadingState />;
  if (invoice.error || !invoice.data)
    return <ErrorState error={invoice.error} onRetry={() => invoice.refetch()} />;

  const inv = invoice.data;
  const isOpen = ['SENT', 'PARTIALLY_PAID', 'OVERDUE'].includes(inv.status);
  const canCancel = inv.status !== 'PAID' && inv.status !== 'CANCELLED';

  return (
    <>
      <Link
        to="/invoices"
        className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> All invoices
      </Link>

      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">{inv.number}</h1>
            <StatusBadge status={inv.status} />
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {inv.customer.name} · issued {formatDate(inv.issueDate)} · due {formatDate(inv.dueDate)}
            {inv.daysOverdue > 0 && (
              <span className="font-medium text-red-600"> · {inv.daysOverdue} days overdue</span>
            )}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {inv.status === 'DRAFT' && (
            <>
              <Link
                to={`/invoices/${inv.id}/edit`}
                className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50"
              >
                <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
              </Link>
              <Button loading={send.isPending} onClick={() => send.mutate()}>
                <Send className="h-4 w-4" aria-hidden="true" /> Mark as sent
              </Button>
            </>
          )}
          {isOpen && (
            <Button onClick={() => setPaymentKey(crypto.randomUUID())}>
              <Wallet className="h-4 w-4" aria-hidden="true" /> Record payment
            </Button>
          )}
          <Button variant="secondary" loading={downloading} onClick={() => download(inv)}>
            <Download className="h-4 w-4" aria-hidden="true" /> PDF
          </Button>
          {canCancel && (
            <Button variant="secondary" onClick={() => setCancelOpen(true)}>
              <Ban className="h-4 w-4" aria-hidden="true" /> Cancel invoice
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section className="card" aria-labelledby="lines-title">
            <h2 id="lines-title" className="border-b border-slate-200 px-5 py-3 text-sm font-semibold">
              Line items
            </h2>
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50">
                  <tr>
                    <th scope="col" className="table-head">
                      Description
                    </th>
                    <th scope="col" className="table-head text-right">
                      Qty
                    </th>
                    <th scope="col" className="table-head text-right">
                      Unit price
                    </th>
                    <th scope="col" className="table-head hidden text-right sm:table-cell">
                      VAT
                    </th>
                    <th scope="col" className="table-head text-right">
                      Amount
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {inv.lineItems.map((line) => (
                    <tr key={line.id}>
                      <td className="table-cell">{line.description}</td>
                      <td className="table-cell text-right">{Number(line.quantity)}</td>
                      <td className="table-cell text-right">{formatMoney(line.unitPrice, inv.currency)}</td>
                      <td className="table-cell hidden text-right sm:table-cell">
                        {Math.round(line.vatRate * 100)}%
                      </td>
                      <td className="table-cell text-right">
                        {formatMoney(line.lineSubtotal, inv.currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <dl className="ml-auto max-w-xs space-y-1 border-t border-slate-200 px-5 py-4 text-sm">
              <Row label="Subtotal" value={formatMoney(inv.subtotal, inv.currency)} />
              <Row label="VAT" value={formatMoney(inv.vatTotal, inv.currency)} />
              <Row label="Total" value={formatMoney(inv.total, inv.currency)} strong />
              <Row label="Paid" value={formatMoney(inv.amountPaid, inv.currency)} />
              <Row
                label="Balance due"
                value={formatMoney(inv.balanceDue, inv.currency)}
                strong
                testId="balance-due"
              />
            </dl>
          </section>

          <section className="card" aria-labelledby="payments-title">
            <h2 id="payments-title" className="border-b border-slate-200 px-5 py-3 text-sm font-semibold">
              Payments
            </h2>
            {inv.payments.length === 0 ? (
              <p className="px-5 py-6 text-sm text-slate-500">No payments recorded yet.</p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {inv.payments.map((p) => (
                  <li key={p.id} className="flex items-center justify-between px-5 py-3 text-sm">
                    <div>
                      <p className="font-medium">{formatMoney(p.amount, inv.currency)}</p>
                      <p className="text-xs text-slate-500">
                        {formatDate(p.paidOn)} · {p.method}
                        {p.reference && ` · ${p.reference}`}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {inv.notes && (
            <section className="card p-5">
              <h2 className="text-sm font-semibold">Notes</h2>
              <p className="mt-2 whitespace-pre-line text-sm text-slate-600">{inv.notes}</p>
            </section>
          )}
        </div>

        <aside className="space-y-6">
          <section className="card p-5 text-sm">
            <h2 className="text-sm font-semibold">Customer</h2>
            <p className="mt-2 font-medium">{inv.customer.name}</p>
            <p className="text-slate-500">{inv.customer.email}</p>
            {inv.cancellationReason && (
              <p className="mt-4 rounded bg-slate-50 p-2 text-slate-600">
                Cancelled: {inv.cancellationReason}
              </p>
            )}
          </section>

          <section className="card" aria-labelledby="activity-title">
            <h2 id="activity-title" className="border-b border-slate-200 px-5 py-3 text-sm font-semibold">
              Activity
            </h2>
            {activity.isLoading && <LoadingState />}
            <ol className="space-y-4 px-5 py-4">
              {activity.data?.map((a) => (
                <li key={a.id} className="relative border-l-2 border-slate-200 pl-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                    {humaniseStatus(a.type)}
                  </p>
                  <p className="text-sm text-slate-700">{a.message}</p>
                  <time className="text-xs text-slate-400" dateTime={a.occurredAt}>
                    {formatDateTime(a.occurredAt)}
                  </time>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>

      <Modal open={paymentKey !== null} title="Record payment" onClose={() => setPaymentKey(null)}>
        <PaymentForm
          balanceDue={inv.balanceDue}
          currency={inv.currency}
          today={businessToday}
          onSubmit={(input) => pay.mutateAsync(input)}
          onCancel={() => setPaymentKey(null)}
        />
      </Modal>

      <Modal
        open={cancelOpen}
        title={`Cancel ${inv.number}?`}
        onClose={() => setCancelOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCancelOpen(false)}>
              Keep invoice
            </Button>
            <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>
              Cancel invoice
            </Button>
          </>
        }
      >
        <p className="mb-4 text-sm text-slate-600">
          Cancelled invoices stop receiving reminders. This cannot be undone.
        </p>
        <Field label="Reason (optional)">
          <input
            className="input"
            value={cancelReason}
            maxLength={500}
            onChange={(e) => setCancelReason(e.target.value)}
          />
        </Field>
      </Modal>
    </>
  );
}

function Row({
  label,
  value,
  strong,
  testId,
}: {
  label: string;
  value: string;
  strong?: boolean;
  testId?: string;
}) {
  return (
    <div className={`flex justify-between ${strong ? 'font-semibold' : ''}`}>
      <dt className={strong ? '' : 'text-slate-500'}>{label}</dt>
      <dd data-testid={testId}>{value}</dd>
    </div>
  );
}
