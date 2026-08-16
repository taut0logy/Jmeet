'use client';

import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Room, Track, type Participant } from 'livekit-client';
import { RoomContext, RoomAudioRenderer, useParticipants, useTracks } from '@livekit/components-react';
import { FiMicOff, FiVideo } from 'react-icons/fi';

const LIVEKIT_URL = process.env.NEXT_PUBLIC_LIVEKIT_URL ?? 'ws://localhost:7880';

export default function EgressPage() {
  const params = useSearchParams();
  const token = params.get('token');
  const roomRef = useRef<Room | null>(null);
  if (!roomRef.current) roomRef.current = new Room({ adaptiveStream: false, dynacast: false });
  const room = roomRef.current;
  const [status, setStatus] = useState<'connecting' | 'connected' | 'error'>('connecting');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      return;
    }
    let cancelled = false;
    room
      .connect(LIVEKIT_URL, token)
      .then(() => {
        if (!cancelled) setStatus('connected');
      })
      .catch(() => {
        if (!cancelled) setStatus('error');
      });
    return () => {
      cancelled = true;
      room.disconnect();
    };
  }, [token, room]);

  return (
    <div className="h-screen w-screen overflow-hidden bg-neutral-900">
      <RoomContext.Provider value={room}>
        {status === 'connected' ? <EgressGrid /> : <EgressPlaceholder errored={status === 'error'} />}
        <RoomAudioRenderer />
      </RoomContext.Provider>
    </div>
  );
}

function EgressGrid() {
  const participants = useParticipants();
  const screenRefs = useTracks([Track.Source.ScreenShare]);

  if (screenRefs.length > 0) {
    const screen = screenRefs[0];
    return (
      <div className="flex h-full w-full items-center justify-center bg-neutral-900 p-6">
        <ScreenShareTile track={screen.publication.track?.mediaStreamTrack} />
      </div>
    );
  }

  if (participants.length === 0) {
    return <EgressPlaceholder />;
  }

  const cols = Math.max(1, Math.ceil(Math.sqrt(participants.length)));
  return (
    <div className="grid h-full w-full gap-3 p-4" style={{ gridTemplateColumns: `repeat(${cols}, 1fr)` }}>
      {participants.map((p) => (
        <EgressTile key={p.identity} participant={p} />
      ))}
    </div>
  );
}

function ScreenShareTile({ track }: { track?: MediaStreamTrack }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const el = videoRef.current;
    if (el && track) el.srcObject = new MediaStream([track]);
    return () => {
      if (el) el.srcObject = null;
    };
  }, [track]);
  return <video ref={videoRef} autoPlay playsInline muted className="max-h-full max-w-full object-contain" />;
}

function EgressTile({ participant }: { participant: Participant }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const camPub = participant.getTrackPublication(Track.Source.Camera);
  const micPub = participant.getTrackPublication(Track.Source.Microphone);
  const hasVideo = !!camPub?.track && !camPub.isMuted;

  useEffect(() => {
    const el = videoRef.current;
    const mediaTrack = camPub?.track?.mediaStreamTrack;
    if (el && mediaTrack && hasVideo) el.srcObject = new MediaStream([mediaTrack]);
    return () => {
      if (el) el.srcObject = null;
    };
  }, [camPub, hasVideo]);

  return (
    <div className="relative flex items-center justify-center overflow-hidden rounded-lg bg-neutral-800">
      {hasVideo ? (
        <video ref={videoRef} autoPlay playsInline muted className="h-full w-full object-cover" />
      ) : (
        <div className="flex size-16 items-center justify-center rounded-full bg-neutral-700 text-xl font-medium text-white">
          {initialsFor(participant.name || participant.identity)}
        </div>
      )}
      <span className="absolute bottom-2 left-2 flex items-center gap-1.5 rounded bg-black/60 px-2 py-1 text-xs text-white">
        {!micPub || micPub.isMuted ? <FiMicOff className="size-3 text-red-400" /> : null}
        {participant.name || participant.identity}
      </span>
    </div>
  );
}

function EgressPlaceholder({ errored = false }: { errored?: boolean }) {
  return (
    <div className="flex h-full w-full flex-col items-center justify-center gap-3 bg-neutral-900 text-neutral-400">
      <FiVideo className="size-10" />
      <p className="text-sm">{errored ? 'Could not connect to this meeting.' : 'Waiting for participants to join…'}</p>
    </div>
  );
}

function initialsFor(name: string) {
  return name
    .split(' ')
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}
