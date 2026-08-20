'use client';

import { FiUserPlus, FiUserX } from 'react-icons/fi';
import { Button } from '@/components/ui/button';

// A dedicated waiting-room queue panel (host/cohost only)
export function WaitingPanel({ waiting, onAdmit, onDeny, onAdmitAll }) {
  return (
    <div className="flex h-full flex-col">
      {waiting.length > 1 ? (
        <div className="border-b border-white/10 p-3">
          <Button type="button" size="sm" variant="secondary" className="w-full" onClick={onAdmitAll}>
            Admit all ({waiting.length})
          </Button>
        </div>
      ) : null}
      <div className="flex-1 overflow-y-auto p-2">
        {waiting.length === 0 ? (
          <p className="pt-8 text-center text-sm text-neutral-500">No one is waiting.</p>
        ) : (
          waiting.map((w) => (
            <div
              key={w.peerId}
              className="flex items-center justify-between gap-2 rounded-md px-2 py-2 hover:bg-white/5"
              data-testid="waiting-row"
            >
              <span className="truncate text-sm text-neutral-100">{w.displayName}</span>
              <div className="flex shrink-0 gap-1.5">
                <Button
                  type="button"
                  size="xs"
                  variant="secondary"
                  aria-label={`Admit ${w.displayName}`}
                  onClick={() => onAdmit(w.peerId)}
                >
                  <FiUserPlus className="size-3" /> Admit
                </Button>
                <Button
                  type="button"
                  size="xs"
                  variant="ghost"
                  aria-label={`Deny ${w.displayName}`}
                  onClick={() => onDeny(w.peerId)}
                >
                  <FiUserX className="size-3" /> Deny
                </Button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
