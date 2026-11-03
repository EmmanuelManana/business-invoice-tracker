import { jsonResponse } from '@/test/render';
import { ApiError, request, setSession, toQuery } from './client';

const session = {
  accessToken: 'new-token',
  tokenType: 'Bearer' as const,
  expiresAt: '2026-03-01T08:15:00Z',
  user: { id: 'u', email: 'a@b.test', fullName: 'A', businessId: 'b', businessName: 'Biz' },
};

describe('api client', () => {
  beforeEach(() => setSession({ ...session, accessToken: 'old-token' }));

  it('sends the bearer token', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ ok: true }));

    await request('/api/v1/things');

    const headers = fetchMock.mock.calls[0]![1]!.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer old-token');
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ status: 401, code: 'unauthorized' }, 401))
      .mockResolvedValueOnce(jsonResponse(session))
      .mockResolvedValueOnce(jsonResponse({ value: 42 }));

    const result = await request<{ value: number }>('/api/v1/things');

    expect(result.value).toBe(42);
    expect(fetchMock.mock.calls[1]![0]).toBe('/api/v1/auth/refresh');
    const retryHeaders = fetchMock.mock.calls[2]![1]!.headers as Record<string, string>;
    expect(retryHeaders.Authorization).toBe('Bearer new-token');
  });

  it('shares one refresh between concurrent 401s', async () => {
    let refreshCalls = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      if (input === '/api/v1/auth/refresh') {
        refreshCalls++;
        return jsonResponse(session);
      }
      const auth = (init?.headers as Record<string, string>).Authorization;
      return auth === 'Bearer new-token' ? jsonResponse({ ok: true }) : jsonResponse({ status: 401 }, 401);
    });

    await Promise.all([request('/api/v1/a'), request('/api/v1/b'), request('/api/v1/c')]);

    expect(refreshCalls).toBe(1);
  });

  it('throws ApiError carrying the RFC 7807 problem', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(
        {
          status: 409,
          code: 'invalid_state_transition',
          detail: 'Invoice cannot transition from PAID to CANCELLED',
        },
        409,
        'application/problem+json',
      ),
    );

    const error = await request('/api/v1/invoices/1/cancel', { method: 'POST' }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).code).toBe('invalid_state_transition');
    expect((error as ApiError).message).toContain('PAID to CANCELLED');
  });

  it('reports network failures as ApiError with status 0', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(request('/api/v1/things')).rejects.toMatchObject({ status: 0, code: 'network_error' });
  });

  it('builds query strings, skipping empty values and repeating arrays', () => {
    expect(toQuery({ status: ['SENT', 'OVERDUE'], search: '', page: 0, customerId: undefined })).toBe(
      '?status=SENT&status=OVERDUE&page=0',
    );
    expect(toQuery({})).toBe('');
  });
});
