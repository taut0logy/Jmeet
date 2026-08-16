'use client';

let ctx: AudioContext | null = null;

function getContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!ctx) {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    ctx = new Ctor();
  }
  if (ctx.state === 'suspended') ctx.resume().catch(() => {});
  return ctx;
}

type Tone = { freq: number; start: number; duration: number; gain?: number; type?: OscillatorType };

function playTones(tones: Tone[]) {
  const audioCtx = getContext();
  if (!audioCtx) return;
  const now = audioCtx.currentTime;
  for (const tone of tones) {
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = tone.type ?? 'sine';
    osc.frequency.value = tone.freq;

    const peak = tone.gain ?? 0.1;
    const t0 = now + tone.start;
    const t1 = t0 + tone.duration;
    gain.gain.setValueAtTime(0, t0);
    gain.gain.linearRampToValueAtTime(peak, t0 + Math.min(0.015, tone.duration / 3));
    gain.gain.exponentialRampToValueAtTime(0.0001, t1);

    osc.connect(gain).connect(audioCtx.destination);
    osc.start(t0);
    osc.stop(t1 + 0.02);
  }
}

const SOUND_DEFS: Record<string, () => Tone[]> = {
  join: () => [
    { freq: 523.25, start: 0, duration: 0.12, gain: 0.1 },
    { freq: 659.25, start: 0.08, duration: 0.16, gain: 0.12 },
  ],
  leave: () => [
    { freq: 659.25, start: 0, duration: 0.1, gain: 0.09 },
    { freq: 493.88, start: 0.07, duration: 0.14, gain: 0.08 },
  ],
  admitted: () => [
    { freq: 523.25, start: 0, duration: 0.1, gain: 0.1 },
    { freq: 659.25, start: 0.07, duration: 0.1, gain: 0.11 },
    { freq: 783.99, start: 0.14, duration: 0.18, gain: 0.12 },
  ],
  denied: () => [
    { freq: 349.23, start: 0, duration: 0.1, gain: 0.08, type: 'triangle' },
    { freq: 293.66, start: 0.09, duration: 0.16, gain: 0.08, type: 'triangle' },
  ],
  waitingRoomRequest: () => [
    { freq: 587.33, start: 0, duration: 0.09, gain: 0.13, type: 'triangle' },
    { freq: 587.33, start: 0.16, duration: 0.09, gain: 0.13, type: 'triangle' },
  ],
  chat: () => [{ freq: 880, start: 0, duration: 0.05, gain: 0.05 }],
  handRaised: () => [{ freq: 740, start: 0, duration: 0.08, gain: 0.07, type: 'triangle' }],
  recordingStart: () => [
    { freq: 440, start: 0, duration: 0.08, gain: 0.1, type: 'square' },
    { freq: 440, start: 0.12, duration: 0.08, gain: 0.1, type: 'square' },
  ],
  recordingStop: () => [{ freq: 330, start: 0, duration: 0.15, gain: 0.09, type: 'square' }],
};

export function playSound(name: string) {
  const def = SOUND_DEFS[name];
  if (!def) return;
  try {
    playTones(def());
  } catch {}
}
