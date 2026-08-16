// Phase A spec §5.7. Muting uses producer.pause(), never track.enabled =
// false, specifically so this analyser keeps reading real audio from the
// live track while muted — that's the whole point of the "you're muted,
// are you talking?" prompt.
type TalkingDetectorOptions = {
  track: MediaStreamTrack | null | undefined;
  onTalking: () => void;
  rmsThreshold?: number;
  sustainedMs?: number;
  cooldownMs?: number;
};

export function createTalkingDetector({
  track,
  onTalking,
  rmsThreshold = 0.02,
  sustainedMs = 1500,
  cooldownMs = 30000,
}: TalkingDetectorOptions) {
  if (!track || typeof window === 'undefined') return { destroy() {} };

  const AudioContextCtor = window.AudioContext || (window as any).webkitAudioContext;
  if (!AudioContextCtor) return { destroy() {} };

  const audioContext = new AudioContextCtor();
  const source = audioContext.createMediaStreamSource(new MediaStream([track]));
  const analyser = audioContext.createAnalyser();
  analyser.fftSize = 512;
  source.connect(analyser);

  const data = new Uint8Array(analyser.fftSize);
  let aboveSince = null;
  let lastFiredAt = 0;
  let rafId = null;
  let stopped = false;

  function tick() {
    if (stopped) return;
    analyser.getByteTimeDomainData(data);
    let sumSquares = 0;
    for (let i = 0; i < data.length; i++) {
      const v = (data[i] - 128) / 128;
      sumSquares += v * v;
    }
    const rms = Math.sqrt(sumSquares / data.length);

    const now = Date.now();
    if (rms > rmsThreshold) {
      if (aboveSince == null) aboveSince = now;
      if (now - aboveSince >= sustainedMs && now - lastFiredAt >= cooldownMs) {
        lastFiredAt = now;
        onTalking();
      }
    } else {
      aboveSince = null;
    }
    rafId = requestAnimationFrame(tick);
  }
  rafId = requestAnimationFrame(tick);

  return {
    destroy() {
      stopped = true;
      if (rafId) cancelAnimationFrame(rafId);
      source.disconnect();
      audioContext.close().catch(() => {});
    },
  };
}
