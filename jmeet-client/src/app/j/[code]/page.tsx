'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { FiLoader, FiLock } from 'react-icons/fi';
import { useSession } from '@/lib/auth/client';
import { useLocalPreview } from '@/hooks/use-local-preview';
import { useMediaDevices } from '@/hooks/use-media-devices';
import { loadDevicePrefs, saveDevicePrefs } from '@/lib/media/devicePrefs';
import { VideoPreview } from '@/components/lobby/video-preview';
import { Logo } from '@/components/brand/logo';
import { ThemeToggle } from '@/components/theme-toggle';
import { api, ApiError } from '@/lib/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

export default function LobbyPage() {
  const { code } = useParams();
  const router = useRouter();
  const { data: session, isPending: sessionPending } = useSession();

  const [preview, setPreview] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [displayName, setDisplayName] = useState('');
  const [joining, setJoining] = useState(false);
  const [profile, setProfile] = useState(null);

  // Seeds the toggle state from the signed-in user's saved defaults, defaultMicMuted/defaultCameraOff are
  // "should start disabled" flags, so the enabled state is their inverse.
  // Before the profile loads (or for a guest), both start enabled.
  const localPreview = useLocalPreview({
    initialMicEnabled: profile ? !profile.defaultMicMuted : true,
    initialCameraEnabled: profile ? !profile.defaultCameraOff : true,
  });
  const { cameras, microphones, speakers } = useMediaDevices();
  const canSelectSpeaker = typeof window !== 'undefined' && 'setSinkId' in (window.HTMLMediaElement?.prototype ?? {});

  const [selectedCameraId, setSelectedCameraId] = useState(() => loadDevicePrefs().videoDeviceId ?? null);
  const [selectedMicId, setSelectedMicId] = useState(() => loadDevicePrefs().audioDeviceId ?? null);
  const [selectedSpeakerId, setSelectedSpeakerId] = useState(() => loadDevicePrefs().audioOutputId ?? null);

  useEffect(() => {
    const stream = localPreview.stream;
    if (!stream) return;
    const videoId = stream.getVideoTracks()[0]?.getSettings().deviceId;
    const audioId = stream.getAudioTracks()[0]?.getSettings().deviceId;
    if (videoId) setSelectedCameraId(videoId);
    if (audioId) setSelectedMicId(audioId);
  }, [localPreview.stream]);

  useEffect(() => {
    if (!selectedSpeakerId && speakers.length > 0) setSelectedSpeakerId(speakers[0].deviceId);
  }, [selectedSpeakerId, speakers]);

  useEffect(() => {
    api
      .get(`/meetings/by-code/${code}`)
      .then(setPreview)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setNotFound(true);
      });
  }, [code]);

  useEffect(() => {
    if (session?.user?.name) setDisplayName(session.user.name);
    if (session) {
      api.get('/users/me').then((res) => setProfile(res.profile)).catch(() => {});
    }
  }, [session]);

  async function handleJoin(e) {
    e.preventDefault();
    setJoining(true);
    try {
      const result = await api.post(`/meetings/by-code/${code}/join-token`, {
        displayName: session ? undefined : displayName.trim(),
      });
      sessionStorage.setItem(`meet:join:${code}`, JSON.stringify(result));
      router.push(`/meeting/${code}`);
    } catch (err) {
      if (err instanceof ApiError) {
        toast.error(err.message ?? 'Could not join this meeting.');
      } else {
        toast.error('Could not join this meeting.');
      }
    } finally {
      setJoining(false);
    }
  }

  if (notFound) {
    return (
      <div className="flex min-h-screen items-center justify-center px-4">
        <p className="text-muted-foreground">This meeting doesn&apos;t exist, or the link is wrong.</p>
      </div>
    );
  }

  if (!preview || sessionPending) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FiLoader className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const needsApprovalGuess = preview.waitingRoom !== 'OFF' && (!session || preview.waitingRoom === 'EVERYONE');
  const canJoin = session || displayName.trim().length > 0;

  return (
    <div className="flex min-h-screen flex-col bg-muted/30">
      <header className="flex items-center justify-between px-6 py-4">
        <span className="flex items-center gap-2 text-lg font-semibold tracking-tight">
          <Logo size={28} />
          jmeet
        </span>
        <ThemeToggle />
      </header>
      <main className="flex flex-1 items-center justify-center px-4 pb-16">
        <div className="grid w-full max-w-4xl gap-8 sm:grid-cols-2">
          <VideoPreview
            stream={localPreview.stream}
            error={localPreview.error}
            micEnabled={localPreview.micEnabled}
            cameraEnabled={localPreview.cameraEnabled}
            onToggleMic={localPreview.toggleMic}
            onToggleCamera={localPreview.toggleCamera}
            name={session?.user?.name ?? displayName}
          />

          <div className="flex flex-col justify-center gap-4">
            <div>
              <h1 className="text-xl font-semibold">{preview.title}</h1>
              <p className="text-sm text-muted-foreground">Hosted by {preview.hostDisplayName || 'You'}</p>
            </div>

            {preview.status === 'CANCELLED' || preview.status === 'ENDED' ? (
              <p className="text-sm text-destructive">This meeting is no longer joinable.</p>
            ) : (
              <form onSubmit={handleJoin} className="space-y-3">
                {!session ? (
                  <div className="space-y-1.5">
                    <Label htmlFor="displayName">Your name</Label>
                    <Input
                      id="displayName"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder="Enter your name"
                      required
                    />
                  </div>
                ) : (
                  <p className="text-sm">
                    Joining as <span className="font-medium">{session.user.name}</span>
                  </p>
                )}

                {cameras.length > 0 || microphones.length > 0 || speakers.length > 0 ? (
                  <div className="grid grid-cols-2 gap-2">
                    {cameras.length > 0 ? (
                      <Select
                        value={selectedCameraId}
                        onValueChange={(id) => {
                          setSelectedCameraId(id);
                          saveDevicePrefs({ videoDeviceId: id });
                          // Pass the current mic along too — switchDevice
                          // (useLocalPreview.start) falls back to the OS
                          // default for any deviceId it isn't given, so
                          // omitting this would silently reset the mic
                          // selection back to default on every camera switch.
                          localPreview.switchDevice({ videoDeviceId: id, audioDeviceId: selectedMicId });
                        }}
                      >
                        <SelectTrigger aria-label="Camera" className="w-full">
                          <SelectValue placeholder="Camera">
                            {(v) => cameras.find((d) => d.deviceId === v)?.label || 'Camera'}
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
                    ) : null}
                    {microphones.length > 0 ? (
                      <Select
                        value={selectedMicId}
                        onValueChange={(id) => {
                          setSelectedMicId(id);
                          saveDevicePrefs({ audioDeviceId: id });
                          localPreview.switchDevice({ audioDeviceId: id, videoDeviceId: selectedCameraId });
                        }}
                      >
                        <SelectTrigger aria-label="Microphone" className="w-full">
                          <SelectValue placeholder="Microphone">
                            {(v) => microphones.find((d) => d.deviceId === v)?.label || 'Microphone'}
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
                    ) : null}
                    {speakers.length > 0 && canSelectSpeaker ? (
                      <Select
                        value={selectedSpeakerId}
                        onValueChange={(id) => {
                          setSelectedSpeakerId(id);
                          saveDevicePrefs({ audioOutputId: id });
                        }}
                      >
                        <SelectTrigger aria-label="Speaker" className="w-full">
                          <SelectValue placeholder="Speaker">
                            {(v) => speakers.find((d) => d.deviceId === v)?.label || 'Speaker'}
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
                    ) : null}
                  </div>
                ) : null}

                <Button type="submit" className="w-full" disabled={joining || !canJoin}>
                  {joining ? (
                    <FiLoader className="size-4 animate-spin" />
                  ) : needsApprovalGuess ? (
                    <FiLock className="size-4" />
                  ) : null}
                  {needsApprovalGuess ? 'Ask to join' : 'Join now'}
                </Button>
              </form>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
