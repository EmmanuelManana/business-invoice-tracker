import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/Button';
import { Field, FormError } from '@/components/ui/Field';
import { applyServerErrors, errorMessage } from '@/lib/errors';
import { AuthShell } from './AuthShell';

const registerSchema = z.object({
  businessName: z.string().trim().min(1, 'Business name is required').max(200),
  fullName: z.string().trim().min(1, 'Your name is required').max(200),
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z
    .string()
    .min(8, 'Use at least 8 characters')
    .max(128)
    .regex(/[a-z]/, 'Include a lower-case letter')
    .regex(/[A-Z]/, 'Include an upper-case letter')
    .regex(/\d/, 'Include a digit'),
});

type RegisterValues = z.infer<typeof registerSchema>;

export function RegisterPage() {
  const { register: registerAccount } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { businessName: '', fullName: '', email: '', password: '' },
  });

  async function onSubmit(values: RegisterValues) {
    setFormError(null);
    try {
      await registerAccount(values);
      toast.success('Welcome! Your business account is ready.');
      navigate('/', { replace: true });
    } catch (error) {
      if (!applyServerErrors(error, setError)) setFormError(errorMessage(error));
    }
  }

  return (
    <AuthShell title="Create your account" subtitle="Set up your business in under a minute">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <FormError>{formError}</FormError>
        <Field label="Business name" error={errors.businessName?.message} required>
          <input className="input" autoComplete="organization" {...register('businessName')} />
        </Field>
        <Field label="Your name" error={errors.fullName?.message} required>
          <input className="input" autoComplete="name" {...register('fullName')} />
        </Field>
        <Field label="Email" error={errors.email?.message} required>
          <input type="email" className="input" autoComplete="email" {...register('email')} />
        </Field>
        <Field
          label="Password"
          error={errors.password?.message}
          hint="At least 8 characters with upper- and lower-case letters and a digit"
          required
        >
          <input type="password" className="input" autoComplete="new-password" {...register('password')} />
        </Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand-600 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
