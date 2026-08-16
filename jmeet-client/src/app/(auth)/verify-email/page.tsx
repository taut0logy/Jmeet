'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { FiMail, FiLoader, FiCheck, FiAlertTriangle } from 'react-icons/fi';
import { authClient } from '@/lib/auth/client';
import { clientUrl } from '@/lib/auth/urls';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

function TokenVerification({ token }: { token: string }) {
  const router = useRouter();
  const [state, setState] = useState<'verifying' | 'done' | 'error'>('verifying');

  useEffect(() => {
    let cancelled = false;
    authClient.$fetch('/auth/verify-email', { body: { token } }).then(({ error }) => {
      if (cancelled) return;
      setState(error ? 'error' : 'done');
      if (!error) setTimeout(() => router.push('/sign-in'), 1800);
    });
    return () => {
      cancelled = true;
    };
  }, [token, router]);

  return (
    <Card>
      <CardHeader className="items-center text-center">
        <div
          className={`mb-2 flex size-12 items-center justify-center rounded-full ${state === 'error' ? 'bg-destructive/10' : 'bg-primary/10'}`}
        >
          {state === 'verifying' ? (
            <FiLoader className="size-6 animate-spin text-primary" />
          ) : state === 'done' ? (
            <FiCheck className="size-6 text-primary" />
          ) : (
            <FiAlertTriangle className="size-6 text-destructive" />
          )}
        </div>
        <CardTitle className="text-xl">
          {state === 'verifying' ? 'Verifying your email…' : state === 'done' ? 'Email verified' : 'Link expired'}
        </CardTitle>
        <CardDescription>
          {state === 'verifying' && 'Just a moment.'}
          {state === 'done' && 'Redirecting you to sign in…'}
          {state === 'error' && 'This verification link is invalid or has expired.'}
        </CardDescription>
      </CardHeader>
      {state === 'error' ? (
        <CardContent className="flex justify-center">
          <Button variant="outline" render={<Link href="/verify-email" />}>
            Request a new link
          </Button>
        </CardContent>
      ) : null}
    </Card>
  );
}

function ResendVerification() {
  const params = useSearchParams();
  const email = params.get('email') ?? '';
  const [sent, setSent] = useState(false);
  const [sending, setSending] = useState(false);

  async function handleResend() {
    setSending(true);
    await authClient.sendVerificationEmail({ email, callbackURL: clientUrl('/dashboard') });
    setSending(false);
    setSent(true);
  }

  return (
    <Card>
      <CardHeader className="items-center text-center">
        <div className="mb-2 flex size-12 items-center justify-center rounded-full bg-primary/10">
          <FiMail className="size-6 text-primary" />
        </div>
        <CardTitle className="text-xl">Check your email</CardTitle>
        <CardDescription>
          {email ? (
            <>
              We sent a verification link to <span className="font-medium text-foreground">{email}</span>.
            </>
          ) : (
            'We sent you a verification link.'
          )}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-3">
        <p className="text-center text-sm text-muted-foreground">
          Click the link in the email to verify your account, then sign in.
        </p>
        <Button variant="outline" onClick={handleResend} disabled={sending || !email}>
          {sending ? <FiLoader className="size-4 animate-spin" /> : sent ? <FiCheck className="size-4" /> : null}
          {sent ? 'Sent again' : 'Resend email'}
        </Button>
      </CardContent>
    </Card>
  );
}

function VerifyEmailContent() {
  const params = useSearchParams();
  const token = params.get('token');
  return token ? <TokenVerification token={token} /> : <ResendVerification />;
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailContent />
    </Suspense>
  );
}
