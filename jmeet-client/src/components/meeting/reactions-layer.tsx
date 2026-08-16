'use client';

import { useMemo } from 'react';

const EMOJI_MAP = { thumbsup: '👍', clap: '👏', heart: '❤️', laugh: '😂', wow: '😮', sad: '😢' };

// Milestone A2. Purely presentational — reactions already self-clear from
// the store after their animation window (see meetingStore.addReaction).
export function ReactionsLayer({ reactions }) {
  // A stable-per-id horizontal offset so simultaneous reactions don't stack
  // exactly on top of each other, without needing extra store state.
  const offsets = useMemo(() => new Map(), []);
  for (const r of reactions) {
    if (!offsets.has(r.id)) offsets.set(r.id, Math.round((hashCode(r.id) % 200) - 100));
  }

  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      {reactions.map((r) => (
        <span
          key={r.id}
          data-testid="floating-reaction"
          className="absolute bottom-24 left-1/2 text-4xl"
          style={{ marginLeft: offsets.get(r.id), animation: 'float-up 4s ease-out forwards' }}
        >
          {EMOJI_MAP[r.emoji] ?? '👍'}
        </span>
      ))}
    </div>
  );
}

function hashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}
