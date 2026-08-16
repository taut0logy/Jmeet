'use client';

import { useEffect } from 'react';
import { api, ApiError } from '@/lib/api/client';
import { useSessionStore, type Session } from '@/lib/auth/session-store';

export type { Session };

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

export function useSession() {
  const session = useSessionStore((s) => s.session);
  const status = useSessionStore((s) => s.status);
  const refetch = useSessionStore((s) => s.refetch);

  useEffect(() => {
    if (status === 'idle') refetch();
  }, [status, refetch]);

  return { data: session, isPending: status === 'idle' || status === 'loading', refetch };
}

export const authClient = {
  getSession: async (): Promise<CallResult<Session>> => {
    await useSessionStore.getState().refetch();
    const session = useSessionStore.getState().session;
    return session ? { data: session, error: null } : { data: null, error: { code: 'NO_SESSION', message: 'Not signed in.' } };
  },
  signIn: {
    email: async ({ email, password }: { email: string; password: string }) => {
      const result = await safeCall(() => api.post('/auth/login', { email, password }));
      if (!result.error) await useSessionStore.getState().refetch();
      return result;
    },
    social: ({ provider }: { provider: string; callbackURL?: string }) => {
      const apiOrigin = process.env.NEXT_PUBLIC_API_ORIGIN ?? 'http://localhost:8080';
      window.location.href = `${apiOrigin}/oauth2/authorization/${provider}`;
    },
  },
  signUp: {
    email: ({ email, password, name }: { email: string; password: string; name: string; callbackURL?: string }) =>
      safeCall(() => api.post('/auth/register', { email, password, name })),
  },
  signOut: async () => {
    const result = await safeCall(() => api.post('/auth/logout'));
    useSessionStore.getState().setSession(null);
    return result;
  },
  resetPassword: ({ newPassword, token }: { newPassword: string; token: string }) =>
    safeCall(() => api.post('/auth/reset-password', { newPassword, token })),
  sendVerificationEmail: ({ email }: { email: string; callbackURL?: string }) =>
    safeCall(() => api.post('/auth/verify-email/resend', { email })),
  $fetch: (path: string, options?: { method?: string; body?: unknown }) => safeCall(() => api.post(path, options?.body)),
};

export const { signIn, signUp, signOut, resetPassword, sendVerificationEmail } = authClient;
export const forgetPassword = authClient.$fetch;
