import { request, setSession, toQuery } from './client';
import type {
  Activity,
  AuthResponse,
  BusinessProfile,
  ClockStatus,
  Customer,
  CustomerInput,
  DashboardSummary,
  InvoiceDetail,
  InvoiceInput,
  InvoiceQuery,
  InvoiceSummary,
  Notification,
  OverdueRunResult,
  Page,
  PaymentInput,
  PaymentOutcome,
  UserProfile,
} from './types';

export const authApi = {
  async login(email: string, password: string) {
    const session = await request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password },
    });
    setSession(session);
    return session;
  },
  async register(input: { businessName: string; fullName: string; email: string; password: string }) {
    const session = await request<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: input });
    setSession(session);
    return session;
  },
  async logout() {
    try {
      await request<void>('/api/v1/auth/logout', { method: 'POST' });
    } finally {
      setSession(null);
    }
  },
  me: () => request<UserProfile>('/api/v1/auth/me'),
};

export const businessApi = {
  get: () => request<BusinessProfile>('/api/v1/business/profile'),
  update: (input: Omit<BusinessProfile, 'id'>) =>
    request<BusinessProfile>('/api/v1/business/profile', { method: 'PUT', body: input }),
};

export const customerApi = {
  list: (params: { search?: string; page?: number; size?: number }) =>
    request<Page<Customer>>(`/api/v1/customers${toQuery(params)}`),
  get: (id: string) => request<Customer>(`/api/v1/customers/${id}`),
  create: (input: CustomerInput) => request<Customer>('/api/v1/customers', { method: 'POST', body: input }),
  update: (id: string, input: CustomerInput) =>
    request<Customer>(`/api/v1/customers/${id}`, { method: 'PUT', body: input }),
  remove: (id: string) => request<void>(`/api/v1/customers/${id}`, { method: 'DELETE' }),
};

export const invoiceApi = {
  list: (query: InvoiceQuery) => request<Page<InvoiceSummary>>(`/api/v1/invoices${toQuery(query)}`),
  get: (id: string) => request<InvoiceDetail>(`/api/v1/invoices/${id}`),
  create: (input: InvoiceInput) =>
    request<InvoiceDetail>('/api/v1/invoices', { method: 'POST', body: input }),
  /** Sends If-Match so a concurrent edit is rejected (412) instead of silently overwritten. */
  update: (id: string, version: number, input: InvoiceInput) =>
    request<InvoiceDetail>(`/api/v1/invoices/${id}`, {
      method: 'PUT',
      body: input,
      headers: { 'If-Match': `"${version}"` },
    }),
  send: (id: string) => request<InvoiceDetail>(`/api/v1/invoices/${id}/send`, { method: 'POST' }),
  cancel: (id: string, reason?: string) =>
    request<InvoiceDetail>(`/api/v1/invoices/${id}/cancel`, { method: 'POST', body: { reason } }),
  /** The idempotency key makes retries (double clicks, timeouts) safe. */
  recordPayment: (id: string, idempotencyKey: string, input: PaymentInput) =>
    request<PaymentOutcome>(`/api/v1/invoices/${id}/payments`, {
      method: 'POST',
      body: input,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  activity: (id: string) => request<Activity[]>(`/api/v1/invoices/${id}/activity`),
  async downloadPdf(id: string, fileName: string) {
    const response = await request<Response>(`/api/v1/invoices/${id}/pdf`, {
      raw: true,
      headers: { Accept: 'application/pdf' },
    });
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  },
};

export const notificationApi = {
  list: (params: { unreadOnly?: boolean; page?: number; size?: number }) =>
    request<Page<Notification>>(`/api/v1/notifications${toQuery(params)}`),
  unreadCount: () => request<{ count: number }>('/api/v1/notifications/unread-count'),
  markRead: (id: string) => request<Notification>(`/api/v1/notifications/${id}/read`, { method: 'POST' }),
  markAllRead: () => request<{ updated: number }>('/api/v1/notifications/read-all', { method: 'POST' }),
};

export const dashboardApi = {
  summary: () => request<DashboardSummary>('/api/v1/dashboard/summary'),
};

export const devApi = {
  clock: () => request<ClockStatus>('/api/v1/dev/clock'),
  advance: (days: number) =>
    request<ClockStatus>('/api/v1/dev/clock/advance', { method: 'POST', body: { days } }),
  reset: () => request<ClockStatus>('/api/v1/dev/clock/reset', { method: 'POST' }),
  runOverdueCheck: () => request<OverdueRunResult>('/api/v1/dev/overdue-check', { method: 'POST' }),
};
