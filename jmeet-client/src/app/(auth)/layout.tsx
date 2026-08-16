import { Logo } from '@/components/brand/logo';
import { ThemeToggle } from '@/components/theme-toggle';

export default function AuthLayout({ children }) {
  return (
    <div className="min-h-screen flex flex-col bg-muted/30">
      <header className="flex items-center justify-between px-6 py-4">
        <span className="flex items-center gap-2 text-lg font-semibold tracking-tight">
          <Logo size={28} />
          jmeet
        </span>
        <ThemeToggle />
      </header>
      <main className="flex-1 flex items-center justify-center px-4 pb-16">
        <div className="w-full max-w-sm">{children}</div>
      </main>
    </div>
  );
}
