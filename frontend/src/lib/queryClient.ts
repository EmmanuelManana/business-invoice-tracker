import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api/client';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: true,
        // Retry transient failures only; 4xx responses will not succeed on retry.
        retry: (failureCount, error) =>
          failureCount < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
      mutations: { retry: false },
    },
  });
}

/** Query keys in one place so invalidation stays consistent. */
export const queryKeys = {
  dashboard: ['dashboard'] as const,
  invoices: ['invoices'] as const,
  invoiceList: (query: object) => ['invoices', 'list', query] as const,
  invoice: (id: string) => ['invoices', 'detail', id] as const,
  invoiceActivity: (id: string) => ['invoices', 'activity', id] as const,
  customers: ['customers'] as const,
  customerList: (query: object) => ['customers', 'list', query] as const,
  notifications: ['notifications'] as const,
  notificationList: (query: object) => ['notifications', 'list', query] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
  business: ['business'] as const,
  devClock: ['dev', 'clock'] as const,
};
