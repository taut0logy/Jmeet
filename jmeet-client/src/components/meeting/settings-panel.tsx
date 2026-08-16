'use client';

import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Label } from '@/components/ui/label';

const WAITING_ROOM_LABELS = { OFF: 'Off', GUESTS_ONLY: 'Guests only', EVERYONE: 'Everyone' };

// host:setFlag (server) / setRoomFlag (connection hook) existed end-to-end
// since Phase A but had no UI calling it — the only way to change these was
// editing the meeting before it started, which never reaches an already-
// running Room (a separate SFU process; there's no live bridge from the
// dashboard's PATCH to it). This panel is that missing UI surface.
export function SettingsPanel({ flags, onSetFlag }) {
  return (
    <div className="flex h-full flex-col gap-5 overflow-y-auto p-4" data-testid="settings-panel">
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-neutral-100">Lock meeting</p>
          <p className="text-xs text-neutral-500">Stop anyone new from joining.</p>
        </div>
        <Switch
          checked={!!flags.locked}
          onCheckedChange={(checked) => onSetFlag('locked', checked)}
          aria-label="Lock meeting"
        />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="waitingRoomPolicy" className="text-neutral-100">
          Waiting room
        </Label>
        <p className="text-xs text-neutral-500">Who a host has to manually let in.</p>
        <Select value={flags.waitingRoom ?? 'GUESTS_ONLY'} onValueChange={(v) => onSetFlag('waitingRoom', v)}>
          <SelectTrigger id="waitingRoomPolicy" className="w-full" data-testid="waiting-room-select">
            <SelectValue>{(v) => WAITING_ROOM_LABELS[v] ?? v}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="OFF">Off — everyone joins directly</SelectItem>
            <SelectItem value="GUESTS_ONLY">Guests only</SelectItem>
            <SelectItem value="EVERYONE">Everyone (except hosts/cohosts)</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
