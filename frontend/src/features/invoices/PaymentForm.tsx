import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { PAYMENT_METHODS, type PaymentInput } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Field, FormError } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { formatMoney, todayIso } from '@/lib/format';

function paymentSchema(balanceDue: number) {
  return z.object({
    amount: z
      .number({ invalid_type_error: 'Enter an amount' })
      .min(0.01, 'Must be at least 0.01')
      .max(balanceDue, `Cannot exceed the balance of ${balanceDue.toFixed(2)}`),
    paidOn: z
      .string()
      .min(1, 'Payment date is required')
      .refine((d) => d <= todayIso(), 'Cannot be in the future'),
    method: z.enum(['EFT', 'CARD', 'CASH', 'OTHER']),
    reference: z.string().max(100).optional(),
  });
}

interface PaymentFormProps {
  balanceDue: number;
  currency: string;
  onSubmit: (input: PaymentInput) => Promise<unknown>;
  onCancel: () => void;
}

export function PaymentForm({ balanceDue, currency, onSubmit, onCancel }: PaymentFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<PaymentInput>({
    resolver: zodResolver(paymentSchema(balanceDue)),
    defaultValues: { amount: balanceDue, paidOn: todayIso(), method: 'EFT', reference: '' },
  });

  async function submit(values: PaymentInput) {
    setFormError(null);
    try {
      await onSubmit({ ...values, reference: values.reference?.trim() || undefined });
    } catch (error) {
      setFormError(errorMessage(error));
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="space-y-4">
      <FormError>{formError}</FormError>
      <p className="text-sm text-slate-600">
        Balance due: <strong>{formatMoney(balanceDue, currency)}</strong>
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Amount" error={errors.amount?.message} required>
          <input
            type="number"
            step="0.01"
            min="0.01"
            inputMode="decimal"
            className="input"
            {...register('amount', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Payment date" error={errors.paidOn?.message} required>
          <input type="date" className="input" {...register('paidOn')} />
        </Field>
        <Field label="Method" error={errors.method?.message} required>
          <select className="input" {...register('method')}>
            {PAYMENT_METHODS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Reference" error={errors.reference?.message}>
          <input className="input" placeholder="e.g. bank reference" {...register('reference')} />
        </Field>
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          Record payment
        </Button>
      </div>
    </form>
  );
}
