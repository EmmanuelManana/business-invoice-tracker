import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { onSessionChange, refreshSession } from '@/api/client';
import { authApi } from '@/api/endpoints';
import type { UserProfile } from '@/api/types';
import { AuthContext, type AuthStatus, type RegisterInput } from './useAuth';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);

  useEffect(() => {
    const unsubscribe = onSessionChange((session) => {
      setUser(session?.user ?? null);
      setStatus(session ? 'authenticated' : 'anonymous');
    });
    // Restore the session after a page reload using the HttpOnly refresh cookie.
    refreshSession();
    return unsubscribe;
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    await authApi.login(email, password);
  }, []);

  const register = useCallback(async (input: RegisterInput) => {
    await authApi.register(input);
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout();
  }, []);

  const value = useMemo(
    () => ({ status, user, login, register, logout }),
    [status, user, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
