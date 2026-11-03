import type { ReactNode } from 'react';
import { AlertTriangle, Inbox } from 'lucide-react';
import { errorMessage } from '@/lib/errors';
import { Button } from './Button';

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-14 text-center">
      <Inbox className="h-10 w-10 text-slate-300" aria-hidden="true" />
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      {description && <p className="max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-2 px-6 py-14 text-center">
      <AlertTriangle className="h-10 w-10 text-red-400" aria-hidden="true" />
      <h3 className="text-sm font-semibold text-slate-900">Couldn&apos;t load this</h3>
      <p className="max-w-sm text-sm text-slate-500">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-3" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
