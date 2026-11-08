import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { businessApi } from '@/api/endpoints';
import type { BusinessProfile } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Field, FormError } from '@/components/ui/Field';
import { LoadingState } from '@/components/ui/Spinner';
import { ErrorState, PageHeader } from '@/components/ui/States';
import { applyServerErrors, errorMessage } from '@/lib/errors';
import { queryKeys } from '@/lib/queryClient';

const text = (max: number) => z.string().trim().max(max).optional().or(z.literal(''));

const businessSchema = z.object({
  name: z.string().trim().min(1, 'Business name is required').max(200),
  registrationNumber: z
    .string()
    .trim()
    .max(30)
    .regex(/^[0-9A-Za-z/\- ]*$/, 'Use letters, digits, / and - only')
    .optional()
    .or(z.literal('')),
  vatNumber: z
    .string()
    .trim()
    .regex(/^(4\d{9})?$/, 'A South African VAT number has 10 digits and starts with 4')
    .optional()
    .or(z.literal('')),
  email: z.string().trim().email('Enter a valid email address').max(254).optional().or(z.literal('')),
  phone: text(30),
  address: z.object({
    line1: text(200),
    line2: text(200),
    city: text(100),
    province: text(100),
    postalCode: text(20),
    country: text(100),
  }),
  currency: z.string().regex(/^[A-Z]{3}$/, 'Use a 3-letter currency code such as ZAR'),
  paymentTermsDays: z
    .number({ invalid_type_error: 'Enter a number of days' })
    .int('Use whole days')
    .min(0)
    .max(365, 'At most 365 days'),
});

type BusinessValues = z.infer<typeof businessSchema>;

export function SettingsPage() {
  const profile = useQuery({ queryKey: queryKeys.business, queryFn: businessApi.get });

  return (
    <>
      <PageHeader
        title="Business profile"
        description="These details appear on your invoices and reminders."
      />
      {profile.isLoading && <LoadingState />}
      {profile.error && <ErrorState error={profile.error} onRetry={() => profile.refetch()} />}
      {profile.data && <ProfileForm profile={profile.data} />}
    </>
  );
}

function ProfileForm({ profile }: { profile: BusinessProfile }) {
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<BusinessValues>({
    resolver: zodResolver(businessSchema),
    defaultValues: {
      name: profile.name,
      registrationNumber: profile.registrationNumber ?? '',
      vatNumber: profile.vatNumber ?? '',
      email: profile.email ?? '',
      phone: profile.phone ?? '',
      address: {
        line1: profile.address.line1 ?? '',
        line2: profile.address.line2 ?? '',
        city: profile.address.city ?? '',
        province: profile.address.province ?? '',
        postalCode: profile.address.postalCode ?? '',
        country: profile.address.country ?? '',
      },
      currency: profile.currency,
      paymentTermsDays: profile.paymentTermsDays,
    },
  });

  async function onSubmit(values: BusinessValues) {
    setFormError(null);
    try {
      const updated = await businessApi.update({
        ...values,
        registrationNumber: values.registrationNumber || null,
        vatNumber: values.vatNumber || null,
        email: values.email || null,
        phone: values.phone || null,
      });
      queryClient.setQueryData(queryKeys.business, updated);
      reset(values);
      toast.success('Business profile saved');
    } catch (error) {
      if (!applyServerErrors(error, setError)) setFormError(errorMessage(error));
    }
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit, () => setFormError(null))}
      noValidate
      className="card max-w-3xl space-y-6 p-6"
    >
      <FormError>{formError}</FormError>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Business name" error={errors.name?.message} required className="sm:col-span-2">
          <input className="input" {...register('name')} />
        </Field>
        <Field
          label="Registration number"
          error={errors.registrationNumber?.message}
          hint="e.g. 2019/123456/07"
        >
          <input className="input" {...register('registrationNumber')} />
        </Field>
        <Field label="VAT number" error={errors.vatNumber?.message}>
          <input className="input" inputMode="numeric" {...register('vatNumber')} />
        </Field>
        <Field label="Email" error={errors.email?.message}>
          <input type="email" className="input" {...register('email')} />
        </Field>
        <Field label="Phone" error={errors.phone?.message}>
          <input type="tel" className="input" {...register('phone')} />
        </Field>
      </div>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-2 text-sm font-semibold text-slate-900">Address</legend>
        <Field label="Address line 1" error={errors.address?.line1?.message} className="sm:col-span-2">
          <input className="input" {...register('address.line1')} />
        </Field>
        <Field label="Address line 2" error={errors.address?.line2?.message} className="sm:col-span-2">
          <input className="input" {...register('address.line2')} />
        </Field>
        <Field label="City" error={errors.address?.city?.message}>
          <input className="input" {...register('address.city')} />
        </Field>
        <Field label="Province" error={errors.address?.province?.message}>
          <input className="input" {...register('address.province')} />
        </Field>
        <Field label="Postal code" error={errors.address?.postalCode?.message}>
          <input className="input" {...register('address.postalCode')} />
        </Field>
        <Field label="Country" error={errors.address?.country?.message}>
          <input className="input" {...register('address.country')} />
        </Field>
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-2 text-sm font-semibold text-slate-900">Invoicing defaults</legend>
        <Field label="Default currency" error={errors.currency?.message} required>
          <input className="input uppercase" maxLength={3} {...register('currency')} />
        </Field>
        <Field
          label="Payment terms (days)"
          error={errors.paymentTermsDays?.message}
          hint="Used to set the due date of new invoices"
          required
        >
          <input
            type="number"
            min={0}
            max={365}
            className="input"
            {...register('paymentTermsDays', { valueAsNumber: true })}
          />
        </Field>
      </fieldset>

      <div className="flex justify-end">
        <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
          Save profile
        </Button>
      </div>
    </form>
  );
}
