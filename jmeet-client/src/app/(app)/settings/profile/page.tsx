'use client';

import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { FiLoader, FiCamera } from 'react-icons/fi';
import { api, ApiError, ensureCsrfToken } from '@/lib/api/client';
import { AvatarEditorDialog } from '@/components/profile/avatar-editor-dialog';
import { ChangePasswordCard } from '@/components/profile/change-password-card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const TIMEZONES = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone').sort() : ['UTC'];

function initialsFor(name) {
  if (!name) return '?';
  return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

export default function ProfileSettingsPage() {
  const [profile, setProfile] = useState(null);
  const [session, setSession] = useState(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [pendingFile, setPendingFile] = useState(null);
  const fileInputRef = useRef(null);

  useEffect(() => {
    api.get('/users/me').then((res) => {
      setProfile(res.profile);
      setSession(res.user);
    });
  }, []);

  async function handleSave(e) {
    e.preventDefault();
    setSaving(true);
    try {
      const { profile: updated } = await api.patch('/users/me', {
        displayName: profile.displayName,
        timezone: profile.timezone,
      });
      setProfile(updated);
      toast.success('Profile saved');
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not save your profile.');
    } finally {
      setSaving(false);
    }
  }

  function handleFilePicked(e) {
    const file = e.target.files?.[0];
    if (file) setPendingFile(file);
    e.target.value = '';
  }

  async function handleSaveCrop(blob) {
    setUploading(true);
    try {
      const token = await ensureCsrfToken();
      const formData = new FormData();
      formData.append('file', blob, 'avatar.png');
      const res = await fetch('/api/users/me/avatar', {
        method: 'POST',
        headers: token ? { 'X-XSRF-TOKEN': token } : undefined,
        body: formData,
      });
      if (!res.ok) throw new Error('Upload failed');
      const { profile: updated } = await res.json();
      setProfile(updated);
      toast.success('Avatar updated');
    } catch {
      toast.error('Could not upload that image.');
    } finally {
      setUploading(false);
    }
  }

  if (!profile) {
    return (
      <div className="flex justify-center py-16">
        <FiLoader className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSave} className="space-y-5">
            <div className="flex items-center gap-4">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                className="hidden"
                onChange={handleFilePicked}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                aria-label="Change profile photo"
                className="group relative size-16 shrink-0 rounded-full outline-offset-2 focus-visible:outline-2 focus-visible:outline-ring disabled:pointer-events-none"
              >
                <Avatar className="size-16">
                  <AvatarImage src={profile.avatarUrl ?? undefined} alt={profile.displayName} />
                  <AvatarFallback className="text-lg">{initialsFor(profile.displayName)}</AvatarFallback>
                </Avatar>
                <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/0 text-transparent transition-colors group-hover:bg-black/45 group-hover:text-white">
                  {uploading ? <FiLoader className="size-5 animate-spin" /> : <FiCamera className="size-5" />}
                </span>
              </button>
              <div>
                <p className="text-sm font-medium">{profile.displayName}</p>
                <p className="text-xs text-muted-foreground">Click your photo to change it. PNG, JPEG, or WebP, up to 2MB.</p>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="displayName">Display name</Label>
              <Input
                id="displayName"
                value={profile.displayName}
                onChange={(e) => setProfile({ ...profile, displayName: e.target.value })}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="profile-timezone">Timezone</Label>
              <Select value={profile.timezone} onValueChange={(tz) => setProfile({ ...profile, timezone: tz })}>
                <SelectTrigger id="profile-timezone" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="max-h-64">
                  {TIMEZONES.map((tz) => (
                    <SelectItem key={tz} value={tz}>
                      {tz}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Button type="submit" disabled={saving}>
              {saving ? <FiLoader className="size-4 animate-spin" /> : null}
              Save changes
            </Button>
          </form>
        </CardContent>
      </Card>

      {session?.hasPassword ? <ChangePasswordCard /> : null}

      <AvatarEditorDialog file={pendingFile} onOpenChange={(open) => !open && setPendingFile(null)} onSave={handleSaveCrop} />
    </div>
  );
}
