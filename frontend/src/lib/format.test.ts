import { addDaysIso, formatDate, formatMoney, humaniseStatus, roundMoney } from './format';

// Intl uses non-breaking spaces as group separators; normalise them for readable assertions.
const plain = (s: string) => s.replace(/\s/g, ' ');

describe('format', () => {
  it('formats rand amounts with South African conventions', () => {
    expect(plain(formatMoney(1234.5))).toBe('R 1 234,50');
    expect(plain(formatMoney(0))).toBe('R 0,00');
  });

  it('formats ISO dates without time-zone drift', () => {
    expect(formatDate('2026-03-01')).toMatch(/1 Mar 2026/);
  });

  it('adds days across month boundaries', () => {
    expect(addDaysIso('2026-01-30', 3)).toBe('2026-02-02');
  });

  it('humanises enum values', () => {
    expect(humaniseStatus('PARTIALLY_PAID')).toBe('Partially Paid');
  });

  it('rounds half up to cents like the server', () => {
    expect(roundMoney(1.005)).toBe(1.01);
    expect(roundMoney(44.9955)).toBe(45);
  });
});
