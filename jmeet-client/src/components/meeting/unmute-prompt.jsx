'use client';

import { FiMic } from 'react-icons/fi';
import { Button } from '@/components/ui/button';

// The local AnalyserNode (src/lib/media/analyser.js) detected sustained
// voice while the local mic track is muted.
export function UnmutePrompt({ visible, onUnmute, onDismiss }) {
  if (!visible) return null;
  return (
    <div
      className="absolute bottom-24 left-1/2 flex -translate-x-1/2 items-center gap-3 rounded-lg bg-neutral-800 px-4 py-2 text-sm text-white shadow-lg"
      data-testid="unmute-prompt"
    >
      <FiMic className="text-amber-400" />
      <span>You&apos;re muted. Talking?</span>
      <Button type="button" size="xs" onClick={onUnmute}>
        Unmute
      </Button>
      <Button type="button" size="xs" variant="ghost" aria-label="Dismiss" onClick={onDismiss}>
        Dismiss
      </Button>
    </div>
  );
}
