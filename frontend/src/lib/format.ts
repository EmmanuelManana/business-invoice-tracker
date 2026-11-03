const moneyFormatters = new Map<string, Intl.NumberFormat>();

/** Formats an amount in the given ISO currency using South African conventions (R 1 234,56). */
export function formatMoney(amount: number, currency = 'ZAR'): string {
  let formatter = moneyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('en-ZA', { style: 'currency', currency });
    moneyFormatters.set(currency, formatter);
  }
  return formatter.format(amount);
}

const dateFormatter = new Intl.DateTimeFormat('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFormatter = new Intl.DateTimeFormat('en-ZA', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** Formats an ISO date (yyyy-mm-dd) without shifting it through the local time zone. */
export function formatDate(isoDate: string): string {
  const [y, m, d] = isoDate.slice(0, 10).split('-').map(Number);
  return dateFormatter.format(new Date(y!, (m ?? 1) - 1, d ?? 1));
}

export function formatDateTime(isoInstant: string): string {
  return dateTimeFormatter.format(new Date(isoInstant));
}

/** Today's date as yyyy-mm-dd in the browser's local time zone. */
export function todayIso(): string {
  return toIsoDate(new Date());
}

export function addDaysIso(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return toIsoDate(new Date(y!, (m ?? 1) - 1, (d ?? 1) + days));
}

function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function humaniseStatus(status: string): string {
  return status
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

/** Rounds to cents the same way the server does (half-up). */
export function roundMoney(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}
