import { ApiError } from '@/api/client';
import { applyServerErrors, toFormPath } from './errors';
import { hasAtMostDecimals, hasAtMostIntegerDigits } from './validation';

describe('number rules (mirror the API)', () => {
  it('counts decimal places', () => {
    expect(hasAtMostDecimals(10, 2)).toBe(true);
    expect(hasAtMostDecimals(10.5, 2)).toBe(true);
    expect(hasAtMostDecimals(10.99, 2)).toBe(true);
    expect(hasAtMostDecimals(10.999, 2)).toBe(false);
    expect(hasAtMostDecimals(1.125, 3)).toBe(true);
    expect(hasAtMostDecimals(1e-7, 3)).toBe(false);
    expect(hasAtMostDecimals(Number.NaN, 2)).toBe(false);
  });

  it('counts whole-number digits', () => {
    expect(hasAtMostIntegerDigits(999_999_999, 9)).toBe(true);
    expect(hasAtMostIntegerDigits(1_000_000_000, 9)).toBe(false);
    expect(hasAtMostIntegerDigits(-12.5, 2)).toBe(true);
  });
});

describe('server field errors', () => {
  it('maps Java-style paths onto form paths', () => {
    expect(toFormPath('lineItems[0].quantity')).toBe('lineItems.0.quantity');
    expect(toFormPath('lineItems[12].unitPrice')).toBe('lineItems.12.unitPrice');
    expect(toFormPath('amount')).toBe('amount');
  });

  it('applies each field error to the form', () => {
    const setError = vi.fn();
    const error = new ApiError({
      status: 400,
      code: 'validation_failed',
      errors: [
        { field: 'lineItems[1].unitPrice', message: 'numeric value out of bounds' },
        { field: 'notes', message: 'size must be between 0 and 2000' },
      ],
    });

    expect(applyServerErrors(error, setError)).toBe(true);
    expect(setError).toHaveBeenCalledWith('lineItems.1.unitPrice', {
      type: 'server',
      message: 'numeric value out of bounds',
    });
    expect(setError).toHaveBeenCalledWith('notes', expect.objectContaining({ type: 'server' }));
  });

  it('does nothing for errors without field details', () => {
    expect(applyServerErrors(new Error('boom'), vi.fn())).toBe(false);
  });
});
