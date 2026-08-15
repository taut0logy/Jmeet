'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

const MEDIA_ERROR_MESSAGES = {
  NotAllowedError: 'Camera and microphone access was blocked. Allow access in your browser\'s site settings and reload.',
  NotFoundError: 'No camera or microphone was found on this device.',
  NotReadableError: 'Your camera or microphone is already in use by another application.',
  OverconstrainedError: 'The selected camera or microphone is not available.',
};

/**
 * Owns the local preview MediaStream for the pre-join lobby. Each getUserMedia
 * failure gets its own message (Phase B spec §8.2) rather than a generic toast.
 *
 * `initialMicEnabled`/`initialCameraEnabled` seed the toggle state from the
 * signed-in user's saved Profile.defaultMicMuted/defaultCameraOff (spec §8.2,
 * acceptance criterion #9) — since that profile fetch is async and arrives
 * after the stream has already started (starting the camera immediately is
 * better UX than waiting on a network round trip first), a change to either
 * value re-applies to the already-live tracks without restarting the stream.
 */
export function useLocalPreview({ initialMicEnabled = true, initialCameraEnabled = true } = {}) {
  const [stream, setStream] = useState(null);
  const [micEnabled, setMicEnabled] = useState(initialMicEnabled);
  const [cameraEnabled, setCameraEnabled] = useState(initialCameraEnabled);
  const [error, setError] = useState(null);
  const streamRef = useRef(null);
  const enabledRef = useRef({ mic: initialMicEnabled, camera: initialCameraEnabled });

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  }, []);

  const start = useCallback(async ({ audioDeviceId, videoDeviceId } = {}) => {
    setError(null);
    stopStream();
    try {
      const newStream = await navigator.mediaDevices.getUserMedia({
        audio: audioDeviceId ? { deviceId: { exact: audioDeviceId } } : true,
        video: videoDeviceId ? { deviceId: { exact: videoDeviceId } } : true,
      });
      streamRef.current = newStream;
      newStream.getAudioTracks().forEach((t) => (t.enabled = enabledRef.current.mic));
      newStream.getVideoTracks().forEach((t) => (t.enabled = enabledRef.current.camera));
      setStream(newStream);
    } catch (err) {
      setError(MEDIA_ERROR_MESSAGES[err.name] ?? 'Could not access your camera or microphone.');
      setStream(null);
    }
  }, [stopStream]);

  useEffect(() => {
    start();
    return () => stopStream();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Applies the profile defaults if/when they arrive after the stream has
  // already started (see doc comment above).
  useEffect(() => {
    enabledRef.current = { mic: initialMicEnabled, camera: initialCameraEnabled };
    setMicEnabled(initialMicEnabled);
    setCameraEnabled(initialCameraEnabled);
    streamRef.current?.getAudioTracks().forEach((t) => (t.enabled = initialMicEnabled));
    streamRef.current?.getVideoTracks().forEach((t) => (t.enabled = initialCameraEnabled));
    // Only re-apply when the *source* values change (profile loaded), not on
    // every render — deliberately omits stream/track state from deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialMicEnabled, initialCameraEnabled]);

  function toggleMic() {
    const next = !micEnabled;
    setMicEnabled(next);
    enabledRef.current.mic = next;
    streamRef.current?.getAudioTracks().forEach((t) => (t.enabled = next));
  }

  function toggleCamera() {
    const next = !cameraEnabled;
    setCameraEnabled(next);
    enabledRef.current.camera = next;
    streamRef.current?.getVideoTracks().forEach((t) => (t.enabled = next));
  }

  return { stream, error, micEnabled, cameraEnabled, toggleMic, toggleCamera, switchDevice: start };
}
