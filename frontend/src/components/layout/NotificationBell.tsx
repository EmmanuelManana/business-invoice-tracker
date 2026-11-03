import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { Link } from 'react-router-dom';
import { notificationApi } from '@/api/endpoints';
import { formatDateTime } from '@/lib/format';
import { queryKeys } from '@/lib/queryClient';
import { ChannelBadge } from '@/features/notifications/ChannelBadge';

export const UNREAD_POLL_MS = 30_000;

/** Bell with unread count; polls so new overdue alerts appear without a reload. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const unread = useQuery({
    queryKey: queryKeys.unreadCount,
    queryFn: notificationApi.unreadCount,
    refetchInterval: UNREAD_POLL_MS,
  });
  const recent = useQuery({
    queryKey: queryKeys.notificationList({ recent: true }),
    queryFn: () => notificationApi.list({ size: 6 }),
    enabled: open,
  });
  const markAllRead = useMutation({
    mutationFn: notificationApi.markAllRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  });

  useEffect(() => {
    if (!open) return;
    function onClick(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const count = unread.data?.count ?? 0;
  const label = count > 0 ? `Notifications, ${count} unread` : 'Notifications';

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="relative rounded-full p-2 text-slate-600 hover:bg-slate-100"
        aria-label={label}
        aria-expanded={open}
        aria-haspopup="true"
      >
        <Bell className="h-5 w-5" aria-hidden="true" />
        {count > 0 && (
          <span
            data-testid="unread-count"
            className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-600 px-1 text-[11px] font-semibold text-white"
          >
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-[22rem] max-w-[calc(100vw-2rem)] rounded-lg border border-slate-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <h2 className="text-sm font-semibold">Notifications</h2>
            <button
              type="button"
              className="text-xs font-medium text-brand-600 hover:underline disabled:text-slate-400"
              onClick={() => markAllRead.mutate()}
              disabled={count === 0 || markAllRead.isPending}
            >
              Mark all read
            </button>
          </div>
          <ul className="max-h-80 divide-y divide-slate-100 overflow-y-auto">
            {recent.isLoading && <li className="px-4 py-6 text-center text-sm text-slate-500">Loading…</li>}
            {recent.data?.items.length === 0 && (
              <li className="px-4 py-6 text-center text-sm text-slate-500">No notifications yet</li>
            )}
            {recent.data?.items.map((n) => (
              <li key={n.id} className={n.readAt ? 'px-4 py-3' : 'bg-brand-50/50 px-4 py-3'}>
                <div className="flex items-center gap-2">
                  <ChannelBadge channel={n.channel} />
                  <span className="text-xs text-slate-500">{formatDateTime(n.createdAt)}</span>
                </div>
                <p className="mt-1 text-sm text-slate-800">{n.subject}</p>
              </li>
            ))}
          </ul>
          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className="block border-t border-slate-100 px-4 py-2.5 text-center text-sm font-medium text-brand-600 hover:bg-slate-50"
          >
            View all notifications
          </Link>
        </div>
      )}
    </div>
  );
}
