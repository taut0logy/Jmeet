'use client';

import { AppShell } from '@/components/app/app-shell';

export default function AppLayout({ children }) {
  return <AppShell>{children}</AppShell>;
}
