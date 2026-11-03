import { createContext, useContext } from 'react';
import type { UserProfile } from '@/api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface RegisterInput {
  businessName: string;
  fullName: string;
  email: string;
  password: string;
}

export interface AuthContextValue {
  status: AuthStatus;
  user: UserProfile | null;
  login: (email: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
