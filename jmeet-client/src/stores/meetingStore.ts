import { create } from 'zustand';

export type ConnectionState = 'idle' | 'connecting' | 'waiting' | 'connected' | 'reconnecting' | 'ended' | 'error';

export type ParticipantRole = 'HOST' | 'COHOST' | 'PARTICIPANT';

export type PeerMeta = { peerId: string; displayName: string; role: ParticipantRole; handRaised: boolean };

export type WaitingEntry = { peerId: string; displayName: string };
export type ChatMessage = { id: string; peerId: string; displayName: string; body: string; createdAt: string };
export type Reaction = { id: string; peerId: string; emoji: string };
export type RoomFlags = { locked: boolean; waitingRoom: 'OFF' | 'GUESTS_ONLY' | 'EVERYONE'; screenShareEnabled: boolean };
export type Recording = { active: boolean; startedAt: string | null; startedBy: string | null };
export type DurationWarning = { endsAt: string } | null;

export type RoomSnapshot = {
  sessionId: string;
  meetingId: string;
  title: string;
  locked: boolean;
  waitingRoom: 'OFF' | 'GUESTS_ONLY' | 'EVERYONE';
  muteOnEntry: boolean;
  cameraOffOnEntry: boolean;
  screenShareEnabled: boolean;
  screenShareMaxConcurrent: number;
  participants: PeerMeta[];
  recentChat: { peerId: string; displayName: string; body: string; createdAt: string }[];
  pendingAdmissions: WaitingEntry[];
  recordingActive: boolean;
  recordingStartedAt: string | null;
  recordingStartedBy: string | null;
  rev: number;
};

const initialState = {
  connectionState: 'idle' as ConnectionState,
  errorMessage: null as string | null,
  rev: 0,

  sessionId: null as string | null,
  selfPeerId: null as string | null,
  meeting: null as { title: string } | null,
  flags: { locked: false, waitingRoom: 'GUESTS_ONLY', screenShareEnabled: true } as RoomFlags,
  peers: {} as Record<string, PeerMeta>,
  waiting: [] as WaitingEntry[],
  audioOutputId: null as string | null,

  chat: [] as ChatMessage[],
  reactions: [] as Reaction[], // transient: self-clearing, see addReaction
  sidePanel: null as 'chat' | 'participants' | 'waiting' | 'settings' | null,
  layoutMode: 'tiled' as 'tiled' | 'spotlight' | 'sidebar',
  pinnedPeerId: null as string | null,

  selfConnectionUnstable: false,
  unmutePromptVisible: false,
  incomingVideoOff: false,

  recording: { active: false, startedAt: null, startedBy: null } as Recording,
  durationWarning: null as DurationWarning,
};

export type State = typeof initialState;

type Actions = {
  reset: () => void;
  setConnectionState: (connectionState: ConnectionState, errorMessage?: string | null) => void;
  applySnapshot: (snapshot: RoomSnapshot, selfPeerId: string) => void;
  /** Returns false if `rev` isn't exactly next — caller should resync via GET /rooms/{id}/sync. */
  applyDelta: (rev: number, apply: (state: State) => Partial<State> | void) => boolean;
  upsertPeer: (peer: PeerMeta) => void;
  removePeer: (peerId: string) => void;
  patchPeer: (peerId: string, patch: Partial<PeerMeta>) => void;
  setWaiting: (waiting: WaitingEntry[]) => void;
  addWaiting: (entry: WaitingEntry) => void;
  removeWaiting: (peerId: string) => void;
  setFlags: (flags: Partial<RoomFlags>) => void;
  setAudioOutputId: (audioOutputId: string | null) => void;
  addChatMessage: (message: ChatMessage) => void;
  addReaction: (reaction: Reaction) => void;
  setSidePanel: (sidePanel: State['sidePanel']) => void;
  setLayoutMode: (layoutMode: State['layoutMode']) => void;
  togglePin: (peerId: string) => void;
  setSelfConnectionUnstable: (selfConnectionUnstable: boolean) => void;
  showUnmutePrompt: () => void;
  dismissUnmutePrompt: () => void;
  setIncomingVideoOff: (incomingVideoOff: boolean) => void;
  setDurationWarning: (durationWarning: DurationWarning) => void;
  setRecording: (recording: Recording) => void;
};

export const useMeetingStore = create<State & Actions>()((set, get) => ({
  ...initialState,

  reset: () => set(initialState),

  setConnectionState: (connectionState, errorMessage = null) => set({ connectionState, errorMessage }),

  applySnapshot: (snapshot, selfPeerId) =>
    set({
      rev: snapshot.rev,
      sessionId: snapshot.sessionId,
      selfPeerId,
      meeting: { title: snapshot.title },
      flags: { locked: snapshot.locked, waitingRoom: snapshot.waitingRoom, screenShareEnabled: snapshot.screenShareEnabled },
      peers: Object.fromEntries(snapshot.participants.map((p) => [p.peerId, p])),
      waiting: snapshot.pendingAdmissions,
      chat: snapshot.recentChat.map((m) => ({ ...m, id: `${m.peerId}:${m.createdAt}` })),
      recording: { active: snapshot.recordingActive, startedAt: snapshot.recordingStartedAt, startedBy: snapshot.recordingStartedBy },
    }),

  applyDelta: (rev, apply) => {
    const { rev: localRev } = get();
    if (rev !== localRev + 1) return false;
    set((state) => ({ ...(apply(state) ?? {}), rev }));
    return true;
  },

  upsertPeer: (peer) => set((state) => ({ peers: { ...state.peers, [peer.peerId]: peer } })),
  removePeer: (peerId) =>
    set((state) => {
      const peers = { ...state.peers };
      delete peers[peerId];
      return { peers };
    }),
  patchPeer: (peerId, patch) =>
    set((state) => {
      if (!state.peers[peerId]) return {};
      return { peers: { ...state.peers, [peerId]: { ...state.peers[peerId], ...patch } } };
    }),

  setWaiting: (waiting) => set({ waiting }),
  addWaiting: (entry) => set((state) => ({ waiting: [...state.waiting, entry] })),
  removeWaiting: (peerId) => set((state) => ({ waiting: state.waiting.filter((w) => w.peerId !== peerId) })),
  setFlags: (flags) => set((state) => ({ flags: { ...state.flags, ...flags } })),
  setAudioOutputId: (audioOutputId) => set({ audioOutputId }),

  addChatMessage: (message) => set((state) => ({ chat: [...state.chat, message] })),

  addReaction: (reaction) => {
    set((state) => ({ reactions: [...state.reactions, reaction] }));
    setTimeout(() => {
      set((state) => ({ reactions: state.reactions.filter((r) => r.id !== reaction.id) }));
    }, 4000);
  },

  setSidePanel: (sidePanel) => set((state) => ({ sidePanel: state.sidePanel === sidePanel ? null : sidePanel })),
  setLayoutMode: (layoutMode) => set({ layoutMode }),
  togglePin: (peerId) => set((state) => ({ pinnedPeerId: state.pinnedPeerId === peerId ? null : peerId })),

  setSelfConnectionUnstable: (selfConnectionUnstable) => set({ selfConnectionUnstable }),
  showUnmutePrompt: () => set({ unmutePromptVisible: true }),
  dismissUnmutePrompt: () => set({ unmutePromptVisible: false }),
  setIncomingVideoOff: (incomingVideoOff) => set({ incomingVideoOff }),
  setDurationWarning: (durationWarning) => set({ durationWarning }),
  setRecording: (recording) => set({ recording }),
}));
