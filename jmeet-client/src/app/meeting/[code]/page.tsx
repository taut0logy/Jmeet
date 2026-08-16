'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { FiLoader } from 'react-icons/fi';
import { useMediaDevices } from '@/hooks/use-media-devices';
import { useMeetingStore } from '@/stores/meetingStore';
import { VideoGrid } from '@/components/meeting/video-grid';
import { ControlBar } from '@/components/meeting/control-bar';
import { SidePanel } from '@/components/meeting/side-panel';
import { ReactionsLayer } from '@/components/meeting/reactions-layer';
import { UnmutePrompt } from '@/components/meeting/unmute-prompt';
import { DurationWarningBanner } from '@/components/meeting/duration-warning-banner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

// TODO(livekit): useMeetingConnection() used to own the mediasoup Device +
// socket.io signaling connection and returned this exact action surface.
// Replace with a hook built on `livekit-client`'s Room (connect/disconnect,
// setMicrophoneEnabled, setCameraEnabled, setScreenShareEnabled,
// switchActiveDevice) plus a STOMP client for the durable events (chat,
// waiting room, roles, flags, recording state) per the backend spec's
// real-time contract — see §12. Keep the same action names below so this
// page doesn't need to change shape, only what backs it.
function useMeetingConnectionStub() {
  const stub = (name) => (...args) => {
    console.warn(`[stub] ${name} not implemented`, args);
    return Promise.resolve();
  };
  return {
    toggleMic: stub('toggleMic'),
    toggleCamera: stub('toggleCamera'),
    leave: stub('leave'),
    admitPeer: stub('admitPeer'),
    denyPeer: stub('denyPeer'),
    admitAllWaiting: stub('admitAllWaiting'),
    sendChat: stub('sendChat'),
    sendReaction: stub('sendReaction'),
    toggleHand: stub('toggleHand'),
    startScreenShare: stub('startScreenShare'),
    stopScreenShare: stub('stopScreenShare'),
    muteParticipant: stub('muteParticipant'),
    muteAllParticipants: stub('muteAllParticipants'),
    removeParticipant: stub('removeParticipant'),
    setParticipantRole: stub('setParticipantRole'),
    endMeetingForAll: stub('endMeetingForAll'),
    toggleIncomingVideo: stub('toggleIncomingVideo'),
    startRecording: stub('startRecording'),
    stopRecording: stub('stopRecording'),
    switchCamera: stub('switchCamera'),
    switchMic: stub('switchMic'),
    switchSpeaker: stub('switchSpeaker'),
    setRoomFlag: stub('setRoomFlag'),
  };
}

// The meeting room shell: video grid, control bar, side panel (chat /
// participants / waiting room), reactions, recording indicator, and the
// last-host-leaving confirmation. None of this JSX is mediasoup-specific —
// it's the product's room UI, wired to whatever connection hook backs it.
export default function MeetingPage() {
  const { code } = useParams();
  const router = useRouter();
  const {
    toggleMic,
    toggleCamera,
    leave,
    admitPeer,
    denyPeer,
    admitAllWaiting,
    sendChat,
    sendReaction,
    toggleHand,
    startScreenShare,
    stopScreenShare,
    muteParticipant,
    muteAllParticipants,
    removeParticipant,
    setParticipantRole,
    endMeetingForAll,
    toggleIncomingVideo,
    startRecording,
    stopRecording,
    switchCamera,
    switchMic,
    switchSpeaker,
    setRoomFlag,
  } = useMeetingConnectionStub();
  const [recordingBusy, setRecordingBusy] = useState(false);
  const [showLastHostLeaveConfirm, setShowLastHostLeaveConfirm] = useState(false);
  const { cameras, microphones, speakers } = useMediaDevices();
  const canSelectSpeaker = typeof window !== 'undefined' && 'setSinkId' in (window.HTMLMediaElement?.prototype ?? {});

  const connectionState = useMeetingStore((s) => s.connectionState);
  const errorMessage = useMeetingStore((s) => s.errorMessage);
  const meeting = useMeetingStore((s) => s.meeting);
  const self = useMeetingStore((s) => s.self);
  const peers = useMeetingStore((s) => s.peers);
  const waiting = useMeetingStore((s) => s.waiting);
  const audioOutputId = useMeetingStore((s) => s.audioOutputId);
  const chat = useMeetingStore((s) => s.chat);
  const reactions = useMeetingStore((s) => s.reactions);
  const flags = useMeetingStore((s) => s.flags);
  const sidePanel = useMeetingStore((s) => s.sidePanel);
  const setSidePanel = useMeetingStore((s) => s.setSidePanel);
  const layoutMode = useMeetingStore((s) => s.layoutMode);
  const setLayoutMode = useMeetingStore((s) => s.setLayoutMode);
  const pinnedPeerId = useMeetingStore((s) => s.pinnedPeerId);
  const togglePin = useMeetingStore((s) => s.togglePin);
  const selfConnectionUnstable = useMeetingStore((s) => s.selfConnectionUnstable);
  const unmutePromptVisible = useMeetingStore((s) => s.unmutePromptVisible);
  const dismissUnmutePrompt = useMeetingStore((s) => s.dismissUnmutePrompt);
  const incomingVideoOff = useMeetingStore((s) => s.incomingVideoOff);
  const recording = useMeetingStore((s) => s.recording);
  const durationWarning = useMeetingStore((s) => s.durationWarning);

  // Mirrors the server's finalizeLeave check: no auto-promotion of a random
  // participant, so if self is the room's only remaining host/cohost,
  // leaving ends the meeting for everyone. A peer mid reconnect-grace has
  // connected:false and doesn't count as "still able to run the meeting".
  const isLastHostOrCohost =
    (self?.role === 'HOST' || self?.role === 'COHOST') &&
    !Object.values(peers).some(
      (p) => p.peerId !== self?.peerId && (p.role === 'HOST' || p.role === 'COHOST') && p.connected,
    );

  async function handleLeave() {
    await leave();
    router.push('/dashboard');
  }

  function handleLeaveClick() {
    if (isLastHostOrCohost) {
      setShowLastHostLeaveConfirm(true);
      return;
    }
    handleLeave();
  }

  async function handleToggleScreenShare() {
    if (self?.sharing) await stopScreenShare();
    else await startScreenShare().catch(() => {});
  }

  function handleEndForAll() {
    if (window.confirm('End the meeting for everyone?')) endMeetingForAll();
  }

  async function handleUnmuteFromPrompt() {
    dismissUnmutePrompt();
    if (!self?.micOn) await toggleMic();
  }

  async function handleToggleRecording() {
    setRecordingBusy(true);
    try {
      if (recording?.active) await stopRecording();
      else await startRecording();
    } catch {
      // Ack rejection (e.g. RECORDING_ALREADY_ACTIVE, INSUFFICIENT_DISK_SPACE)
      // is surfaced via the button simply not toggling — server-pushed
      // recording state is the source of truth, not this call's success.
    } finally {
      setRecordingBusy(false);
    }
  }

  if (connectionState === 'idle' || connectionState === 'connecting') {
    return <CenteredMessage icon={<FiLoader className="size-6 animate-spin" />} text="Connecting…" />;
  }

  if (connectionState === 'waiting') {
    return (
      <CenteredMessage
        icon={<FiLoader className="size-6 animate-spin" />}
        text="Waiting for the host to let you in…"
      />
    );
  }

  if (connectionState === 'error') {
    return (
      <CenteredMessage
        text={errorMessage ?? 'Something went wrong.'}
        action={
          <Button variant="secondary" onClick={() => router.push(`/j/${code}`)}>
            Back to lobby
          </Button>
        }
      />
    );
  }

  if (connectionState === 'ended') {
    return (
      <CenteredMessage
        text={errorMessage ?? 'The meeting has ended.'}
        action={
          <Button variant="secondary" onClick={() => router.push('/dashboard')}>
            Back to dashboard
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-neutral-950 text-white">
      <header className="flex items-center justify-between px-4 py-3">
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium text-neutral-300">{meeting?.title}</span>
          {recording?.active ? (
            <span
              className="flex items-center gap-1.5 rounded-full bg-red-500/90 px-2.5 py-1 text-xs font-medium text-white"
              data-testid="recording-indicator"
            >
              <span className="size-2 animate-pulse rounded-full bg-white" />
              Recording
            </span>
          ) : null}
        </div>
        {connectionState === 'reconnecting' ? (
          <span className="text-xs text-amber-400">Reconnecting…</span>
        ) : null}
      </header>

      <div className="flex min-h-0 flex-1">
        <div className="relative flex min-w-0 flex-1 flex-col">
          <VideoGrid
            peers={peers}
            self={self}
            layoutMode={layoutMode}
            pinnedPeerId={pinnedPeerId}
            onTogglePin={togglePin}
          />
          {/* TODO(livekit): remote audio playback used to be a hand-rolled
              RemoteAudioTrack loop over mediasoup consumers. LiveKit ships
              this as a drop-in: <RoomAudioRenderer /> from
              '@livekit/components-react' inside the Room context — delete
              this comment and render that instead. */}
          <ReactionsLayer reactions={reactions} />
          <UnmutePrompt
            visible={unmutePromptVisible}
            onUnmute={handleUnmuteFromPrompt}
            onDismiss={dismissUnmutePrompt}
          />
          <div className="absolute left-1/2 top-4 flex -translate-x-1/2 flex-col items-center gap-2">
            <DurationWarningBanner endsAt={durationWarning?.endsAt} />
            {selfConnectionUnstable ? (
              <div
                className="rounded-md bg-red-500/90 px-3 py-1.5 text-xs text-white shadow"
                data-testid="connection-unstable-banner"
              >
                Your connection is unstable
              </div>
            ) : null}
          </div>
        </div>

        {sidePanel ? (
          <SidePanel
            activeTab={sidePanel}
            onTabChange={setSidePanel}
            chat={chat}
            peers={peers}
            waiting={waiting}
            self={self}
            flags={flags}
            actions={{
              sendChat,
              muteParticipant,
              removeParticipant,
              setParticipantRole,
              muteAllParticipants,
              admitPeer,
              denyPeer,
              admitAllWaiting,
              setRoomFlag,
            }}
          />
        ) : null}
      </div>

      <ControlBar
        micOn={!!self?.micOn}
        camOn={!!self?.camOn}
        sharing={!!self?.sharing}
        handRaised={!!self?.handRaised}
        allowScreenShare={!!flags.allowScreenShare}
        isHost={self?.role === 'HOST'}
        isHostOrCohost={self?.role === 'HOST' || self?.role === 'COHOST'}
        waitingCount={waiting.length}
        sidePanel={sidePanel}
        layoutMode={layoutMode}
        incomingVideoOff={incomingVideoOff}
        recordingActive={!!recording?.active}
        recordingBusy={recordingBusy}
        cameras={cameras}
        microphones={microphones}
        speakers={speakers}
        canSelectSpeaker={canSelectSpeaker}
        audioOutputId={audioOutputId}
        onToggleMic={toggleMic}
        onToggleCamera={toggleCamera}
        onSwitchCamera={switchCamera}
        onSwitchMic={switchMic}
        onSwitchSpeaker={switchSpeaker}
        onToggleScreenShare={handleToggleScreenShare}
        onToggleHand={toggleHand}
        onSendReaction={sendReaction}
        onToggleSidePanel={setSidePanel}
        onSetLayoutMode={setLayoutMode}
        onToggleIncomingVideo={toggleIncomingVideo}
        onToggleRecording={handleToggleRecording}
        onEndForAll={handleEndForAll}
        onLeave={handleLeaveClick}
      />

      <Dialog open={showLastHostLeaveConfirm} onOpenChange={setShowLastHostLeaveConfirm}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>You&apos;re the last host in this meeting</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            There&apos;s no one else who can run it — leaving now will end the meeting for everyone still here.
          </p>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setShowLastHostLeaveConfirm(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                setShowLastHostLeaveConfirm(false);
                handleLeave();
              }}
            >
              Leave and end meeting
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function CenteredMessage({
  icon,
  text,
  action,
}: {
  icon?: React.ReactNode;
  text: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-neutral-950 px-4 text-center text-white">
      {icon}
      <p className="max-w-sm text-sm text-neutral-300">{text}</p>
      {action}
    </div>
  );
}
