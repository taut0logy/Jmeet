'use client';

import { useEffect, useRef } from 'react';
import { FiMic, FiMicOff, FiVideo, FiVideoOff, FiAlertCircle } from 'react-icons/fi';
import { Button } from '@/components/ui/button';

export function VideoPreview({ stream, error, micEnabled, cameraEnabled, onToggleMic, onToggleCamera, name }) {
  const videoRef = useRef(null);

  useEffect(() => {
    if (videoRef.current) videoRef.current.srcObject = stream ?? null;
  }, [stream]);

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-neutral-900">
      {error ? (
        <div className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center text-white">
          <FiAlertCircle className="size-8 text-destructive" />
          <p className="text-sm">{error}</p>
        </div>
      ) : cameraEnabled && stream ? (
        <video ref={videoRef} autoPlay muted playsInline className="size-full scale-x-[-1] object-cover" />
      ) : (
        <div className="flex h-full items-center justify-center">
          <div className="flex size-20 items-center justify-center rounded-full bg-neutral-700 text-2xl font-medium text-white">
            {(name || '?').trim().charAt(0).toUpperCase()}
          </div>
        </div>
      )}

      {!error ? (
        <div className="absolute inset-x-0 bottom-3 flex items-center justify-center gap-3">
          <Button
            type="button"
            size="icon"
            variant={micEnabled ? 'secondary' : 'destructive'}
            className="rounded-full"
            onClick={onToggleMic}
            aria-label={micEnabled ? 'Mute microphone' : 'Unmute microphone'}
          >
            {micEnabled ? <FiMic className="size-4" /> : <FiMicOff className="size-4" />}
          </Button>
          <Button
            type="button"
            size="icon"
            variant={cameraEnabled ? 'secondary' : 'destructive'}
            className="rounded-full"
            onClick={onToggleCamera}
            aria-label={cameraEnabled ? 'Turn off camera' : 'Turn on camera'}
          >
            {cameraEnabled ? <FiVideo className="size-4" /> : <FiVideoOff className="size-4" />}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
