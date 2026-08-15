'use client';

import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { FiLoader } from 'react-icons/fi';
import { api, ApiError } from '@/lib/api/client';
import { useMediaDevices } from '@/hooks/use-media-devices';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

export default function DeviceSettingsPage() {
  const [profile, setProfile] = useState(null);
  const [saving, setSaving] = useState(false);
  const [permissionGranted, setPermissionGranted] = useState(false);
  const { cameras, microphones, speakers, refresh } = useMediaDevices();

  useEffect(() => {
    api.get('/users/me').then((res) => setProfile(res.profile));
  }, []);

  async function requestPermission() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true });
      stream.getTracks().forEach((t) => t.stop());
      setPermissionGranted(true);
      refresh();
    } catch {
      toast.error('Camera/microphone permission was denied.');
    }
  }

  async function handleSave() {
    setSaving(true);
    try {
      const { profile: updated } = await api.patch('/users/me', {
        defaultMicMuted: profile.defaultMicMuted,
        defaultCameraOff: profile.defaultCameraOff,
        preferredAudioInputId: profile.preferredAudioInputId || null,
        preferredVideoInputId: profile.preferredVideoInputId || null,
        preferredAudioOutputId: profile.preferredAudioOutputId || null,
      });
      setProfile(updated);
      toast.success('Device preferences saved');
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not save your preferences.');
    } finally {
      setSaving(false);
    }
  }

  if (!profile) {
    return (
      <div className="flex justify-center py-16">
        <FiLoader className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const labelsAvailable = permissionGranted || cameras.some((c) => c.label);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Devices</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {!labelsAvailable ? (
          <div className="rounded-lg border border-dashed p-4 text-sm">
            <p className="mb-2 text-muted-foreground">
              Grant camera and microphone access to see device names and choose defaults.
            </p>
            <Button size="sm" variant="outline" onClick={requestPermission}>
              Grant access
            </Button>
          </div>
        ) : null}

        <div className="space-y-1.5">
          <Label htmlFor="pref-camera">Camera</Label>
          <Select
            value={profile.preferredVideoInputId ?? ''}
            onValueChange={(v) => setProfile({ ...profile, preferredVideoInputId: v })}
            disabled={!labelsAvailable}
          >
            <SelectTrigger id="pref-camera" className="w-full">
              <SelectValue placeholder="System default">
                {(v) => cameras.find((d) => d.deviceId === v)?.label || 'System default'}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {cameras.map((d) => (
                <SelectItem key={d.deviceId} value={d.deviceId}>
                  {d.label || 'Camera'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="pref-microphone">Microphone</Label>
          <Select
            value={profile.preferredAudioInputId ?? ''}
            onValueChange={(v) => setProfile({ ...profile, preferredAudioInputId: v })}
            disabled={!labelsAvailable}
          >
            <SelectTrigger id="pref-microphone" className="w-full">
              <SelectValue placeholder="System default">
                {(v) => microphones.find((d) => d.deviceId === v)?.label || 'System default'}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {microphones.map((d) => (
                <SelectItem key={d.deviceId} value={d.deviceId}>
                  {d.label || 'Microphone'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {speakers.length > 0 ? (
          <div className="space-y-1.5">
            <Label htmlFor="pref-speaker">Speaker</Label>
            <Select
              value={profile.preferredAudioOutputId ?? ''}
              onValueChange={(v) => setProfile({ ...profile, preferredAudioOutputId: v })}
              disabled={!labelsAvailable}
            >
              <SelectTrigger id="pref-speaker" className="w-full">
                <SelectValue placeholder="System default">
                  {(v) => speakers.find((d) => d.deviceId === v)?.label || 'System default'}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                {speakers.map((d) => (
                  <SelectItem key={d.deviceId} value={d.deviceId}>
                    {d.label || 'Speaker'}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ) : null}

        <div className="flex items-center justify-between">
          <Label htmlFor="defaultMicMuted">Join muted by default</Label>
          <Switch
            id="defaultMicMuted"
            checked={profile.defaultMicMuted}
            onCheckedChange={(v) => setProfile({ ...profile, defaultMicMuted: v })}
          />
        </div>
        <div className="flex items-center justify-between">
          <Label htmlFor="defaultCameraOff">Join with camera off by default</Label>
          <Switch
            id="defaultCameraOff"
            checked={profile.defaultCameraOff}
            onCheckedChange={(v) => setProfile({ ...profile, defaultCameraOff: v })}
          />
        </div>

        <Button onClick={handleSave} disabled={saving}>
          {saving ? <FiLoader className="size-4 animate-spin" /> : null}
          Save changes
        </Button>
      </CardContent>
    </Card>
  );
}
