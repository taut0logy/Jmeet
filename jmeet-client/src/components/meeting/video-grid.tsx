'use client';

import { useMemo } from 'react';
import { ParticipantTile } from './participant-tile';
import type { Peer } from '@/stores/meetingStore';

type VideoGridProps = {
  peers: Record<string, Peer>;
  self?: Peer | null;
  speakingPeerIds?: string[];
  layoutMode?: 'tiled' | 'spotlight' | 'sidebar';
  pinnedPeerId?: string | null;
  dominantSpeakerId?: string | null;
  onTogglePin: (peerId: string) => void;
};

// Tiled / Spotlight / Sidebar, with manual pin overriding the automatic
// spotlight target. `screenTiles` are always preferred as the spotlight
// target over a pinned peer's cam feed only when nothing is pinned — an
// explicit pin always wins. This layout algorithm is unchanged by the SFU
// swap; only where camTiles/screenTiles' tracks come from changes.
//
// TODO(livekit): camTiles/screenTiles used to be built here by manually
// cross-referencing `producers` + `consumers` maps by peerId/source. Replace
// with LiveKit's `useTracks([Track.Source.Camera, Track.Source.ScreenShare])`
// from @livekit/components-react, which returns TrackReference objects
// directly — group those by source into the same
// `{ peer, track, isSelf, isScreenShare, speaking, score }` shape the JSX
// below expects, then delete this stub.
export function VideoGrid({
  peers,
  self,
  speakingPeerIds = [],
  layoutMode = 'tiled',
  pinnedPeerId,
  dominantSpeakerId,
  onTogglePin,
}: VideoGridProps) {
  const { camTiles, screenTiles } = useMemo(() => {
    // Placeholder — see TODO above. Renders name-only tiles (no track) for
    // every known peer so the layout is visible before LiveKit is wired in.
    const speakingSet = new Set(speakingPeerIds);
    const cams = Object.values(peers).map((peer) => ({
      peer,
      track: null,
      isSelf: peer.peerId === self?.peerId,
      speaking: speakingSet.has(peer.peerId),
    }));
    cams.sort((a, b) => (b.isSelf ? 1 : 0) - (a.isSelf ? 1 : 0));
    return { camTiles: cams, screenTiles: [] };
  }, [peers, self, speakingPeerIds]);

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

  // Spotlight/Sidebar share the same "one big + a strip of others" shape;
  // only the strip's orientation differs.
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
