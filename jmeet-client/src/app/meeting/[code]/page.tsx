'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { FiLoader } from 'react-icons/fi';
import { RoomContext, RoomAudioRenderer, useLocalParticipant } from '@livekit/components-react';
import { useMediaDevices } from '@/hooks/use-media-devices';
import { useRoomConnection } from '@/hooks/use-room-connection';
import { useMeetingStore } from '@/stores/meetingStore';
import { VideoGrid } from '@/components/meeting/video-grid';
import { ControlBar } from '@/components/meeting/control-bar';
import { SidePanel } from '@/components/meeting/side-panel';
import { ReactionsLayer } from '@/components/meeting/reactions-layer';
import { UnmutePrompt } from '@/components/meeting/unmute-prompt';
import { DurationWarningBanner } from '@/components/meeting/duration-warning-banner';
import { Button } from '@/components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

export default function MeetingPage() {
  const { code } = useParams<{ code: string }>();
  const connection = useRoomConnection(code);

  return (
    <RoomContext.Provider value={connection.room}>
      <MeetingRoom code={code} connection={connection} />
    </RoomContext.Provider>
  );
}

function MeetingRoom({ code, connection }: { code: string; connection: ReturnType<typeof useRoomConnection> }) {
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
  } = connection;
  const [recordingBusy, setRecordingBusy] = useState(false);
  const [showLastHostLeaveConfirm, setShowLastHostLeaveConfirm] = useState(false);
  const [showEndForAllConfirm, setShowEndForAllConfirm] = useState(false);
  const { cameras, microphones, speakers } = useMediaDevices();
  const canSelectSpeaker = typeof window !== 'undefined' && 'setSinkId' in (window.HTMLMediaElement?.prototype ?? {});
  const { isMicrophoneEnabled, isCameraEnabled, isScreenShareEnabled } = useLocalParticipant();

  const connectionState = useMeetingStore((s) => s.connectionState);
  const errorMessage = useMeetingStore((s) => s.errorMessage);
  const meeting = useMeetingStore((s) => s.meeting);
  const selfPeerId = useMeetingStore((s) => s.selfPeerId);
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

  const self = selfPeerId ? peers[selfPeerId] : null;

  const isLastHostOrCohost =
    (self?.role === 'HOST' || self?.role === 'COHOST') &&
    !Object.values(peers).some((p) => p.peerId !== selfPeerId && (p.role === 'HOST' || p.role === 'COHOST'));

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
    if (isScreenShareEnabled) await stopScreenShare();
    else await startScreenShare().catch(() => {});
  }

  function handleEndForAll() {
    setShowEndForAllConfirm(true);
  }

  async function handleUnmuteFromPrompt() {
    dismissUnmutePrompt();
    if (!isMicrophoneEnabled) await toggleMic();
  }

  async function handleToggleRecording() {
    setRecordingBusy(true);
    try {
      if (recording?.active) await stopRecording();
      else await startRecording();
    } catch {
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
      <RoomAudioRenderer />
      <header className="flex items-center justify-between gap-3 px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <span className="truncate text-sm font-medium text-neutral-300">{meeting?.title}</span>
          {recording?.active ? (
            <span
              className="flex shrink-0 items-center gap-1.5 rounded-full bg-red-500/90 px-2.5 py-1 text-xs font-medium text-white"
              data-testid="recording-indicator"
            >
              <span className="size-2 animate-pulse rounded-full bg-white" />
              <span className="hidden sm:inline">Recording</span>
            </span>
          ) : null}
        </div>
        {connectionState === 'reconnecting' ? (
          <span className="shrink-0 text-xs text-amber-400">Reconnecting…</span>
        ) : null}
      </header>

      <div className="flex min-h-0 flex-1">
        <div className="relative flex min-w-0 flex-1 flex-col">
          <VideoGrid
            peers={peers}
            selfPeerId={selfPeerId}
            layoutMode={layoutMode}
            pinnedPeerId={pinnedPeerId}
            incomingVideoOff={incomingVideoOff}
            onTogglePin={togglePin}
          />
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
            self={self ? { ...self, peerId: selfPeerId } : null}
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
        micOn={isMicrophoneEnabled}
        camOn={isCameraEnabled}
        sharing={isScreenShareEnabled}
        handRaised={!!self?.handRaised}
        allowScreenShare={!!flags.screenShareEnabled}
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

      <AlertDialog open={showLastHostLeaveConfirm} onOpenChange={setShowLastHostLeaveConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>You&apos;re the last host in this meeting</AlertDialogTitle>
            <AlertDialogDescription>
              There&apos;s no one else who can run it — leaving now will end the meeting for everyone still here.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={handleLeave}>
              Leave and end meeting
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={showEndForAllConfirm} onOpenChange={setShowEndForAllConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>End the meeting for everyone?</AlertDialogTitle>
            <AlertDialogDescription>Everyone still in the call will be disconnected.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={endMeetingForAll}>
              End for everyone
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
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
