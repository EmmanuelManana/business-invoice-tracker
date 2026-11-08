import type { AuthResponse, Problem } from './types';

/** Error thrown for any non-2xx response; carries the parsed RFC 7807 problem. */
export class ApiError extends Error {
  constructor(public readonly problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`);
    this.name = 'ApiError';
  }

  get status() {
    return this.problem.status;
  }

  get code() {
    return this.problem.code;
  }
}

type SessionListener = (session: AuthResponse | null) => void;

/**
 * The access token lives only in memory (never localStorage) to limit XSS exposure. The refresh
 * token is an HttpOnly cookie the browser sends to /api/v1/auth/* automatically.
 */
let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse | null> | null = null;
const listeners = new Set<SessionListener>();

export function setSession(session: AuthResponse | null) {
  accessToken = session?.accessToken ?? null;
  listeners.forEach((listener) => listener(session));
}

export function onSessionChange(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function hasAccessToken() {
  return accessToken !== null;
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request, so a
 * burst of 401s triggers a single refresh (important because refresh tokens are single-use).
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = acrossTabs(() =>
      fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'same-origin' }),
    )
      .then(async (response) => {
        if (!response.ok) {
          setSession(null);
          return null;
        }
        const session = (await response.json()) as AuthResponse;
        setSession(session);
        return session;
      })
      .catch(() => {
        setSession(null);
        return null;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/**
 * Runs the refresh while holding a lock shared by every tab of this app, so two tabs never send
 * the same refresh cookie at the same moment: the second tab waits, then sends the cookie the first
 * tab just rotated. Browsers without the Web Locks API fall back to running it directly; the
 * server's short grace period covers that case.
 */
function acrossTabs<T>(work: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  return locks ? locks.request('invoice-tracker-refresh', work) : work();
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  /** Return the raw Response (e.g. for file downloads). */
  raw?: boolean;
  /** Internal: prevents infinite refresh loops. */
  retried?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError({
      status: 0,
      title: 'Network error',
      detail: 'Could not reach the server. Check your connection and try again.',
      code: 'network_error',
    });
  }

  const isAuthEndpoint = path.startsWith('/api/v1/auth/');
  if (response.status === 401 && !options.retried && !isAuthEndpoint) {
    const session = await refreshSession();
    if (session) {
      return request<T>(path, { ...options, retried: true });
    }
  }

  if (!response.ok) {
    throw new ApiError(await parseProblem(response));
  }
  if (options.raw) {
    return response as unknown as T;
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function parseProblem(response: Response): Promise<Problem> {
  try {
    const body = (await response.json()) as Problem;
    return { ...body, status: body.status ?? response.status };
  } catch {
    return { status: response.status, title: response.statusText || 'Request failed' };
  }
}

/** Builds a query string, skipping empty values and repeating array params. */
export function toQuery(params: object): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      value.forEach((v) => search.append(key, String(v)));
    } else {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `?${query}` : '';
}
