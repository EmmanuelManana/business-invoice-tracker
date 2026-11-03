import { invoiceSchema, previewTotals, toInvoiceInput } from './invoiceSchema';

const validInvoice = {
  customerId: 'c-1',
  issueDate: '2026-03-01',
  dueDate: '2026-03-31',
  notes: '',
  lineItems: [{ description: 'Consulting', quantity: 2, unitPrice: 1500, vatApplicable: true }],
};

describe('invoiceSchema', () => {
  it('accepts a valid invoice', () => {
    expect(invoiceSchema.safeParse(validInvoice).success).toBe(true);
  });

  it('rejects a due date before the issue date', () => {
    const result = invoiceSchema.safeParse({ ...validInvoice, dueDate: '2026-02-01' });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['dueDate']);
  });

  it('requires at least one valid line item', () => {
    expect(invoiceSchema.safeParse({ ...validInvoice, lineItems: [] }).success).toBe(false);
    const badLine = { description: '', quantity: 0, unitPrice: -1, vatApplicable: false };
    const result = invoiceSchema.safeParse({ ...validInvoice, lineItems: [badLine] });
    expect(result.error?.issues.map((i) => i.path.join('.'))).toEqual(
      expect.arrayContaining(['lineItems.0.description', 'lineItems.0.quantity', 'lineItems.0.unitPrice']),
    );
  });
});

describe('previewTotals', () => {
  it('matches the server rounding (per line, half up)', () => {
    const totals = previewTotals([
      { quantity: 2, unitPrice: 1500, vatApplicable: true },
      { quantity: 3, unitPrice: 99.99, vatApplicable: true },
      { quantity: 1, unitPrice: 250, vatApplicable: false },
    ]);
    expect(totals).toEqual({ subtotal: 3549.97, vat: 495, total: 4044.97 });
  });

  it('treats incomplete lines as zero', () => {
    expect(previewTotals([{ quantity: Number.NaN, unitPrice: 10, vatApplicable: true }])).toEqual({
      subtotal: 0,
      vat: 0,
      total: 0,
    });
  });
});

describe('toInvoiceInput', () => {
  it('maps the VAT checkbox to a rate and drops empty notes', () => {
    const input = toInvoiceInput({
      ...validInvoice,
      lineItems: [
        { description: ' A ', quantity: 1, unitPrice: 10, vatApplicable: true },
        { description: 'B', quantity: 1, unitPrice: 10, vatApplicable: false },
      ],
    });
    expect(input.notes).toBeUndefined();
    expect(input.lineItems).toEqual([
      { description: 'A', quantity: 1, unitPrice: 10, vatRate: 0.15 },
      { description: 'B', quantity: 1, unitPrice: 10, vatRate: 0 },
    ]);
  });
});
