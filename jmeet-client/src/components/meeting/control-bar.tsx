'use client';

import { useState } from 'react';
import {
  FiMic,
  FiMicOff,
  FiVideo,
  FiVideoOff,
  FiPhoneOff,
  FiMessageSquare,
  FiUsers,
  FiSmile,
  FiMonitor,
  FiSquare,
  FiEye,
  FiEyeOff,
  FiCircle,
  FiChevronUp,
} from 'react-icons/fi';
import { FaHandPaper } from 'react-icons/fa';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from '@/components/ui/dropdown-menu';
import { loadDevicePrefs } from '@/lib/media/devicePrefs';
import { LayoutSwitcher } from './layout-switcher';

const REACTIONS = [
  { emoji: 'thumbsup', glyph: '👍' },
  { emoji: 'clap', glyph: '👏' },
  { emoji: 'heart', glyph: '❤️' },
  { emoji: 'laugh', glyph: '😂' },
  { emoji: 'wow', glyph: '😮' },
  { emoji: 'sad', glyph: '😢' },
];

type MediaDeviceOption = { deviceId: string; label: string };

type DeviceSplitButtonProps = {
  active: boolean;
  toggleLabel: string;
  menuLabel: string;
  activeIcon: React.ReactNode;
  inactiveIcon: React.ReactNode;
  onToggle: () => void;
  devices: MediaDeviceOption[];
  selectedDeviceId?: string | null;
  onSelectDevice: (deviceId: string) => void;
  extraSection?: React.ReactNode;
};

// Zoom/Meet-style split button: the main half keeps the existing on/off
// toggle behavior untouched; the chevron half opens a device picker.
// TODO(livekit): device switching should call Room.switchActiveDevice()
// under the hood (via the switchCamera/switchMic props below) so it never
// interrupts the call — same intent as mediasoup's Producer.replaceTrack
// had, different API.
function DeviceSplitButton({
  active,
  toggleLabel,
  menuLabel,
  activeIcon,
  inactiveIcon,
  onToggle,
  devices,
  selectedDeviceId,
  onSelectDevice,
  extraSection,
}: DeviceSplitButtonProps) {
  const variant = active ? 'secondary' : 'destructive';
  const hasDevices = devices.length > 0;
  return (
    <div className="flex items-stretch overflow-hidden rounded-lg">
      <Button
        type="button"
        variant={variant}
        size="icon"
        aria-label={toggleLabel}
        aria-pressed={!active}
        onClick={onToggle}
        className="rounded-r-none"
      >
        {active ? activeIcon : inactiveIcon}
      </Button>
      {hasDevices || extraSection ? (
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={menuLabel}
            render={<Button type="button" variant={variant} size="icon" className="w-4 rounded-l-none border-l border-black/20 px-0" />}
          >
            <FiChevronUp className="size-3" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" side="top" className={"min-w-76"}>
            {hasDevices ? (
              <DropdownMenuRadioGroup value={selectedDeviceId ?? ''} onValueChange={onSelectDevice}>
                <DropdownMenuLabel>{menuLabel}</DropdownMenuLabel>
                {devices.map((d, i) => (
                  <DropdownMenuRadioItem key={d.deviceId} value={d.deviceId}>
                    {d.label || `${menuLabel} ${i + 1}`}
                  </DropdownMenuRadioItem>
                ))}
              </DropdownMenuRadioGroup>
            ) : null}
            {hasDevices && extraSection ? <DropdownMenuSeparator /> : null}
            {extraSection}
          </DropdownMenuContent>
        </DropdownMenu>
      ) : null}
    </div>
  );
}

// screen share, raise hand, reactions, chat, participants, layout switcher, and host-only end-for-all.
export function ControlBar({
  micOn,
  camOn,
  sharing,
  handRaised,
  allowScreenShare,
  isHost,
  isHostOrCohost,
  waitingCount = 0,
  sidePanel,
  layoutMode,
  incomingVideoOff,
  recordingActive,
  recordingBusy,
  cameras = [],
  microphones = [],
  speakers = [],
  canSelectSpeaker = false,
  audioOutputId = null,
  onToggleMic,
  onToggleCamera,
  onSwitchCamera,
  onSwitchMic,
  onSwitchSpeaker,
  onToggleScreenShare,
  onToggleHand,
  onSendReaction,
  onToggleSidePanel,
  onSetLayoutMode,
  onToggleIncomingVideo,
  onToggleRecording,
  onEndForAll,
  onLeave,
}) {
  // Only for highlighting the currently-selected item in each device menu —
  // the actual switch is applied immediately (see switchCamera/switchMic),
  // this just needs to survive re-renders without living in the global
  // store (nothing else in the app cares which mic/camera is selected).
  const [selectedCameraId, setSelectedCameraId] = useState(() => loadDevicePrefs().videoDeviceId ?? null);
  const [selectedMicId, setSelectedMicId] = useState(() => loadDevicePrefs().audioDeviceId ?? null);

  function handleSelectCamera(id) {
    setSelectedCameraId(id);
    onSwitchCamera?.(id);
  }

  function handleSelectMic(id) {
    setSelectedMicId(id);
    onSwitchMic?.(id);
  }

  return (
    <div className="flex flex-wrap items-center justify-center gap-2 border-t border-white/10 bg-neutral-950 px-4 py-3">
      <DeviceSplitButton
        active={micOn}
        toggleLabel={micOn ? 'Mute microphone' : 'Unmute microphone'}
        menuLabel="Microphone"
        activeIcon={<FiMic />}
        inactiveIcon={<FiMicOff />}
        onToggle={onToggleMic}
        devices={microphones}
        selectedDeviceId={selectedMicId}
        onSelectDevice={handleSelectMic}
        extraSection={
          canSelectSpeaker && speakers.length > 0 ? (
            <DropdownMenuRadioGroup value={audioOutputId ?? ''} onValueChange={onSwitchSpeaker}>
              <DropdownMenuLabel>Speaker</DropdownMenuLabel>
              {speakers.map((d, i) => (
                <DropdownMenuRadioItem key={d.deviceId} value={d.deviceId}>
                  {d.label || `Speaker ${i + 1}`}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          ) : null
        }
      />
      <DeviceSplitButton
        active={camOn}
        toggleLabel={camOn ? 'Turn off camera' : 'Turn on camera'}
        menuLabel="Camera"
        activeIcon={<FiVideo />}
        inactiveIcon={<FiVideoOff />}
        onToggle={onToggleCamera}
        devices={cameras}
        selectedDeviceId={selectedCameraId}
        onSelectDevice={handleSelectCamera}
      />

      {allowScreenShare ? (
        <Button
          type="button"
          variant={sharing ? 'default' : 'secondary'}
          size="icon"
          aria-label={sharing ? 'Stop presenting' : 'Present screen'}
          aria-pressed={sharing}
          onClick={onToggleScreenShare}
        >
          {sharing ? <FiSquare /> : <FiMonitor />}
        </Button>
      ) : null}

      <Button
        type="button"
        variant={handRaised ? 'default' : 'secondary'}
        size="icon"
        aria-label={handRaised ? 'Lower hand' : 'Raise hand'}
        aria-pressed={handRaised}
        onClick={onToggleHand}
      >
        <FaHandPaper />
      </Button>

      <DropdownMenu>
        <DropdownMenuTrigger aria-label="Send a reaction" render={<Button type="button" variant="secondary" size="icon" />}>
          <FiSmile />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="center">
          <div className="flex gap-1 p-1">
            {REACTIONS.map((r) => (
              <button
                key={r.emoji}
                type="button"
                aria-label={`React with ${r.emoji}`}
                className="rounded-md p-1.5 text-xl hover:bg-accent"
                onClick={() => onSendReaction(r.emoji)}
              >
                {r.glyph}
              </button>
            ))}
          </div>
        </DropdownMenuContent>
      </DropdownMenu>

      <Button
        type="button"
        variant={sidePanel === 'chat' ? 'default' : 'secondary'}
        size="icon"
        aria-label="Chat"
        aria-pressed={sidePanel === 'chat'}
        onClick={() => onToggleSidePanel('chat')}
      >
        <FiMessageSquare />
      </Button>
      <div className="relative">
        <Button
          type="button"
          variant={sidePanel === 'participants' || sidePanel === 'waiting' ? 'default' : 'secondary'}
          size="icon"
          aria-label="Participants"
          aria-pressed={sidePanel === 'participants' || sidePanel === 'waiting'}
          onClick={() => onToggleSidePanel(isHostOrCohost && waitingCount > 0 ? 'waiting' : 'participants')}
        >
          <FiUsers />
        </Button>
        {isHostOrCohost && waitingCount > 0 ? (
          <span
            className="absolute -right-1 -top-1 flex size-4 items-center justify-center rounded-full bg-amber-500 text-[10px] font-semibold text-black"
            data-testid="waiting-badge"
          >
            {waitingCount}
          </span>
        ) : null}
      </div>

      <LayoutSwitcher layoutMode={layoutMode} onChange={onSetLayoutMode} />

      <Button
        type="button"
        variant={incomingVideoOff ? 'default' : 'secondary'}
        size="icon"
        aria-label={incomingVideoOff ? 'Turn on incoming video' : 'Turn off incoming video'}
        aria-pressed={incomingVideoOff}
        onClick={onToggleIncomingVideo}
      >
        {incomingVideoOff ? <FiEyeOff /> : <FiEye />}
      </Button>

      {isHostOrCohost ? (
        <Button
          type="button"
          variant={recordingActive ? 'destructive' : 'secondary'}
          aria-label={recordingActive ? 'Stop recording' : 'Start recording'}
          aria-pressed={recordingActive}
          disabled={recordingBusy}
          onClick={onToggleRecording}
          data-testid="record-button"
        >
          <FiCircle className={recordingActive ? 'fill-current' : ''} />
          {recordingActive ? 'Stop recording' : 'Record'}
        </Button>
      ) : null}

      {isHost ? (
        <Button type="button" variant="destructive" aria-label="End meeting for everyone" onClick={onEndForAll}>
          End for all
        </Button>
      ) : null}
      <Button type="button" variant="destructive" size="icon" aria-label="Leave meeting" onClick={onLeave}>
        <FiPhoneOff />
      </Button>
    </div>
  );
}
