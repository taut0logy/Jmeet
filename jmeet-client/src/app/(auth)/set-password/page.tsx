'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { FiLoader, FiLock } from 'react-icons/fi';
import { useSession } from '@/lib/auth/client';
import { api, ApiError } from '@/lib/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default function SetPasswordPage() {
  const router = useRouter();
  const { data: session, isPending, refetch } = useSession();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isPending) return;
    if (!session) router.replace('/sign-in');
    else if (session.user.hasPassword) router.replace('/dashboard');
  }, [isPending, session, router]);

  const mismatch = confirmPassword.length > 0 && password !== confirmPassword;

  async function handleSubmit(e) {
    e.preventDefault();
    if (mismatch) return;
    setError(null);
    setSubmitting(true);
    try {
      await api.post('/auth/set-password', { newPassword: password });
      await refetch();
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not set your password.');
    } finally {
      setSubmitting(false);
    }
  }

  if (isPending || !session || session.user.hasPassword) {
    return (
      <div className="flex justify-center py-16">
        <FiLoader className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <Card>
      <CardHeader className="items-center text-center">
        <div className="mb-2 flex size-12 items-center justify-center rounded-full bg-primary/10">
          <FiLock className="size-6 text-primary" />
        </div>
        <CardTitle className="text-xl">Set a password</CardTitle>
        <CardDescription>
          You signed up with Google — choose a password so you can also sign in with your email.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="password">New password</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              autoComplete="new-password"
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword">Confirm password</Label>
            <Input
              id="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              minLength={8}
              autoComplete="new-password"
              aria-invalid={mismatch}
            />
            {mismatch ? <p className="text-xs text-destructive">Passwords don&apos;t match.</p> : null}
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <Button type="submit" className="w-full" disabled={submitting || mismatch}>
            {submitting ? <FiLoader className="size-4 animate-spin" /> : null}
            Continue
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
