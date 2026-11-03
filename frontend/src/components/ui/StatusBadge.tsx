import clsx from 'clsx';
import type { InvoiceStatus } from '@/api/types';
import { humaniseStatus } from '@/lib/format';

const styles: Record<InvoiceStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-700 ring-slate-300',
  SENT: 'bg-blue-50 text-blue-700 ring-blue-200',
  PARTIALLY_PAID: 'bg-amber-50 text-amber-800 ring-amber-200',
  OVERDUE: 'bg-red-50 text-red-700 ring-red-200',
  PAID: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  CANCELLED: 'bg-slate-50 text-slate-500 ring-slate-200 line-through',
};

export function StatusBadge({ status }: { status: InvoiceStatus }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
        styles[status],
      )}
    >
      {humaniseStatus(status)}
    </span>
  );
}

export function Badge({
  children,
  tone = 'slate',
}: {
  children: string;
  tone?: 'slate' | 'blue' | 'green' | 'red' | 'amber';
}) {
  const tones = {
    slate: 'bg-slate-100 text-slate-700',
    blue: 'bg-blue-50 text-blue-700',
    green: 'bg-emerald-50 text-emerald-700',
    red: 'bg-red-50 text-red-700',
    amber: 'bg-amber-50 text-amber-800',
  };
  return (
    <span className={clsx('inline-flex rounded px-1.5 py-0.5 text-xs font-medium', tones[tone])}>
      {children}
    </span>
  );
}
