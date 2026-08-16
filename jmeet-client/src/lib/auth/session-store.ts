import { create } from 'zustand';
import { api } from '@/lib/api/client';

export type SessionUser = {
  id: string;
  name: string;
  email?: string;
  image?: string;
  hasPassword?: boolean;
  [key: string]: unknown;
};
export type Session = { user: SessionUser; [key: string]: unknown };

type Status = 'idle' | 'loading' | 'authenticated' | 'unauthenticated';

type SessionState = {
  session: Session | null;
  status: Status;
  refetch: () => Promise<void>;
  setSession: (session: Session | null) => void;
};

let inflight: Promise<void> | null = null;

export const useSessionStore = create<SessionState>((set) => ({
  session: null,
  status: 'idle',
  setSession: (session) => set({ session, status: session ? 'authenticated' : 'unauthenticated' }),
  refetch: () => {
    if (inflight) return inflight;
    set({ status: 'loading' });
    inflight = api
      .get('/users/me')
      .then((res) => set({ session: res as Session, status: 'authenticated' }))
      .catch(() => set({ session: null, status: 'unauthenticated' }))
      .finally(() => {
        inflight = null;
      });
    return inflight;
  },
}));
