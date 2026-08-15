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

async function safeCall(fn) {
  try {
    return { data: await fn(), error: null };
  } catch (err) {
    if (err instanceof ApiError) return { data: null, error: { code: err.code, message: err.message } };
    return { data: null, error: { code: 'UNKNOWN', message: err.message } };
  }
}

export function useSession() {
  const [data, setData] = useState(null);
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
  signIn: {
    email: ({ email, password }) => safeCall(() => api.post('/auth/login', { email, password })),
    // Spring Security's default OAuth2 login redirect convention
    // (spring-boot-starter-oauth2-client) — not a Better-Auth-style call,
    // a real navigation.
    social: ({ provider }) => {
      window.location.href = `/oauth2/authorization/${provider}`;
    },
  },
  signUp: {
    email: ({ email, password, name }) => safeCall(() => api.post('/auth/register', { email, password, name })),
  },
  signOut: () => safeCall(() => api.post('/auth/logout')),
  resetPassword: ({ newPassword, token }) =>
    safeCall(() => api.post('/auth/reset-password', { newPassword, token })),
  sendVerificationEmail: ({ email }) => safeCall(() => api.post('/auth/verify-email/resend', { email })),
  // Generic escape hatch some pages used for one-off calls not otherwise
  // exposed above (e.g. request-password-reset).
  $fetch: (path, options) => safeCall(() => api.post(path, options?.body)),
};

export const { signIn, signUp, signOut, resetPassword, sendVerificationEmail } = authClient;
export const forgetPassword = authClient.$fetch;
