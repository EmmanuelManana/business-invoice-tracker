import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/Button';
import { Field, FormError } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { AuthShell } from './AuthShell';

const loginSchema = z.object({
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginValues = z.infer<typeof loginSchema>;

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  async function onSubmit(values: LoginValues) {
    setFormError(null);
    try {
      await login(values.email, values.password);
      const from = (location.state as { from?: string } | null)?.from ?? '/';
      navigate(from, { replace: true });
    } catch (error) {
      setFormError(errorMessage(error));
    }
  }

  return (
    <AuthShell title="Sign in" subtitle="Track invoices and get paid on time">
      <form onSubmit={handleSubmit(onSubmit, () => setFormError(null))} noValidate className="space-y-4">
        <FormError>{formError}</FormError>
        <Field label="Email" error={errors.email?.message} required>
          <input type="email" autoComplete="email" className="input" {...register('email')} />
        </Field>
        <Field label="Password" error={errors.password?.message} required>
          <input
            type="password"
            autoComplete="current-password"
            className="input"
            {...register('password')}
          />
        </Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        New here?{' '}
        <Link to="/register" className="font-medium text-brand-600 hover:underline">
          Create a business account
        </Link>
      </p>
    </AuthShell>
  );
}
