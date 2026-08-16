'use client';

import { useMemo } from 'react';
import { Track } from 'livekit-client';
import { useTracks, useSpeakingParticipants, useParticipants } from '@livekit/components-react';
import { ParticipantTile } from './participant-tile';
import type { PeerMeta } from '@/stores/meetingStore';

type VideoGridProps = {
  peers: Record<string, PeerMeta>;
  selfPeerId?: string | null;
  layoutMode?: 'tiled' | 'spotlight' | 'sidebar';
  pinnedPeerId?: string | null;
  incomingVideoOff?: boolean;
  onTogglePin: (peerId: string) => void;
};

export function VideoGrid({
  peers,
  selfPeerId,
  layoutMode = 'tiled',
  pinnedPeerId,
  incomingVideoOff = false,
  onTogglePin,
}: VideoGridProps) {
  const trackRefs = useTracks([Track.Source.Camera, Track.Source.ScreenShare]);
  const speaking = useSpeakingParticipants();
  const liveParticipants = useParticipants();

  const { camTiles, screenTiles, dominantSpeakerId } = useMemo(() => {
    const speakingIds = speaking.map((p) => p.identity);
    const speakingSet = new Set(speakingIds);
    const camByPeer = new Map(
      trackRefs.filter((t) => t.source === Track.Source.Camera).map((t) => [t.participant.identity, t]),
    );
    const screenRefs = trackRefs.filter((t) => t.source === Track.Source.ScreenShare);
    const liveByPeer = new Map(liveParticipants.map((p) => [p.identity, p]));

    const cams = Object.values(peers).map((peer) => {
      const isSelf = peer.peerId === selfPeerId;
      const ref = camByPeer.get(peer.peerId);
      const trackVisible = !!ref && (isSelf || !incomingVideoOff);
      const live = liveByPeer.get(peer.peerId);
      return {
        peer: { ...peer, micOn: live?.isMicrophoneEnabled ?? false, camOn: !!ref },
        track: trackVisible ? (ref!.publication.track?.mediaStreamTrack ?? null) : null,
        isSelf,
        isScreenShare: false as const,
        speaking: speakingSet.has(peer.peerId),
        score: null as number | null, // TODO(polish): per-participant connection quality
      };
    });
    cams.sort((a, b) => (b.isSelf ? 1 : 0) - (a.isSelf ? 1 : 0));

    const screens = incomingVideoOff
      ? []
      : screenRefs.map((ref) => {
          const meta = peers[ref.participant.identity];
          return {
            peer: meta ?? {
              peerId: ref.participant.identity,
              displayName: ref.participant.name || ref.participant.identity,
              role: 'PARTICIPANT' as const,
              handRaised: false,
              micOn: false,
              camOn: false,
            },
            track: ref.publication.track?.mediaStreamTrack ?? null,
            isSelf: ref.participant.identity === selfPeerId,
            isScreenShare: true as const,
            speaking: false,
            score: null as number | null,
          };
        });

    return { camTiles: cams, screenTiles: screens, dominantSpeakerId: speakingIds.find((id) => id !== selfPeerId) ?? null };
  }, [peers, selfPeerId, trackRefs, speaking, liveParticipants, incomingVideoOff]);

  if (layoutMode === 'tiled') {
    return (
      <div className="grid flex-1 auto-rows-fr grid-cols-2 gap-3 overflow-auto p-4 sm:grid-cols-3 lg:grid-cols-4">
        {screenTiles.map((tile) => (
          <div key={`screen-${tile.peer.peerId}`} className="col-span-2 row-span-2">
            <ParticipantTile {...tile} />
          </div>
        ))}
        {camTiles.map((tile) => (
          <ParticipantTile
            key={tile.peer.peerId}
            {...tile}
            pinnable={!tile.isSelf}
            pinned={pinnedPeerId === tile.peer.peerId}
            onTogglePin={onTogglePin}
          />
        ))}
      </div>
    );
  }

  const isSidebar = layoutMode === 'sidebar';
  const spotlightScreen = screenTiles[0] ?? null;
  const spotlightPeerId = pinnedPeerId ?? spotlightScreen?.peer.peerId ?? dominantSpeakerId ?? camTiles[0]?.peer.peerId;
  const mainTile =
    spotlightScreen && (!pinnedPeerId || pinnedPeerId === spotlightScreen.peer.peerId)
      ? spotlightScreen
      : (camTiles.find((t) => t.peer.peerId === spotlightPeerId) ?? camTiles[0]);
  const otherTiles = camTiles.filter((t) => t.peer.peerId !== mainTile?.peer.peerId);

  return (
    <div className={`flex flex-1 gap-3 overflow-hidden p-4 ${isSidebar ? 'flex-row' : 'flex-col'}`}>
      <div className="min-h-0 flex-1">
        {mainTile ? (
          <ParticipantTile
            {...mainTile}
            large
            pinnable={!mainTile.isScreenShare && !mainTile.isSelf}
            pinned={pinnedPeerId === mainTile.peer.peerId}
            onTogglePin={onTogglePin}
          />
        ) : null}
      </div>
      {otherTiles.length > 0 ? (
        <div className={isSidebar ? 'flex w-44 shrink-0 flex-col gap-2 overflow-y-auto' : 'flex h-28 shrink-0 gap-2 overflow-x-auto'}>
          {otherTiles.map((tile) => (
            <div key={tile.peer.peerId} className={isSidebar ? 'aspect-video shrink-0' : 'aspect-video h-full shrink-0'}>
              <ParticipantTile
                {...tile}
                pinnable={!tile.isSelf}
                pinned={pinnedPeerId === tile.peer.peerId}
                onTogglePin={onTogglePin}
              />
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
