// Types mirroring the backend's JSON contract (see /swagger-ui.html).

export type InvoiceStatus = 'DRAFT' | 'SENT' | 'PARTIALLY_PAID' | 'OVERDUE' | 'PAID' | 'CANCELLED';
export type PaymentMethod = 'EFT' | 'CARD' | 'CASH' | 'OTHER';
export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED';
export type NotificationKind = 'PAYMENT_REMINDER' | 'OVERDUE_ALERT';

export const INVOICE_STATUSES: InvoiceStatus[] = [
  'DRAFT',
  'SENT',
  'PARTIALLY_PAID',
  'OVERDUE',
  'PAID',
  'CANCELLED',
];
export const PAYMENT_METHODS: PaymentMethod[] = ['EFT', 'CARD', 'CASH', 'OTHER'];

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  businessId: string;
  businessName: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: UserProfile;
}

export interface Address {
  line1?: string | null;
  line2?: string | null;
  city?: string | null;
  province?: string | null;
  postalCode?: string | null;
  country?: string | null;
}

export interface BusinessProfile {
  id: string;
  name: string;
  registrationNumber?: string | null;
  vatNumber?: string | null;
  email?: string | null;
  phone?: string | null;
  address: Address;
  currency: string;
  paymentTermsDays: number;
}

export interface Customer {
  id: string;
  name: string;
  email: string;
  phone?: string | null;
  billingAddress: Address;
  createdAt: string;
}

export interface CustomerInput {
  name: string;
  email: string;
  phone?: string;
  billingAddress?: Address;
}

export interface InvoiceSummary {
  id: string;
  number: string;
  customerId: string;
  customerName: string;
  issueDate: string;
  dueDate: string;
  status: InvoiceStatus;
  currency: string;
  total: number;
  amountPaid: number;
  balanceDue: number;
  daysOverdue: number;
}

export interface LineItem {
  id: string;
  position: number;
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
  lineSubtotal: number;
  lineVat: number;
  lineTotal: number;
}

export interface Payment {
  id: string;
  amount: number;
  paidOn: string;
  method: PaymentMethod;
  reference?: string | null;
  createdAt: string;
}

export interface InvoiceDetail {
  id: string;
  number: string;
  status: InvoiceStatus;
  customer: { id: string; name: string; email: string };
  issueDate: string;
  dueDate: string;
  currency: string;
  notes?: string | null;
  lineItems: LineItem[];
  payments: Payment[];
  subtotal: number;
  vatTotal: number;
  total: number;
  amountPaid: number;
  balanceDue: number;
  daysOverdue: number;
  sentAt?: string | null;
  paidAt?: string | null;
  overdueAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface InvoiceInput {
  customerId: string;
  issueDate?: string;
  dueDate?: string;
  notes?: string;
  lineItems: { description: string; quantity: number; unitPrice: number; vatRate: number }[];
}

export interface PaymentInput {
  amount: number;
  paidOn: string;
  method: PaymentMethod;
  reference?: string;
}

export interface PaymentOutcome {
  invoice: InvoiceDetail;
  payment: Payment;
  replayed: boolean;
}

export interface Activity {
  id: string;
  type: string;
  message: string;
  occurredAt: string;
}

export interface InvoiceQuery {
  status?: InvoiceStatus[];
  customerId?: string;
  from?: string;
  to?: string;
  search?: string;
  sort?: string;
  direction?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

export interface Notification {
  id: string;
  kind: NotificationKind;
  stage: string;
  channel: NotificationChannel;
  recipient: string;
  subject: string;
  body: string;
  status: NotificationStatus;
  attempts: number;
  lastError?: string | null;
  invoiceId?: string | null;
  invoiceNumber?: string | null;
  createdAt: string;
  sentAt?: string | null;
  readAt?: string | null;
}

export interface AgeingBucket {
  key: string;
  label: string;
  amount: number;
  count: number;
}

export interface DashboardSummary {
  currency: string;
  asOf: string;
  totalOutstanding: number;
  totalOverdue: number;
  paidThisMonth: number;
  countsByStatus: Record<InvoiceStatus, number>;
  ageing: AgeingBucket[];
  topOverdue: {
    id: string;
    number: string;
    customerName: string;
    dueDate: string;
    daysOverdue: number;
    balanceDue: number;
  }[];
}

/** The caller's business calendar (the demo time machine is per business). */
export interface ClockStatus {
  now: string;
  today: string;
  offsetDays: number;
  /** Only set by a reset: invoices that were overdue only in simulated time. */
  invoicesNoLongerOverdue: number;
}

/** The business's current date, for date defaults and validation. */
export interface BusinessDate {
  today: string;
  simulatedDays: number;
}

export interface OverdueRunResult {
  ranAt: string;
  serverDate: string;
  invoicesMarkedOverdue: number;
  remindersQueued: number;
  alertsQueued: number;
  notificationsSent: number;
  notificationsFailed: number;
  notificationsSkipped: number;
  errors: number;
  skipped: boolean;
}

/** RFC 7807 problem body returned by the API for every error. */
export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  correlationId?: string;
  errors?: { field: string; message: string }[];
  [key: string]: unknown;
}
