import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react';

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  className?: string;
  /** A single form control; it receives id, aria-invalid and aria-describedby. */
  children: ReactElement;
  labelHidden?: boolean;
}

/** Label + control + hint/error, wired together for screen readers. */
export function Field({ label, error, hint, required, className, children, labelHidden }: FieldProps) {
  const id = useId();
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ');
  const control = isValidElement(children)
    ? cloneElement(children as ReactElement<Record<string, unknown>>, {
        id,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': describedBy || undefined,
        'aria-required': required || undefined,
      })
    : children;

  return (
    <div className={className}>
      <label
        htmlFor={id}
        className={labelHidden ? 'sr-only' : 'mb-1 block text-sm font-medium text-slate-700'}
      >
        {label}
        {required && (
          <span className="text-red-600" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </label>
      {control}
      {hint && !error && (
        <p id={`${id}-hint`} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-xs text-red-600" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export function FormError({ children }: { children?: ReactNode }) {
  if (!children) return null;
  return (
    <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
      {children}
    </div>
  );
}
