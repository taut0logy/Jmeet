'use client';

import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { ChatPanel } from './chat-panel';
import { ParticipantsPanel } from './participants-panel';
import { WaitingPanel } from './waiting-panel';
import { SettingsPanel } from './settings-panel';

// Milestone A2 (spec §6.4). Chat | Participants | Waiting room — the
// waiting tab only exists for hosts/cohosts.
export function SidePanel({ activeTab, onTabChange, chat, peers, waiting, self, flags, actions }) {
  const isHostOrCohost = self?.role === 'HOST' || self?.role === 'COHOST';

  return (
    <aside className="flex w-80 flex-col border-l border-white/10 bg-neutral-950" data-testid="side-panel">
      <Tabs value={activeTab} onValueChange={onTabChange} className="flex h-full flex-col">
        <TabsList className="m-2">
          <TabsTrigger value="chat">Chat</TabsTrigger>
          <TabsTrigger value="participants">People</TabsTrigger>
          {isHostOrCohost ? (
            <TabsTrigger value="waiting" data-testid="waiting-tab">
              Waiting{waiting.length > 0 ? ` (${waiting.length})` : ''}
            </TabsTrigger>
          ) : null}
          {isHostOrCohost ? (
            <TabsTrigger value="settings" data-testid="settings-tab">
              Settings
            </TabsTrigger>
          ) : null}
        </TabsList>
        <TabsContent value="chat" className="flex-1 overflow-hidden">
          <ChatPanel chat={chat} selfPeerId={self?.peerId} allowChat={flags.allowChat} onSend={actions.sendChat} />
        </TabsContent>
        <TabsContent value="participants" className="flex-1 overflow-hidden">
          <ParticipantsPanel
            peers={peers}
            selfPeerId={self?.peerId}
            selfRole={self?.role}
            onMute={actions.muteParticipant}
            onRemove={actions.removeParticipant}
            onSetRole={actions.setParticipantRole}
            onMuteAll={actions.muteAllParticipants}
          />
        </TabsContent>
        {isHostOrCohost ? (
          <TabsContent value="waiting" className="flex-1 overflow-hidden">
            <WaitingPanel
              waiting={waiting}
              onAdmit={actions.admitPeer}
              onDeny={actions.denyPeer}
              onAdmitAll={actions.admitAllWaiting}
            />
          </TabsContent>
        ) : null}
        {isHostOrCohost ? (
          <TabsContent value="settings" className="flex-1 overflow-hidden">
            <SettingsPanel flags={flags} onSetFlag={actions.setRoomFlag} />
          </TabsContent>
        ) : null}
      </Tabs>
    </aside>
  );
}
