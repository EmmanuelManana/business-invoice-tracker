import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import type { Customer, CustomerInput } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Field, FormError } from '@/components/ui/Field';
import { applyServerErrors, errorMessage } from '@/lib/errors';

const optional = (max: number) => z.string().trim().max(max).optional().or(z.literal(''));

const customerSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200),
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address').max(254),
  phone: z
    .string()
    .trim()
    .max(30)
    .regex(/^[+0-9 ()-]*$/, 'Use digits, spaces, +, - and brackets only')
    .optional()
    .or(z.literal('')),
  billingAddress: z.object({
    line1: optional(200),
    line2: optional(200),
    city: optional(100),
    province: optional(100),
    postalCode: optional(20),
    country: optional(100),
  }),
});

type CustomerValues = z.infer<typeof customerSchema>;

interface CustomerFormProps {
  customer?: Customer;
  onSubmit: (input: CustomerInput) => Promise<unknown>;
  onCancel: () => void;
}

export function CustomerForm({ customer, onSubmit, onCancel }: CustomerFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CustomerValues>({
    resolver: zodResolver(customerSchema),
    defaultValues: {
      name: customer?.name ?? '',
      email: customer?.email ?? '',
      phone: customer?.phone ?? '',
      billingAddress: {
        line1: customer?.billingAddress.line1 ?? '',
        line2: customer?.billingAddress.line2 ?? '',
        city: customer?.billingAddress.city ?? '',
        province: customer?.billingAddress.province ?? '',
        postalCode: customer?.billingAddress.postalCode ?? '',
        country: customer?.billingAddress.country ?? 'South Africa',
      },
    },
  });

  async function submit(values: CustomerValues) {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (!applyServerErrors(error, setError)) setFormError(errorMessage(error));
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="space-y-4">
      <FormError>{formError}</FormError>
      <Field label="Name" error={errors.name?.message} required>
        <input className="input" autoComplete="organization" {...register('name')} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Email" error={errors.email?.message} required>
          <input type="email" className="input" {...register('email')} />
        </Field>
        <Field label="Phone" error={errors.phone?.message} hint="Used for SMS reminders">
          <input type="tel" className="input" {...register('phone')} />
        </Field>
      </div>
      <fieldset className="space-y-3">
        <legend className="text-sm font-medium text-slate-700">Billing address</legend>
        <Field label="Address line 1" error={errors.billingAddress?.line1?.message} labelHidden>
          <input className="input" placeholder="Street address" {...register('billingAddress.line1')} />
        </Field>
        <Field label="Address line 2" error={errors.billingAddress?.line2?.message} labelHidden>
          <input
            className="input"
            placeholder="Suite, unit (optional)"
            {...register('billingAddress.line2')}
          />
        </Field>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="City" error={errors.billingAddress?.city?.message} labelHidden>
            <input className="input" placeholder="City" {...register('billingAddress.city')} />
          </Field>
          <Field label="Province" error={errors.billingAddress?.province?.message} labelHidden>
            <input className="input" placeholder="Province" {...register('billingAddress.province')} />
          </Field>
          <Field label="Postal code" error={errors.billingAddress?.postalCode?.message} labelHidden>
            <input className="input" placeholder="Postal code" {...register('billingAddress.postalCode')} />
          </Field>
        </div>
      </fieldset>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {customer ? 'Save changes' : 'Add customer'}
        </Button>
      </div>
    </form>
  );
}
