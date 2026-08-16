'use client';

import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { FiMail, FiLoader, FiCheck } from 'react-icons/fi';
import { authClient } from '@/lib/auth/client';
import { clientUrl } from '@/lib/auth/urls';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

function VerifyEmailContent() {
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

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailContent />
    </Suspense>
  );
}
