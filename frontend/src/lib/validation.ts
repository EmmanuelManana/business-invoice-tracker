/**
 * Number rules that mirror the server's validation (@Digits on the API), so the browser rejects
 * the same values the server would, with a message next to the field.
 */

/** True if {@code value} has no more than {@code places} digits after the decimal point. */
export function hasAtMostDecimals(value: number, places: number): boolean {
  if (!Number.isFinite(value)) return false;
  const text = String(value);
  if (/e/i.test(text)) return false; // exponent notation means far too many digits either way
  const fraction = text.split('.')[1] ?? '';
  return fraction.length <= places;
}

/** True if the whole-number part has no more than {@code digits} digits. */
export function hasAtMostIntegerDigits(value: number, digits: number): boolean {
  return Number.isFinite(value) && String(Math.trunc(Math.abs(value))).length <= digits;
}

export const decimalsMessage = (places: number) =>
  places === 1 ? 'Use at most 1 decimal place' : `Use at most ${places} decimal places`;
