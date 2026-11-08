import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { notificationApi } from '@/api/endpoints';
import type { Notification } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingState } from '@/components/ui/Spinner';
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/States';
import { Badge } from '@/components/ui/StatusBadge';
import { DevToolsCard } from '@/features/dev/DevToolsCard';
import { formatDateTime } from '@/lib/format';
import { queryKeys } from '@/lib/queryClient';
import { ChannelBadge } from './ChannelBadge';

/** Log of every simulated notification (customer reminders and business alerts). */
export function NotificationsPage() {
  const queryClient = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);

  const query = { unreadOnly, page, size: 20 };
  const notifications = useQuery({
    queryKey: queryKeys.notificationList(query),
    queryFn: () => notificationApi.list(query),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications });
  const markRead = useMutation({ mutationFn: notificationApi.markRead, onSuccess: invalidate });
  const markAllRead = useMutation({ mutationFn: notificationApi.markAllRead, onSuccess: invalidate });

  function toggle(n: Notification) {
    setExpanded((current) => (current === n.id ? null : n.id));
    if (!n.readAt) markRead.mutate(n.id);
  }

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Simulated reminders sent to customers and overdue alerts for you. Nothing is actually emailed or texted."
        actions={
          <Button variant="secondary" loading={markAllRead.isPending} onClick={() => markAllRead.mutate()}>
            <CheckCheck className="h-4 w-4" aria-hidden="true" /> Mark all read
          </Button>
        }
      />
      <div className="mb-6">
        <DevToolsCard />
      </div>

      <div className="card">
        <div className="flex items-center gap-2 border-b border-slate-200 px-4 py-3">
          <input
            id="unread-only"
            type="checkbox"
            className="h-4 w-4 rounded border-slate-300 text-brand-600"
            checked={unreadOnly}
            onChange={(e) => {
              setUnreadOnly(e.target.checked);
              setPage(0);
            }}
          />
          <label htmlFor="unread-only" className="text-sm text-slate-700">
            Unread only
          </label>
        </div>

        {notifications.isLoading && <LoadingState />}
        {notifications.error && (
          <ErrorState error={notifications.error} onRetry={() => notifications.refetch()} />
        )}
        {notifications.data && notifications.data.items.length === 0 && (
          <EmptyState
            title={unreadOnly ? 'All caught up' : 'No notifications yet'}
            description="Reminders appear here when invoices approach or pass their due date."
          />
        )}
        {notifications.data && notifications.data.items.length > 0 && (
          <>
            <ul className="divide-y divide-slate-100" aria-label="Notifications">
              {notifications.data.items.map((n) => (
                <li key={n.id} className={n.readAt ? '' : 'bg-brand-50/40'}>
                  <button
                    type="button"
                    onClick={() => toggle(n)}
                    aria-expanded={expanded === n.id}
                    className="flex w-full flex-col gap-1 px-4 py-3 text-left hover:bg-slate-50 sm:flex-row sm:items-center sm:gap-4"
                  >
                    <div className="flex shrink-0 items-center gap-2 sm:w-40">
                      <ChannelBadge channel={n.channel} />
                      <Badge
                        tone={
                          n.status === 'SENT'
                            ? 'green'
                            : n.status === 'FAILED'
                              ? 'red'
                              : n.status === 'SKIPPED'
                                ? 'amber'
                                : 'slate'
                        }
                      >
                        {n.status}
                      </Badge>
                    </div>
                    <div className="min-w-0 flex-1">
                      <p
                        className={`truncate text-sm ${n.readAt ? 'text-slate-700' : 'font-semibold text-slate-900'}`}
                      >
                        {n.subject}
                      </p>
                      <p className="truncate text-xs text-slate-500">
                        To {n.recipient} · stage {n.stage}
                      </p>
                    </div>
                    <time className="shrink-0 text-xs text-slate-400" dateTime={n.createdAt}>
                      {formatDateTime(n.createdAt)}
                    </time>
                  </button>
                  {expanded === n.id && (
                    <div className="space-y-2 px-4 pb-4 sm:pl-48">
                      <pre className="whitespace-pre-wrap rounded-md bg-slate-50 p-3 font-sans text-sm text-slate-700">
                        {n.body}
                      </pre>
                      <div className="flex flex-wrap gap-4 text-xs text-slate-500">
                        <span>Attempts: {n.attempts}</span>
                        {n.sentAt && <span>Sent {formatDateTime(n.sentAt)}</span>}
                        {n.lastError &&
                          (n.status === 'SKIPPED' ? (
                            <span>Not sent: {n.lastError}</span>
                          ) : (
                            <span className="text-red-600">Error: {n.lastError}</span>
                          ))}
                        {n.invoiceId && (
                          <Link
                            to={`/invoices/${n.invoiceId}`}
                            className="font-medium text-brand-600 hover:underline"
                          >
                            View {n.invoiceNumber}
                          </Link>
                        )}
                      </div>
                    </div>
                  )}
                </li>
              ))}
            </ul>
            <Pagination
              page={notifications.data.page}
              totalPages={notifications.data.totalPages}
              totalItems={notifications.data.totalItems}
              onPageChange={setPage}
            />
          </>
        )}
      </div>
    </>
  );
}
