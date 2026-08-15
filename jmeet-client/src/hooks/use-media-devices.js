'use client';

import { useCallback, useEffect, useState } from 'react';

/**
 * Enumerates camera/mic/speaker devices. Device labels are empty strings
 * until permission has been granted at least once (browser privacy
 * behaviour) — callers should request getUserMedia before relying on
 * labels being populated.
 */
export function useMediaDevices() {
  const [devices, setDevices] = useState({ cameras: [], microphones: [], speakers: [] });

  const refresh = useCallback(async () => {
    if (!navigator.mediaDevices?.enumerateDevices) return;
    const list = await navigator.mediaDevices.enumerateDevices();
    setDevices({
      cameras: list.filter((d) => d.kind === 'videoinput'),
      microphones: list.filter((d) => d.kind === 'audioinput'),
      speakers: list.filter((d) => d.kind === 'audiooutput'),
    });
  }, []);

  useEffect(() => {
    refresh();
    navigator.mediaDevices?.addEventListener?.('devicechange', refresh);
    return () => navigator.mediaDevices?.removeEventListener?.('devicechange', refresh);
  }, [refresh]);

  return { ...devices, refresh };
}
