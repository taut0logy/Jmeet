export class ApiError extends Error {
  status: number;
  code: string;
  details: unknown;

  constructor(status: number, code: string, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

type RequestOptions = Omit<RequestInit, 'method' | 'body'> & {
  method?: string;
  body?: unknown;
};

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

function csrfToken(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith('XSRF-TOKEN='))
    ?.substring('XSRF-TOKEN='.length);
}

// The CSRF cookie is issued on any response, but a mutating request needs it
// to already exist to pass the check — a fresh browser session with no prior
// GET has nothing to send yet. Bootstrap it once rather than require every
// page to have made a GET first.
export async function ensureCsrfToken(): Promise<string | undefined> {
  let token = csrfToken();
  if (!token) {
    await fetch('/api/auth/sessions', { method: 'GET' });
    token = csrfToken();
  }
  return token;
}

async function request(path: string, { method = 'GET', body, ...rest }: RequestOptions = {}) {
  const headers: Record<string, string> = body ? { 'Content-Type': 'application/json' } : {};
  if (!SAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    ...rest,
  });

  if (res.status === 204) return null;

  const isJson = res.headers.get('content-type')?.includes('application/json');
  const payload = isJson ? await res.json() : null;

  if (!res.ok) {
    throw new ApiError(res.status, payload?.code ?? 'UNKNOWN', payload?.error ?? res.statusText, payload?.details);
  }
  return payload;
}

export const api = {
  get: (path: string, options?: RequestOptions) => request(path, { ...options, method: 'GET' }),
  post: (path: string, body?: unknown, options?: RequestOptions) => request(path, { ...options, method: 'POST', body }),
  patch: (path: string, body?: unknown, options?: RequestOptions) => request(path, { ...options, method: 'PATCH', body }),
  delete: (path: string, options?: RequestOptions) => request(path, { ...options, method: 'DELETE' }),
};
