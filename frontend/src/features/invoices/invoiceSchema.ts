import { z } from 'zod';
import type { InvoiceDetail, InvoiceInput } from '@/api/types';
import { roundMoney } from '@/lib/format';

export const DEFAULT_VAT_RATE = 0.15;

const number = (message: string) =>
  z.number({ invalid_type_error: message, required_error: message }).refine(Number.isFinite, message);

export const lineItemSchema = z.object({
  description: z.string().trim().min(1, 'Description is required').max(500),
  quantity: number('Enter a quantity').refine((v) => v > 0, 'Must be greater than 0'),
  unitPrice: number('Enter a price').refine((v) => v >= 0, 'Cannot be negative'),
  vatApplicable: z.boolean(),
});

export const invoiceSchema = z
  .object({
    customerId: z.string().min(1, 'Choose a customer'),
    issueDate: z.string().min(1, 'Issue date is required'),
    dueDate: z.string().min(1, 'Due date is required'),
    notes: z.string().max(2000).optional(),
    lineItems: z.array(lineItemSchema).min(1, 'Add at least one line item').max(100),
  })
  .refine((v) => v.dueDate >= v.issueDate, {
    path: ['dueDate'],
    message: 'Due date cannot be before the issue date',
  });

export type InvoiceFormValues = z.infer<typeof invoiceSchema>;
export type LineItemValues = z.infer<typeof lineItemSchema>;

export const emptyLine = (): LineItemValues => ({
  description: '',
  quantity: 1,
  unitPrice: 0,
  vatApplicable: true,
});

/**
 * Client-side preview of totals using the same per-line rounding as the server. Display only:
 * the server recomputes everything and its figures are authoritative.
 */
export function previewTotals(lines: Partial<LineItemValues>[]) {
  let subtotal = 0;
  let vat = 0;
  for (const line of lines) {
    const quantity = Number(line.quantity) || 0;
    const price = Number(line.unitPrice) || 0;
    const net = roundMoney(quantity * price);
    subtotal += net;
    vat += line.vatApplicable ? roundMoney(net * DEFAULT_VAT_RATE) : 0;
  }
  subtotal = roundMoney(subtotal);
  vat = roundMoney(vat);
  return { subtotal, vat, total: roundMoney(subtotal + vat) };
}

export function toInvoiceInput(values: InvoiceFormValues): InvoiceInput {
  return {
    customerId: values.customerId,
    issueDate: values.issueDate,
    dueDate: values.dueDate,
    notes: values.notes?.trim() || undefined,
    lineItems: values.lineItems.map((l) => ({
      description: l.description.trim(),
      quantity: l.quantity,
      unitPrice: l.unitPrice,
      vatRate: l.vatApplicable ? DEFAULT_VAT_RATE : 0,
    })),
  };
}

export function fromInvoice(invoice: InvoiceDetail): InvoiceFormValues {
  return {
    customerId: invoice.customer.id,
    issueDate: invoice.issueDate,
    dueDate: invoice.dueDate,
    notes: invoice.notes ?? '',
    lineItems: invoice.lineItems.map((l) => ({
      description: l.description,
      quantity: Number(l.quantity),
      unitPrice: Number(l.unitPrice),
      vatApplicable: Number(l.vatRate) > 0,
    })),
  };
}
