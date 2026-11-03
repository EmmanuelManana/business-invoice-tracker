import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '@/api/client';

/** A user-facing message for any error thrown by the API client. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? error.problem.title ?? 'Something went wrong';
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Something went wrong';
}

/** Copies server-side field validation errors onto a react-hook-form instance. Returns true if any applied. */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
): boolean {
  if (!(error instanceof ApiError) || !error.problem.errors?.length) {
    return false;
  }
  for (const fieldError of error.problem.errors) {
    setError(fieldError.field as Path<T>, { type: 'server', message: fieldError.message });
  }
  return true;
}
