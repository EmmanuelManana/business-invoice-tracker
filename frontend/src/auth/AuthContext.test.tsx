import { act, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { setSession } from '@/api/client';
import type { AuthResponse } from '@/api/types';
import { jsonResponse } from '@/test/render';
import { AuthProvider } from './AuthContext';
import { useAuth } from './useAuth';

function session(userId: string, businessName: string): AuthResponse {
  return {
    accessToken: `token-${userId}`,
    tokenType: 'Bearer',
    expiresAt: '2026-03-01T08:15:00Z',
    user: {
      id: userId,
      email: `${userId}@x.test`,
      fullName: userId,
      businessId: `b-${userId}`,
      businessName,
    },
  };
}

function Status() {
  const { status, user } = useAuth();
  return <p>{status === 'authenticated' ? `signed in as ${user?.id}` : status}</p>;
}

describe('AuthProvider', () => {
  let queryClient: QueryClient;

  beforeEach(async () => {
    // No refresh cookie: the startup refresh fails and the user is anonymous.
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 401 }, 401));
    queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Status />
        </AuthProvider>
      </QueryClientProvider>,
    );
    await screen.findByText('anonymous');
  });

  const cached = () => queryClient.getQueryCache().getAll().length;

  it('clears cached data when the user signs out', () => {
    act(() => setSession(session('alice', 'Alice Co')));
    queryClient.setQueryData(['invoices'], ['alice-invoice']);

    act(() => setSession(null));

    expect(cached()).toBe(0);
    expect(screen.getByText('anonymous')).toBeInTheDocument();
  });

  it('clears cached data when a different user signs in', () => {
    act(() => setSession(session('alice', 'Alice Co')));
    queryClient.setQueryData(['dashboard'], { totalOutstanding: 100 });

    act(() => setSession(session('bob', 'Bob Co')));

    expect(queryClient.getQueryData(['dashboard'])).toBeUndefined();
    expect(screen.getByText('signed in as bob')).toBeInTheDocument();
  });

  it('keeps cached data when the same user refreshes their token', () => {
    act(() => setSession(session('alice', 'Alice Co')));
    queryClient.setQueryData(['dashboard'], { totalOutstanding: 100 });

    act(() => setSession({ ...session('alice', 'Alice Co'), accessToken: 'rotated' }));

    expect(queryClient.getQueryData(['dashboard'])).toEqual({ totalOutstanding: 100 });
  });
});
