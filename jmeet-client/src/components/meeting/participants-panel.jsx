'use client';

import { FiMic, FiMicOff, FiVideo, FiVideoOff, FiMoreVertical, FiUserX } from 'react-icons/fi';
import { FaHandPaper } from 'react-icons/fa';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu';

// Milestone A2. Host/cohost get a per-participant action menu; everyone else
// sees a read-only roster.
export function ParticipantsPanel({ peers, selfPeerId, selfRole, onMute, onRemove, onSetRole, onMuteAll }) {
  const isHost = selfRole === 'HOST';
  const isHostOrCohost = isHost || selfRole === 'COHOST';
  const list = Object.values(peers).sort((a, b) => (a.peerId === selfPeerId ? -1 : b.peerId === selfPeerId ? 1 : 0));

  return (
    <div className="flex h-full flex-col">
      {isHostOrCohost ? (
        <div className="border-b border-white/10 p-3">
          <Button type="button" size="sm" variant="secondary" className="w-full" onClick={onMuteAll}>
            Mute all
          </Button>
        </div>
      ) : null}
      <div className="flex-1 overflow-y-auto p-2">
        {list.map((peer) => {
          const isSelf = peer.peerId === selfPeerId;
          return (
            <div
              key={peer.peerId}
              className="flex items-center justify-between gap-2 rounded-md px-2 py-2 hover:bg-white/5"
              data-testid="participant-row"
              data-peer-id={peer.peerId}
            >
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate text-sm text-neutral-100">
                  {peer.displayName}
                  {isSelf ? ' (You)' : ''}
                </span>
                {peer.handRaised ? <FaHandPaper className="size-3.5 shrink-0 text-amber-400" aria-label="Hand raised" /> : null}
                {peer.role === 'HOST' || peer.role === 'COHOST' ? (
                  <span className="shrink-0 rounded bg-white/10 px-1.5 py-0.5 text-[10px] text-neutral-300">
                    {peer.role === 'HOST' ? 'Host' : 'Cohost'}
                  </span>
                ) : null}
              </div>
              <div className="flex shrink-0 items-center gap-1.5 text-neutral-400">
                {peer.micOn ? <FiMic className="size-3.5" /> : <FiMicOff className="size-3.5 text-red-400" />}
                {peer.camOn ? <FiVideo className="size-3.5" /> : <FiVideoOff className="size-3.5" />}
                {isHostOrCohost && !isSelf ? (
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      aria-label={`More options for ${peer.displayName}`}
                      render={<Button type="button" variant="ghost" size="icon-xs" />}
                    >
                      <FiMoreVertical />
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => onMute(peer.peerId, 'audio')} disabled={!peer.micOn}>
                        Mute microphone
                      </DropdownMenuItem>
                      {isHost && peer.role !== 'HOST' ? (
                        <DropdownMenuItem
                          onClick={() => onSetRole(peer.peerId, peer.role === 'COHOST' ? 'PARTICIPANT' : 'COHOST')}
                        >
                          {peer.role === 'COHOST' ? 'Remove as cohost' : 'Make cohost'}
                        </DropdownMenuItem>
                      ) : null}
                      <DropdownMenuSeparator />
                      <DropdownMenuItem variant="destructive" onClick={() => onRemove(peer.peerId)}>
                        <FiUserX /> Remove from meeting
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                ) : null}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
