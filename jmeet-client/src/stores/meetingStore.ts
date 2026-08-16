import { create } from 'zustand';

// Tiles subscribe through selectors keyed by peerId so one participant's
// update doesn't re-render every other tile. Pure state only — connection
// side effects live in the connection orchestrator, not here.
//
// TODO(livekit): producers/consumers/localStream/screenStream/
// dominantSpeakerId/speakingPeerIds used to live here, hand-patched from
// mediasoup signaling events. LiveKit's own `Room` object is already
// reactive and owns all of that (live tracks, active speaker, per-
// participant speaking state) — read it through `@livekit/components-react`
// hooks (useTracks, useParticipants, useLocalParticipant) instead of
// mirroring it into this store. Re-add only app-level state LiveKit doesn't
// model itself.

export type ConnectionState = 'idle' | 'connecting' | 'waiting' | 'connected' | 'reconnecting' | 'ended' | 'error';

// Role/hand-raise are app-level metadata LiveKit doesn't know about — merge
// this with LiveKit's live Participant objects for the full picture rather
// than trying to mirror media state into it too.
export type Peer = {
  peerId: string;
  displayName: string;
  role: 'HOST' | 'COHOST' | 'PARTICIPANT';
  handRaised: boolean;
  micOn: boolean;
  camOn: boolean;
  connected: boolean;
  [key: string]: unknown;
};

export type WaitingEntry = { peerId: string; displayName: string; [key: string]: unknown };
export type ChatMessage = { id: string; peerId: string; displayName: string; text: string; at: string };
export type Reaction = { id: string; peerId: string; emoji: string; at: string };
export type RoomFlags = {
  locked?: boolean;
  waitingRoom?: string;
  allowChat?: boolean;
  allowScreenShare?: boolean;
  [key: string]: unknown;
};
export type MeetingSummary = { title: string; [key: string]: unknown };
export type Recording = { active: boolean; startedAt: string | null; startedBy: string | null };
export type DurationWarning = { endsAt: string } | null;
export type Snapshot = {
  rev: number;
  meeting: MeetingSummary | null;
  self: Peer;
  flags: RoomFlags;
  peers: Peer[];
  waiting: WaitingEntry[];
  chat?: ChatMessage[];
  recording?: Recording;
  durationWarning?: DurationWarning;
};

const initialState = {
  connectionState: 'idle' as ConnectionState,
  errorMessage: null as string | null,
  rev: 0,
  meeting: null as MeetingSummary | null,
  self: null as Peer | null,
  flags: {} as RoomFlags,
  peers: {} as Record<string, Peer>,
  waiting: [] as WaitingEntry[],
  audioOutputId: null as string | null, // preferred speaker device — applied per-<audio>/<video> element

  chat: [] as ChatMessage[],
  reactions: [] as Reaction[], // transient: self-clearing, see addReaction
  sidePanel: null as 'chat' | 'participants' | 'waiting' | null,
  layoutMode: 'tiled' as 'tiled' | 'spotlight' | 'sidebar',
  pinnedPeerId: null as string | null,

  selfConnectionUnstable: false, // "your connection is unstable" banner
  unmutePromptVisible: false, // "you're muted, are you talking?"
  incomingVideoOff: false, // manual "turn off incoming video" override

  // Mirrors server-side recording state — seeded from the join snapshot,
  // updated by a recording-state event.
  recording: { active: false, startedAt: null, startedBy: null } as Recording,

  // Scheduled-meeting duration enforcement — null until the server's
  // duration warning arrives. `endsAt` is an absolute ISO timestamp (not a
  // countdown number) so the overlay's live countdown is immune to
  // when-it-happened-to-render/network-latency drift.
  durationWarning: null as DurationWarning,
};

type State = typeof initialState;

type Actions = {
  reset: () => void;
  setConnectionState: (connectionState: ConnectionState, errorMessage?: string | null) => void;
  applySnapshot: (snapshot: Snapshot) => void;
  /** Returns false if `delta.rev` isn't exactly next — caller should resync. */
  applyDelta: (delta: { rev: number }, apply: (state: State, delta: any) => Partial<State> | void) => boolean;
  upsertPeer: (peer: Peer) => void;
  removePeer: (peerId: string) => void;
  patchPeer: (peerId: string, patch: Partial<Peer>) => void;
  setWaiting: (waiting: WaitingEntry[]) => void;
  setFlags: (flags: RoomFlags) => void;
  setSelf: (patch: Partial<Peer>) => void;
  setAudioOutputId: (audioOutputId: string | null) => void;
  addChatMessage: (message: ChatMessage) => void;
  /** Reactions self-clear after a few seconds — the UI is a transient animation, not a log. */
  addReaction: (reaction: Reaction) => void;
  setSidePanel: (sidePanel: State['sidePanel']) => void;
  setLayoutMode: (layoutMode: State['layoutMode']) => void;
  setPinnedPeerId: (pinnedPeerId: string | null) => void;
  togglePin: (peerId: string) => void;
  setSelfConnectionUnstable: (selfConnectionUnstable: boolean) => void;
  showUnmutePrompt: () => void;
  dismissUnmutePrompt: () => void;
  setIncomingVideoOff: (incomingVideoOff: boolean) => void;
  setDurationWarning: (durationWarning: DurationWarning) => void;
};

export const useMeetingStore = create<State & Actions>()((set, get) => ({
  ...initialState,

  reset: () => set(initialState),

  setConnectionState: (connectionState, errorMessage = null) => set({ connectionState, errorMessage }),

  applySnapshot: (snapshot) =>
    set({
      rev: snapshot.rev,
      meeting: snapshot.meeting,
      self: snapshot.self,
      flags: snapshot.flags,
      peers: Object.fromEntries(snapshot.peers.map((p) => [p.peerId, p])),
      waiting: snapshot.waiting,
      chat: snapshot.chat ?? [],
      recording: snapshot.recording ?? initialState.recording,
      durationWarning: snapshot.durationWarning ?? null,
    }),

  applyDelta: (delta, apply) => {
    const { rev } = get();
    if (delta.rev !== rev + 1) return false;
    set((state) => {
      const patch = apply(state, delta) ?? {};
      return { ...patch, rev: delta.rev };
    });
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
  setFlags: (flags) => set({ flags }),
  setSelf: (patch) => set((state) => ({ self: state.self ? { ...state.self, ...patch } : state.self })),
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
  setPinnedPeerId: (pinnedPeerId) => set({ pinnedPeerId }),
  togglePin: (peerId) => set((state) => ({ pinnedPeerId: state.pinnedPeerId === peerId ? null : peerId })),

  setSelfConnectionUnstable: (selfConnectionUnstable) => set({ selfConnectionUnstable }),
  showUnmutePrompt: () => set({ unmutePromptVisible: true }),
  dismissUnmutePrompt: () => set({ unmutePromptVisible: false }),
  setIncomingVideoOff: (incomingVideoOff) => set({ incomingVideoOff }),
  setDurationWarning: (durationWarning) => set({ durationWarning }),
}));
