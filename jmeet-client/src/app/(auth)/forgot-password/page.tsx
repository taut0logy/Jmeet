'use client';

import { useState } from 'react';
import Link from 'next/link';
import { FiLoader, FiArrowLeft } from 'react-icons/fi';
import { authClient } from '@/lib/auth/client';
import { clientUrl } from '@/lib/auth/urls';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    // Not authClient.forgetPassword() — verified directly (intercepting
    // fetch) that it 404s against this installed better-auth version; the
    // client method name doesn't match the server's actual endpoint,
    // /request-password-reset. $fetch calls the real endpoint directly.
    const { error: resetError } = await authClient.$fetch('/request-password-reset', {
      method: 'POST',
      body: { email, redirectTo: clientUrl('/reset-password') },
    });
    setSubmitting(false);
    if (resetError) {
      setError('Something went wrong. Please try again.');
      return;
    }
    setSent(true);
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Reset your password</CardTitle>
        <CardDescription>We&apos;ll email you a link to choose a new one.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {sent ? (
          <p className="text-sm text-muted-foreground">
            If an account exists for <span className="font-medium text-foreground">{email}</span>, a reset link is
            on its way.
          </p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? <FiLoader className="size-4 animate-spin" /> : null}
              Send reset link
            </Button>
          </form>
        )}
        <Link href="/sign-in" className="flex items-center justify-center gap-1 text-sm text-muted-foreground">
          <FiArrowLeft className="size-3.5" />
          Back to sign in
        </Link>
      </CardContent>
    </Card>
  );
}
