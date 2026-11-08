import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { onSessionChange, refreshSession } from '@/api/client';
import { authApi } from '@/api/endpoints';
import type { UserProfile } from '@/api/types';
import { AuthContext, type AuthStatus, type RegisterInput } from './useAuth';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);
  const currentUserId = useRef<string | null>(null);

  useEffect(() => {
    const unsubscribe = onSessionChange((session) => {
      const nextUserId = session?.user.id ?? null;
      // Cached data belongs to whoever fetched it. Drop it on sign-out or when a different user
      // signs in, so nobody ever sees the previous business's data, not even briefly.
      if (nextUserId !== currentUserId.current) {
        queryClient.clear();
        currentUserId.current = nextUserId;
      }
      setUser(session?.user ?? null);
      setStatus(session ? 'authenticated' : 'anonymous');
    });
    // Restore the session after a page reload using the HttpOnly refresh cookie.
    refreshSession();
    return unsubscribe;
  }, [queryClient]);

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
