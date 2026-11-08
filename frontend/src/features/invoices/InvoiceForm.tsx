import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Trash2 } from 'lucide-react';
import { useController, useFieldArray, useForm, useWatch } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { Field, FormError } from '@/components/ui/Field';
import { CustomerSelect } from '@/features/customers/CustomerSelect';
import { applyServerErrors, errorMessage } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { emptyLine, invoiceSchema, previewTotals, type InvoiceFormValues } from './invoiceSchema';

interface InvoiceFormProps {
  /** Name of the invoice's current customer when editing (shown even if not in the first page of results). */
  customerName?: string;
  defaultValues: InvoiceFormValues;
  currency?: string;
  submitLabel: string;
  onSubmit: (values: InvoiceFormValues) => Promise<unknown>;
  onCancel: () => void;
}

export function InvoiceForm({
  customerName,
  defaultValues,
  currency = 'ZAR',
  submitLabel,
  onSubmit,
  onCancel,
}: InvoiceFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<InvoiceFormValues>({ resolver: zodResolver(invoiceSchema), defaultValues });
  const { field: customerField } = useController({ control, name: 'customerId' });
  const { fields, append, remove } = useFieldArray({ control, name: 'lineItems' });
  const lines = useWatch({ control, name: 'lineItems' });
  const totals = previewTotals(lines ?? []);

  async function submit(values: InvoiceFormValues) {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      applyServerErrors(error, setError);
      setFormError(errorMessage(error));
    }
  }

  return (
    <form onSubmit={handleSubmit(submit, () => setFormError(null))} noValidate className="space-y-6">
      <FormError>{formError}</FormError>

      <section className="card grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Customer" error={errors.customerId?.message} required className="sm:col-span-2">
          <CustomerSelect
            value={customerField.value}
            onChange={customerField.onChange}
            emptyLabel="Select a customer…"
            selectedName={customerName}
          />
        </Field>
        <Field label="Issue date" error={errors.issueDate?.message} required>
          <input type="date" className="input" {...register('issueDate')} />
        </Field>
        <Field label="Due date" error={errors.dueDate?.message} required>
          <input type="date" className="input" {...register('dueDate')} />
        </Field>
      </section>

      <section className="card" aria-labelledby="line-items-title">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 id="line-items-title" className="text-sm font-semibold">
            Line items
          </h2>
          <Button variant="secondary" size="sm" onClick={() => append(emptyLine())}>
            <Plus className="h-3.5 w-3.5" aria-hidden="true" /> Add line
          </Button>
        </div>
        {errors.lineItems?.root?.message && (
          <p className="px-5 pt-3 text-sm text-red-600">{errors.lineItems.root.message}</p>
        )}
        <ol className="divide-y divide-slate-100">
          {fields.map((field, index) => {
            const lineErrors = errors.lineItems?.[index];
            return (
              <li
                key={field.id}
                className="grid gap-3 px-5 py-4 sm:grid-cols-12 sm:items-start"
                data-testid="line-item"
              >
                <Field
                  label={`Line ${index + 1} description`}
                  error={lineErrors?.description?.message}
                  className="sm:col-span-5"
                >
                  <input
                    className="input"
                    placeholder="What did you deliver?"
                    {...register(`lineItems.${index}.description`)}
                  />
                </Field>
                <Field label="Quantity" error={lineErrors?.quantity?.message} className="sm:col-span-2">
                  <input
                    type="number"
                    step="0.001"
                    min="0"
                    inputMode="decimal"
                    className="input"
                    {...register(`lineItems.${index}.quantity`, { valueAsNumber: true })}
                  />
                </Field>
                <Field label="Unit price" error={lineErrors?.unitPrice?.message} className="sm:col-span-2">
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    inputMode="decimal"
                    className="input"
                    {...register(`lineItems.${index}.unitPrice`, { valueAsNumber: true })}
                  />
                </Field>
                <div className="flex items-center gap-2 sm:col-span-2 sm:pt-8">
                  <input
                    id={`vat-${field.id}`}
                    type="checkbox"
                    className="h-4 w-4 rounded border-slate-300 text-brand-600"
                    {...register(`lineItems.${index}.vatApplicable`)}
                  />
                  <label htmlFor={`vat-${field.id}`} className="text-sm text-slate-700">
                    VAT 15%
                  </label>
                </div>
                <div className="flex justify-end sm:col-span-1 sm:pt-6">
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Remove line ${index + 1}`}
                    disabled={fields.length === 1}
                    onClick={() => remove(index)}
                  >
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              </li>
            );
          })}
        </ol>
        <dl
          className="ml-auto max-w-xs space-y-1 border-t border-slate-200 px-5 py-4 text-sm"
          aria-live="polite"
        >
          <div className="flex justify-between">
            <dt className="text-slate-500">Subtotal</dt>
            <dd data-testid="preview-subtotal">{formatMoney(totals.subtotal, currency)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-slate-500">VAT</dt>
            <dd data-testid="preview-vat">{formatMoney(totals.vat, currency)}</dd>
          </div>
          <div className="flex justify-between text-base font-semibold">
            <dt>Total</dt>
            <dd data-testid="preview-total">{formatMoney(totals.total, currency)}</dd>
          </div>
          <p className="pt-1 text-xs text-slate-400">Final totals are calculated by the server.</p>
        </dl>
      </section>

      <section className="card p-5">
        <Field label="Notes" error={errors.notes?.message} hint="Shown on the invoice, e.g. banking details">
          <textarea rows={3} className="input" {...register('notes')} />
        </Field>
      </section>

      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
