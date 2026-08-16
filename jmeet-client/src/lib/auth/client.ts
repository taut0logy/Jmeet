'use client';

// TODO(spring-security): replaces `better-auth/react`'s createAuthClient().
// Better Auth's client SDK is hardwired to Better Auth's own wire protocol
// (endpoint shapes, session cookie name, response bodies) — it cannot talk
// to a Spring Security backend at all, so this isn't a rewiring job, it's a
// different implementation behind the same names every page already calls.
// Endpoints below match the backend spec §10. Session state is a bare
// fetch-on-mount hook here; Better Auth's real one was reactive/cached —
// replace with real client-side session state (context, swr, etc.) as
// needed, this is just enough to keep every call site resolving.

import { useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api/client';

type CallResult<T> =
  | { data: T; error: null }
  | { data: null; error: { code: string; message: string; status?: number } };

async function safeCall<T>(fn: () => Promise<T>): Promise<CallResult<T>> {
  try {
    return { data: await fn(), error: null };
  } catch (err) {
    if (err instanceof ApiError) {
      return { data: null, error: { code: err.code, message: err.message, status: err.status } };
    }
    return { data: null, error: { code: 'UNKNOWN', message: err instanceof Error ? err.message : String(err) } };
  }
}

export type Session = {
  user: { name: string; email?: string; image?: string; [key: string]: unknown };
  [key: string]: unknown;
};

export function useSession() {
  const [data, setData] = useState<Session | null>(null);
  const [isPending, setIsPending] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api
      .get('/users/me')
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch(() => {
        if (!cancelled) setData(null);
      })
      .finally(() => {
        if (!cancelled) setIsPending(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { data, isPending };
}

export const authClient = {
  getSession: (): Promise<CallResult<Session>> => safeCall(() => api.get('/users/me')),
  signIn: {
    email: ({ email, password }: { email: string; password: string }) =>
      safeCall(() => api.post('/auth/login', { email, password })),
    // Spring Security's default OAuth2 login redirect convention
    // (spring-boot-starter-oauth2-client) — not a Better-Auth-style call,
    // a real navigation. callbackURL is accepted for call-site compatibility
    // but unused — Spring Security's own post-login redirect applies.
    social: ({ provider }: { provider: string; callbackURL?: string }) => {
      window.location.href = `/oauth2/authorization/${provider}`;
    },
  },
  signUp: {
    email: ({ email, password, name }: { email: string; password: string; name: string; callbackURL?: string }) =>
      safeCall(() => api.post('/auth/register', { email, password, name })),
  },
  signOut: () => safeCall(() => api.post('/auth/logout')),
  resetPassword: ({ newPassword, token }: { newPassword: string; token: string }) =>
    safeCall(() => api.post('/auth/reset-password', { newPassword, token })),
  sendVerificationEmail: ({ email }: { email: string; callbackURL?: string }) =>
    safeCall(() => api.post('/auth/verify-email/resend', { email })),
  // Generic escape hatch some pages used for one-off calls not otherwise
  // exposed above (e.g. request-password-reset). Always POSTs — `method` is
  // accepted for call-site compatibility but unused.
  $fetch: (path: string, options?: { method?: string; body?: unknown }) => safeCall(() => api.post(path, options?.body)),
};

export const { signIn, signUp, signOut, resetPassword, sendVerificationEmail } = authClient;
export const forgetPassword = authClient.$fetch;
