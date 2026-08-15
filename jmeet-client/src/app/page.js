'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useSession } from '@/lib/auth/client';

export default function RootPage() {
  const { data: session, isPending } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (isPending) return;
    router.replace(session ? '/dashboard' : '/sign-in');
  }, [isPending, session, router]);

  return null;
}
