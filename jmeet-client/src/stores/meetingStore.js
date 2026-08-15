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

const initialState = {
  connectionState: 'idle', // idle | connecting | waiting | connected | reconnecting | ended | error
  errorMessage: null,
  rev: 0,
  meeting: null,
  self: null, // { peerId, role, micOn, camOn }
  flags: {},
  // Role/hand-raise are app-level metadata LiveKit doesn't know about —
  // merge this with LiveKit's live Participant objects for the full picture
  // rather than trying to mirror media state into it too.
  peers: {}, // peerId -> { peerId, displayName, role, handRaised, connected, ... }
  waiting: [],
  audioOutputId: null, // preferred speaker device — applied per-<audio>/<video> element

  chat: [], // { id, peerId, displayName, text, at }
  reactions: [], // transient: { id, peerId, emoji, at } — self-clearing, see addReaction
  sidePanel: null, // null | 'chat' | 'participants' | 'waiting'
  layoutMode: 'tiled', // 'tiled' | 'spotlight' | 'sidebar'
  pinnedPeerId: null,

  selfConnectionUnstable: false, // "your connection is unstable" banner
  unmutePromptVisible: false, // "you're muted, are you talking?"
  incomingVideoOff: false, // manual "turn off incoming video" override

  // Mirrors server-side recording state — seeded from the join snapshot,
  // updated by a recording-state event.
  recording: { active: false, startedAt: null, startedBy: null },

  // Scheduled-meeting duration enforcement — null until the server's
  // duration warning arrives. `endsAt` is an absolute ISO timestamp (not a
  // countdown number) so the overlay's live countdown is immune to
  // when-it-happened-to-render/network-latency drift.
  durationWarning: null, // { endsAt: string } | null
};

export const useMeetingStore = create((set, get) => ({
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

  /** Returns false if `delta.rev` isn't exactly next — caller should resync. */
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
  setSelf: (patch) => set((state) => ({ self: { ...state.self, ...patch } })),
  setAudioOutputId: (audioOutputId) => set({ audioOutputId }),

  addChatMessage: (message) => set((state) => ({ chat: [...state.chat, message] })),

  /** Reactions self-clear after a few seconds — the UI is a transient animation, not a log. */
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
