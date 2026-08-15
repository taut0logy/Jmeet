'use client';

import { useMemo, useState } from 'react';
import { FiLoader } from 'react-icons/fi';
import { RecurrenceEditor } from './recurrence-editor';
import { buildRRuleString, defaultRecurrenceState, parseRRuleString } from '@/lib/recurrence';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const DURATIONS = [15, 30, 45, 60, 90, 120];
const TIMEZONES = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ['UTC'];

const ACCESS_LABELS = {
  LINK: 'Anyone with the link',
  AUTHENTICATED: 'Signed-in users only',
  INVITED_ONLY: 'Invited only',
};
const WAITING_ROOM_LABELS = { OFF: 'Off', GUESTS_ONLY: 'Guests only', EVERYONE: 'Everyone' };

function toDateTimeLocalValue(date) {
  const d = new Date(date);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function MeetingForm({ initial, onSubmit, submitting, submitLabel = 'Save', recurrenceLocked = false }) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [startsAtLocal, setStartsAtLocal] = useState(
    toDateTimeLocalValue(initial?.startsAt ?? new Date(Date.now() + 60 * 60 * 1000)),
  );
  const [durationMin, setDurationMin] = useState(initial?.durationMin ?? 60);
  const [timezone, setTimezone] = useState(
    initial?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
  );
  const [recurrence, setRecurrence] = useState(
    initial?.rrule ? parseRRuleString(initial.rrule) : defaultRecurrenceState(),
  );
  const [access, setAccess] = useState(initial?.access ?? 'LINK');
  const [waitingRoom, setWaitingRoom] = useState(initial?.waitingRoom ?? 'GUESTS_ONLY');
  const [allowGuests, setAllowGuests] = useState(initial?.allowGuests ?? true);
  const [muteOnEntry, setMuteOnEntry] = useState(initial?.muteOnEntry ?? false);
  const [cameraOffOnEntry, setCameraOffOnEntry] = useState(initial?.cameraOffOnEntry ?? false);

  const sortedTimezones = useMemo(() => [...TIMEZONES].sort(), []);

  function handleSubmit(e) {
    e.preventDefault();
    onSubmit({
      title,
      description: description || undefined,
      kind: 'SCHEDULED',
      startsAt: new Date(startsAtLocal).toISOString(),
      durationMin,
      timezone,
      rrule: recurrenceLocked ? undefined : buildRRuleString(recurrence) ?? null,
      access,
      waitingRoom,
      allowGuests,
      muteOnEntry,
      cameraOffOnEntry,
    });
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="title">Title</Label>
          <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="description">Description</Label>
          <Input
            id="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={2000}
          />
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="startsAt">Starts</Label>
          <Input
            id="startsAt"
            type="datetime-local"
            value={startsAtLocal}
            onChange={(e) => setStartsAtLocal(e.target.value)}
            required
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="durationMin">Duration</Label>
          <Select value={String(durationMin)} onValueChange={(v) => setDurationMin(Number(v))}>
            <SelectTrigger id="durationMin" className="w-full">
              <SelectValue>{(value) => `${value} minutes`}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {DURATIONS.map((m) => (
                <SelectItem key={m} value={String(m)}>
                  {m} minutes
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="timezone">Timezone</Label>
        <Select value={timezone} onValueChange={setTimezone}>
          <SelectTrigger id="timezone" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent className="max-h-64">
            {sortedTimezones.map((tz) => (
              <SelectItem key={tz} value={tz}>
                {tz}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <RecurrenceEditor value={recurrence} onChange={setRecurrence} disabled={recurrenceLocked} />

      <div className="space-y-3 rounded-lg border p-4">
        <p className="text-sm font-medium">Access</p>
        <div className="space-y-1.5">
          <Label htmlFor="access">Who can join</Label>
          <Select value={access} onValueChange={setAccess}>
            <SelectTrigger id="access" className="w-full">
              <SelectValue>{(value) => ACCESS_LABELS[value] ?? value}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="LINK">Anyone with the link</SelectItem>
              <SelectItem value="AUTHENTICATED">Signed-in users only</SelectItem>
              <SelectItem value="INVITED_ONLY">Invited only</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="waitingRoom">Waiting room</Label>
          <Select value={waitingRoom} onValueChange={setWaitingRoom}>
            <SelectTrigger id="waitingRoom" className="w-full">
              <SelectValue>{(value) => WAITING_ROOM_LABELS[value] ?? value}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="OFF">Off</SelectItem>
              <SelectItem value="GUESTS_ONLY">Guests only</SelectItem>
              <SelectItem value="EVERYONE">Everyone</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center justify-between">
          <Label htmlFor="allowGuests">Allow guests (no account required)</Label>
          <Switch id="allowGuests" checked={allowGuests} onCheckedChange={setAllowGuests} />
        </div>
        <div className="flex items-center justify-between">
          <Label htmlFor="muteOnEntry">Mute participants on entry</Label>
          <Switch id="muteOnEntry" checked={muteOnEntry} onCheckedChange={setMuteOnEntry} />
        </div>
        <div className="flex items-center justify-between">
          <Label htmlFor="cameraOffOnEntry">Turn camera off on entry</Label>
          <Switch id="cameraOffOnEntry" checked={cameraOffOnEntry} onCheckedChange={setCameraOffOnEntry} />
        </div>
      </div>

      <Button type="submit" disabled={submitting}>
        {submitting ? <FiLoader className="size-4 animate-spin" /> : null}
        {submitLabel}
      </Button>
    </form>
  );
}
