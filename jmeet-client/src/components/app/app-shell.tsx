'use client';

import Link from 'next/link';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { FiLoader, FiLogOut, FiSettings } from 'react-icons/fi';
import { useSession, signOut } from '@/lib/auth/client';
import { Logo } from '@/components/brand/logo';
import { ThemeToggle } from '@/components/theme-toggle';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

function initialsFor(name?: string | null) {
  if (!name) return '?';
  return name
    .split(' ')
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const { data: session, isPending } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (isPending) return;
    if (!session) router.replace('/sign-in');
    else if (session.user.hasPassword === false) router.replace('/set-password');
  }, [isPending, session, router]);

  if (isPending || !session || session.user.hasPassword === false) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FiLoader className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-50 flex items-center justify-between border-b border-border/60 bg-background/85 px-6 py-3 backdrop-blur-md supports-backdrop-filter:bg-background/70">
        <Link href="/dashboard" className="flex items-center gap-2 text-lg font-semibold tracking-tight">
          <Logo size={26} />
          jmeet
        </Link>
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="Account menu"
              render={<Button variant="ghost" className="h-9 gap-2 rounded-full p-1" />}
            >
              <Avatar className="size-7 ring-1 ring-border">
                <AvatarImage src={session.user.image ?? undefined} alt={session.user.name} />
                <AvatarFallback>{initialsFor(session.user.name)}</AvatarFallback>
              </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <div className="px-2 py-1.5 text-sm">
                <p className="break-words font-medium leading-none">{session.user.name}</p>
                <p className="mt-1 break-words text-xs text-muted-foreground">{session.user.email}</p>
              </div>
              <DropdownMenuSeparator />
              <DropdownMenuItem render={<Link href="/settings/profile" />}>
                <FiSettings className="size-4" />
                Settings
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={async () => {
                  await signOut();
                  router.push('/sign-in');
                }}
              >
                <FiLogOut className="size-4" />
                Sign out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>
      <main className="flex-1 px-6 py-10">
        <div className="mx-auto max-w-4xl">{children}</div>
      </main>
    </div>
  );
}
