'use client';

import { useCallback, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Room, RoomEvent } from 'livekit-client';
import type { Client } from '@stomp/stompjs';
import { createStompClient, subscribeJson, sendJson } from '@/lib/signaling/stomp-client';
import { api, ApiError } from '@/lib/api/client';
import { useMeetingStore, type RoomSnapshot, type PeerMeta, type State } from '@/stores/meetingStore';
import { playSound } from '@/lib/audio/sounds';

const LIVEKIT_URL = process.env.NEXT_PUBLIC_LIVEKIT_URL ?? 'ws://localhost:7880';

type Broadcast = { type: string; rev: number; data: any };

function reduceBroadcast(msg: Broadcast, state: State) {
  switch (msg.type) {
    case 'admission-requested':
      return { waiting: [...state.waiting, { peerId: msg.data.peerId, displayName: msg.data.displayName }] };
    case 'joined': {
      const peer = msg.data as PeerMeta;
      return {
        peers: { ...state.peers, [peer.peerId]: peer },
        waiting: state.waiting.filter((w) => w.peerId !== peer.peerId),
      };
    }
    case 'left': {
      const peers = { ...state.peers };
      delete peers[msg.data.peerId];
      return { peers };
    }
    case 'role-changed': {
      const peer = state.peers[msg.data.peerId];
      if (!peer) return {};
      return { peers: { ...state.peers, [msg.data.peerId]: { ...peer, role: msg.data.role } } };
    }
    case 'hand-raised': {
      const peer = state.peers[msg.data.peerId];
      if (!peer) return {};
      return { peers: { ...state.peers, [msg.data.peerId]: { ...peer, handRaised: msg.data.raised } } };
    }
    case 'flags-changed':
      return {
        flags: {
          ...state.flags,
          locked: msg.data.locked,
          screenShareEnabled: msg.data.screenShareEnabled,
          waitingRoom: msg.data.waitingRoom ?? state.flags.waitingRoom,
        },
      };
    case 'chat':
      return { chat: [...state.chat, { ...msg.data, id: `${msg.data.peerId}:${msg.data.createdAt}` }] };
    case 'recording-state':
      return { recording: { active: msg.data.active, startedAt: msg.data.startedAt || null, startedBy: msg.data.startedBy || null } };
    case 'duration-warning':
      return { durationWarning: { endsAt: msg.data.endsAt } };
    default:
      return {};
  }
}

function reactToBroadcast(msg: Broadcast, selfPeerId: string) {
  switch (msg.type) {
    case 'room-ended':
      useMeetingStore.getState().setConnectionState('ended', 'The meeting has ended.');
      break;
    case 'joined':
      if (msg.data.peerId !== selfPeerId) playSound('join');
      break;
    case 'left':
      if (msg.data.peerId !== selfPeerId) playSound('leave');
      break;
    case 'admission-requested':
      playSound('waitingRoomRequest');
      break;
    case 'chat':
      if (msg.data.peerId !== selfPeerId) playSound('chat');
      break;
    case 'hand-raised':
      if (msg.data.raised && msg.data.peerId !== selfPeerId) playSound('handRaised');
      break;
    case 'recording-state':
      playSound(msg.data.active ? 'recordingStart' : 'recordingStop');
      break;
  }
}

export function useRoomConnection(code: string) {
  const router = useRouter();
  const roomRef = useRef<Room | null>(null);
  if (!roomRef.current) roomRef.current = new Room({ adaptiveStream: true, dynacast: true });
  const room = roomRef.current;

  const stompRef = useRef<Client | null>(null);
  const sessionIdRef = useRef<string | null>(null);
  const selfPeerIdRef = useRef<string | null>(null);
  const startedForCodeRef = useRef<string | null>(null);
  const torndownRef = useRef(false);
  const cleanupTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (cleanupTimerRef.current && startedForCodeRef.current === code) {
      clearTimeout(cleanupTimerRef.current);
      cleanupTimerRef.current = null;
    } else if (startedForCodeRef.current !== code) {
      startedForCodeRef.current = code;
      torndownRef.current = false;
      setup();
    }

    return () => {
      cleanupTimerRef.current = setTimeout(() => {
        cleanupTimerRef.current = null;
        startedForCodeRef.current = null;
        torndownRef.current = true;
        stompRef.current?.deactivate();
        room.disconnect();
        useMeetingStore.getState().reset();
      }, 0);
    };

    function setup() {
      const stomp = createStompClient();
      stompRef.current = stomp;

      async function resync() {
        const sessionId = sessionIdRef.current;
        const peerId = selfPeerIdRef.current;
        if (!sessionId || !peerId) return;
        try {
          const snapshot: RoomSnapshot = await api.get(`/rooms/${sessionId}/sync?peerId=${peerId}`);
          useMeetingStore.getState().applySnapshot(snapshot, peerId);
          if (useMeetingStore.getState().connectionState === 'reconnecting') {
            useMeetingStore.getState().setConnectionState('connected');
          }
        } catch {}
      }

      stomp.onConnect = () => {
        const sessionId = sessionIdRef.current;
        const peerId = selfPeerIdRef.current;
        if (!sessionId || !peerId) return;

        subscribeJson<Broadcast>(stomp, `/topic/room.${sessionId}`, (msg) => {
          if (msg.type === 'reaction') {
            useMeetingStore.getState().addReaction({ id: `${msg.data.peerId}:${msg.rev}`, peerId: msg.data.peerId, emoji: msg.data.emoji });
            return;
          }
          const applied = useMeetingStore.getState().applyDelta(msg.rev, (state) => reduceBroadcast(msg, state));
          if (!applied) resync();
          reactToBroadcast(msg, peerId);
        });

        subscribeJson<{ type: string; data: { status: string; token?: string } }>(
          stomp,
          `/topic/room.${sessionId}.peer.${peerId}`,
          (msg) => {
            if (msg.type !== 'admission-decided') return;
            if (msg.data.status === 'ADMITTED' && msg.data.token) {
              playSound('admitted');
              afterAdmitted(msg.data.token, null);
            } else {
              playSound('denied');
              useMeetingStore.getState().setConnectionState('error', 'Your request to join was denied.');
            }
          },
        );

        if (useMeetingStore.getState().connectionState === 'reconnecting') resync();
      };

      stomp.onWebSocketClose = () => {
        if (useMeetingStore.getState().connectionState === 'connected') {
          useMeetingStore.getState().setConnectionState('reconnecting');
        }
      };

      room.on(RoomEvent.Disconnected, () => {
        if (torndownRef.current) return;
        useMeetingStore.getState().setConnectionState('ended', 'The meeting has ended.');
      });

      async function afterAdmitted(livekitToken: string, snapshot: RoomSnapshot | null) {
        if (torndownRef.current) return;
        const sessionId = sessionIdRef.current!;
        const peerId = selfPeerIdRef.current!;

        try {
          await room.connect(LIVEKIT_URL, livekitToken);
        } catch {
          if (!torndownRef.current) {
            useMeetingStore.getState().setConnectionState('error', 'Could not connect to the meeting media server.');
          }
          return;
        }
        if (torndownRef.current) {
          room.disconnect();
          return;
        }

        const resolvedSnapshot = snapshot ?? (await api.get(`/rooms/${sessionId}/sync?peerId=${peerId}`));
        useMeetingStore.getState().applySnapshot(resolvedSnapshot, peerId);
        useMeetingStore.getState().setConnectionState('connected');
        playSound('join');
      }

      async function join() {
        useMeetingStore.getState().setConnectionState('connecting');
        const stored = sessionStorage.getItem(`meet:join:${code}`);
        if (!stored) {
          router.replace(`/j/${code}`);
          return;
        }
        const { token: joinToken } = JSON.parse(stored);

        let joinRes;
        try {
          joinRes = await api.post(`/rooms/${code}/join`, { joinToken });
        } catch (err) {
          if (!torndownRef.current) {
            useMeetingStore.getState().setConnectionState(
              'error',
              err instanceof ApiError ? err.message : 'Could not join this meeting.',
            );
          }
          return;
        }
        if (torndownRef.current) return;

        sessionIdRef.current = joinRes.snapshot.sessionId;
        selfPeerIdRef.current = joinRes.peerId;
        stomp.activate();

        if (joinRes.status === 'PENDING') {
          useMeetingStore.getState().setConnectionState('waiting');
          return;
        }
        await afterAdmitted(joinRes.token, joinRes.snapshot);
      }

      join();
    }
  }, [code, room, router]);

  const sessionId = () => sessionIdRef.current;
  const selfPeerId = () => selfPeerIdRef.current;

  const toggleMic = useCallback(() => room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled), [room]);
  const toggleCamera = useCallback(() => room.localParticipant.setCameraEnabled(!room.localParticipant.isCameraEnabled), [room]);
  const startScreenShare = useCallback(() => room.localParticipant.setScreenShareEnabled(true), [room]);
  const stopScreenShare = useCallback(() => room.localParticipant.setScreenShareEnabled(false), [room]);
  const switchCamera = useCallback((deviceId: string) => room.switchActiveDevice('videoinput', deviceId), [room]);
  const switchMic = useCallback((deviceId: string) => room.switchActiveDevice('audioinput', deviceId), [room]);
  const switchSpeaker = useCallback(
    async (deviceId: string) => {
      await room.switchActiveDevice('audiooutput', deviceId);
      useMeetingStore.getState().setAudioOutputId(deviceId);
    },
    [room],
  );

  const toggleHand = useCallback(() => {
    const peerId = selfPeerId();
    if (!peerId || !sessionId() || !stompRef.current) return;
    const raised = !(useMeetingStore.getState().peers[peerId]?.handRaised ?? false);
    sendJson(stompRef.current, `/app/room/${sessionId()}/hand`, { peerId, raised });
  }, []);

  const sendChat = useCallback((body: string) => {
    const peerId = selfPeerId();
    if (!peerId || !sessionId() || !stompRef.current) return;
    sendJson(stompRef.current, `/app/room/${sessionId()}/chat`, { peerId, body });
  }, []);

  const sendReaction = useCallback((emoji: string) => {
    const peerId = selfPeerId();
    if (!peerId || !sessionId() || !stompRef.current) return;
    sendJson(stompRef.current, `/app/room/${sessionId()}/reaction`, { peerId, emoji });
    useMeetingStore.getState().addReaction({ id: `local:${Date.now()}:${Math.random()}`, peerId, emoji });
  }, []);

  const admitPeer = useCallback((peerId: string) => api.post(`/rooms/${sessionId()}/admissions`, { peerId, approve: true }), []);
  const denyPeer = useCallback((peerId: string) => api.post(`/rooms/${sessionId()}/admissions`, { peerId, approve: false }), []);
  const admitAllWaiting = useCallback(() => api.post(`/rooms/${sessionId()}/admissions`, { admitAll: true }), []);
  const muteParticipant = useCallback(
    (peerId: string) => api.post(`/rooms/${sessionId()}/participants/${peerId}/mute`, { mute: true }),
    [],
  );
  const muteAllParticipants = useCallback(() => api.post(`/rooms/${sessionId()}/flags`, { muteAll: true }), []);
  const removeParticipant = useCallback((peerId: string) => api.delete(`/rooms/${sessionId()}/participants/${peerId}`), []);
  const setParticipantRole = useCallback(
    (peerId: string, role: string) => api.post(`/rooms/${sessionId()}/participants/${peerId}/role`, { role }),
    [],
  );
  const endMeetingForAll = useCallback(() => api.delete(`/rooms/${sessionId()}`), []);
  const startRecording = useCallback(() => api.post(`/rooms/${sessionId()}/recording`), []);
  const stopRecording = useCallback(() => api.delete(`/rooms/${sessionId()}/recording`), []);
  const setRoomFlag = useCallback((key: string, value: unknown) => api.post(`/rooms/${sessionId()}/flags`, { [key]: value }), []);
  const toggleIncomingVideo = useCallback(() => {
    useMeetingStore.getState().setIncomingVideoOff(!useMeetingStore.getState().incomingVideoOff);
  }, []);

  const leave = useCallback(async () => {
    playSound('leave');
    torndownRef.current = true;
    stompRef.current?.deactivate();
    await room.disconnect();
    sessionStorage.removeItem(`meet:join:${code}`);
    useMeetingStore.getState().reset();
  }, [room, code]);

  return {
    room,
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
  };
}
