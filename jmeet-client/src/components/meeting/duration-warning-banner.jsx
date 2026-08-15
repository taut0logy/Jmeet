'use client';

import { useEffect, useState } from 'react';
import { FiClock } from 'react-icons/fi';

function formatRemaining(ms) {
  const totalSeconds = Math.max(0, Math.round(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

// Scheduled meetings have a hard duration cutoff (roomManager's
// scheduleDurationEnforcement) — this is the 5-minutes-before warning shown
// to every participant, not just the host. `endsAt` is an absolute
// timestamp from the server, not a countdown number, so this stays correct
// regardless of when it renders or how stale the tab's JS timers get.
export function DurationWarningBanner({ endsAt }) {
  // The lazy useState initializer (runs once, at mount) is the only place
  // Date.now() is read outside a callback — the interval below re-reads it
  // every second from inside its own callback, which is the sanctioned
  // "setState from a callback when external state changes" pattern, not a
  // synchronous effect-body setState call.
  const [remainingMs, setRemainingMs] = useState(() => (endsAt ? new Date(endsAt).getTime() - Date.now() : 0));

  useEffect(() => {
    if (!endsAt) return undefined;
    const target = new Date(endsAt).getTime();
    const timer = setInterval(() => setRemainingMs(target - Date.now()), 1000);
    return () => clearInterval(timer);
  }, [endsAt]);

  if (!endsAt) return null;

  return (
    <div
      className="absolute left-1/2 top-4 flex -translate-x-1/2 items-center gap-2 rounded-md bg-amber-500/90 px-3 py-1.5 text-xs font-medium text-black shadow"
      data-testid="duration-warning-banner"
    >
      <FiClock className="size-3.5" />
      This meeting ends in {formatRemaining(remainingMs)}
    </div>
  );
}
