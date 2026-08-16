'use client';

import { FiX } from 'react-icons/fi';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { ChatPanel } from './chat-panel';
import { ParticipantsPanel } from './participants-panel';
import { WaitingPanel } from './waiting-panel';
import { SettingsPanel } from './settings-panel';

export function SidePanel({ activeTab, onTabChange, chat, peers, waiting, self, flags, actions }) {
  const isHostOrCohost = self?.role === 'HOST' || self?.role === 'COHOST';

  return (
    <aside
      className="fixed inset-0 z-40 flex flex-col bg-neutral-950 sm:static sm:inset-auto sm:z-auto sm:w-80 sm:border-l sm:border-white/10"
      data-testid="side-panel"
    >
      <Tabs value={activeTab} onValueChange={onTabChange} className="flex h-full flex-col">
        <div className="flex items-center gap-2 pr-2 pt-2 sm:pr-0 sm:pt-0">
          <TabsList className="m-2 flex-1 sm:flex-none">
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
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="Close panel"
            className="shrink-0 sm:hidden"
            onClick={() => onTabChange(activeTab)}
          >
            <FiX />
          </Button>
        </div>
        <TabsContent value="chat" className="flex-1 overflow-hidden">
          <ChatPanel chat={chat} selfPeerId={self?.peerId} onSend={actions.sendChat} />
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
