'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { FiLoader, FiCopy, FiTrash2, FiUserPlus, FiVideo, FiDownload, FiFilm } from 'react-icons/fi';
import { MeetingForm } from '@/components/meetings/meeting-form';
import { api, ApiError } from '@/lib/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

// scope=following can change title/duration/time-of-day but never the
// recurrence pattern itself (Phase B spec §5.4) — a deliberate one-Meeting-
// row-per-series design trade-off. This dialog is the UI surface for that:
// it only exposes the fields the API actually accepts for this scope, so
// there's nothing to "disable" — the pattern controls simply aren't here.
function EditFollowingDialog({ open, onOpenChange, occurrence, onSave }) {
  const [title, setTitle] = useState('');
  const [durationMin, setDurationMin] = useState(60);
  const [startTimeLocal, setStartTimeLocal] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (occurrence) {
      const d = new Date(occurrence.startsAt);
      setTitle(occurrence.title ?? '');
      setDurationMin(occurrence.durationMin ?? 60);
      setStartTimeLocal(`${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`);
    }
  }, [occurrence]);

  async function handleSave() {
    setSaving(true);
    try {
      await onSave({ title, durationMin: Number(durationMin), startTimeLocal });
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit this and following</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="following-title">Title</Label>
            <Input id="following-title" value={title} onChange={(e) => setTitle(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="following-time">Time</Label>
            <Input
              id="following-time"
              type="time"
              value={startTimeLocal}
              onChange={(e) => setStartTimeLocal(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="following-duration">Duration (minutes)</Label>
            <Input
              id="following-duration"
              type="number"
              min={5}
              max={1440}
              value={durationMin}
              onChange={(e) => setDurationMin(Number(e.target.value))}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            The recurrence pattern can&apos;t change from here — edit the whole series below to change it.
          </p>
        </div>
        <DialogFooter>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? <FiLoader className="size-4 animate-spin" /> : null}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function formatOccurrence(startsAt) {
  return new Date(startsAt).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatDuration(ms) {
  if (!ms) return '—';
  const totalSeconds = Math.round(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function formatSize(bytes) {
  if (!bytes) return '—';
  const mb = bytes / (1024 * 1024);
  return mb >= 1024 ? `${(mb / 1024).toFixed(2)} GB` : `${mb.toFixed(1)} MB`;
}

const RECORDING_STATUS_STYLES = {
  RECORDING: 'bg-red-500/20 text-red-400',
  PROCESSING: 'bg-amber-500/20 text-amber-500',
  READY: 'bg-emerald-500/20 text-emerald-500',
  FAILED: 'bg-neutral-500/20 text-neutral-400',
};

const RECORDING_STATUS_LABELS = {
  RECORDING: 'Recording',
  PROCESSING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
};

function RecordingStatusBadge({ status }) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${RECORDING_STATUS_STYLES[status] ?? RECORDING_STATUS_STYLES.FAILED}`}
    >
      {RECORDING_STATUS_LABELS[status] ?? status}
    </span>
  );
}

export default function MeetingDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const [meeting, setMeeting] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [editingOccurrence, setEditingOccurrence] = useState(null);
  const [recordings, setRecordings] = useState(null);

  const load = useCallback(async () => {
    try {
      const data = await api.get(`/meetings/${id}`);
      setMeeting(data);
    } catch {
      toast.error('Could not load this meeting.');
      router.push('/dashboard');
    }
  }, [id, router]);

  const loadRecordings = useCallback(async () => {
    try {
      const data = await api.get(`/meetings/${id}/recordings`);
      setRecordings(data.recordings);
    } catch {
      // Not every viewer of this page is host/cohost-eligible for the
      // recordings sub-resource even when they can see the meeting itself —
      // leave the section absent rather than surface a scary error toast.
      setRecordings([]);
    }
  }, [id]);

  useEffect(() => {
    load();
    loadRecordings();
  }, [load, loadRecordings]);

  // Polls while a recording is actively RECORDING or PROCESSING (e.g. the
  // meeting is live in another tab right now) so status/download links
  // appear without a manual refresh — stops itself once nothing is pending.
  useEffect(() => {
    const pending = recordings?.some((r) => r.status === 'RECORDING' || r.status === 'PROCESSING');
    if (!pending) return undefined;
    const timer = setInterval(loadRecordings, 5000);
    return () => clearInterval(timer);
  }, [recordings, loadRecordings]);

  async function handleUpdate(payload) {
    setSubmitting(true);
    try {
      await api.patch(`/meetings/${id}?scope=all`, payload);
      toast.success('Meeting updated');
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not update the meeting.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancelMeeting() {
    if (!confirm('Cancel this entire meeting series?')) return;
    await api.delete(`/meetings/${id}?scope=all`);
    toast.success('Meeting cancelled');
    router.push('/dashboard');
  }

  async function handleCancelOccurrence(originalStartsAt) {
    await api.delete(`/meetings/${id}?scope=this&originalStartsAt=${encodeURIComponent(originalStartsAt)}`);
    toast.success('Occurrence cancelled');
    load();
  }

  async function handleCancelFollowing(originalStartsAt) {
    if (!confirm('Cancel this occurrence and every one after it?')) return;
    await api.delete(`/meetings/${id}?scope=following&originalStartsAt=${encodeURIComponent(originalStartsAt)}`);
    toast.success('Series truncated');
    load();
  }

  async function handleSaveFollowing(patch) {
    await api.patch(`/meetings/${id}?scope=following`, {
      originalStartsAt: editingOccurrence.originalStartsAt,
      ...patch,
    });
    toast.success('Updated this and following occurrences');
    load();
  }

  async function handleInvite(e) {
    e.preventDefault();
    if (!inviteEmail.trim()) return;
    try {
      await api.post(`/meetings/${id}/members`, { email: inviteEmail.trim(), role: 'INVITEE' });
      setInviteEmail('');
      toast.success('Invited');
      load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not invite that person.');
    }
  }

  function copyJoinLink() {
    navigator.clipboard.writeText(`${window.location.origin}/j/${meeting.code}`);
    toast.success('Join link copied');
  }

  if (!meeting) {
    return (
      <div className="flex justify-center py-16">
        <FiLoader className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <div>
            {/* CardTitle renders a plain <div> by design, not a heading — wrap
                the real page heading in an actual h1 for correct semantics. */}
            <CardTitle>
              <h1 className="contents">{meeting.title}</h1>
            </CardTitle>
            <p className="mt-1 font-mono text-sm text-muted-foreground">{meeting.code}</p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={copyJoinLink}>
              <FiCopy className="size-4" />
              Copy link
            </Button>
            <Button size="sm" nativeButton={false} render={<a href={`/j/${meeting.code}`} />}>
              <FiVideo className="size-4" />
              Join
            </Button>
          </div>
        </CardHeader>
      </Card>

      {meeting.nextOccurrences?.length ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Upcoming occurrences</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1">
            {meeting.nextOccurrences.map((occ) => (
              <div key={occ.originalStartsAt} className="flex items-center justify-between rounded-md px-2 py-1.5 text-sm hover:bg-accent">
                <span>{formatOccurrence(occ.startsAt)}</span>
                <DropdownMenu>
                  <DropdownMenuTrigger render={<Button variant="ghost" size="sm" />}>Manage</DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onClick={() => handleCancelOccurrence(occ.originalStartsAt)}>
                      Cancel this occurrence
                    </DropdownMenuItem>
                    {meeting.rrule ? (
                      <DropdownMenuItem onClick={() => setEditingOccurrence(occ)}>
                        Edit this and following
                      </DropdownMenuItem>
                    ) : null}
                    {meeting.rrule ? (
                      <DropdownMenuItem onClick={() => handleCancelFollowing(occ.originalStartsAt)}>
                        Cancel this and following
                      </DropdownMenuItem>
                    ) : null}
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            ))}
          </CardContent>
        </Card>
      ) : null}

      {recordings?.length ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Recordings</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1">
            {recordings.map((r) => (
              <div key={r.id} className="flex items-center justify-between rounded-md px-2 py-1.5 text-sm hover:bg-accent">
                <div className="flex items-center gap-2">
                  <FiFilm className="size-4 text-muted-foreground" />
                  <span>{formatOccurrence(r.startedAt)}</span>
                  <RecordingStatusBadge status={r.status} />
                </div>
                <div className="flex items-center gap-3 text-muted-foreground">
                  <span className="text-xs">
                    {formatDuration(r.durationMs)} · {formatSize(r.sizeBytes)}
                  </span>
                  {r.status === 'READY' && r.downloadUrl ? (
                    <Button size="sm" variant="outline" nativeButton={false} render={<a href={r.downloadUrl} download />}>
                      <FiDownload className="size-4" />
                      Download
                    </Button>
                  ) : null}
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Edit series</CardTitle>
        </CardHeader>
        <CardContent>
          <MeetingForm initial={meeting} onSubmit={handleUpdate} submitting={submitting} submitLabel="Save changes" />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">People</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <form onSubmit={handleInvite} className="flex gap-2">
            <Input
              type="email"
              placeholder="Invite by email"
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
            />
            <Button type="submit" variant="secondary">
              <FiUserPlus className="size-4" />
              Invite
            </Button>
          </form>
          {meeting.members?.length ? (
            <ul className="space-y-1 text-sm">
              {meeting.members.map((m) => (
                <li key={m.id} className="flex items-center justify-between rounded-md px-2 py-1">
                  <span>{m.email ?? m.userId}</span>
                  <span className="text-xs text-muted-foreground">{m.role}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">No one invited yet.</p>
          )}
        </CardContent>
      </Card>

      <Button variant="destructive" onClick={handleCancelMeeting}>
        <FiTrash2 className="size-4" />
        Cancel meeting
      </Button>

      <EditFollowingDialog
        open={!!editingOccurrence}
        onOpenChange={(open) => !open && setEditingOccurrence(null)}
        occurrence={editingOccurrence}
        onSave={handleSaveFollowing}
      />
    </div>
  );
}
