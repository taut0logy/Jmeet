'use client';

import { useEffect, useRef, useState } from 'react';
import { FiSend } from 'react-icons/fi';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export function ChatPanel({ chat, selfPeerId, onSend }) {
  const [text, setText] = useState('');
  const listRef = useRef(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [chat.length]);

  function handleSubmit(e) {
    e.preventDefault();
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setText('');
  }

  return (
    <div className="flex h-full flex-col">
      <div ref={listRef} className="flex-1 space-y-3 overflow-y-auto p-3" data-testid="chat-messages">
        {chat.length === 0 ? (
          <p className="pt-8 text-center text-sm text-neutral-500">No messages yet.</p>
        ) : (
          chat.map((m) => (
            <div key={m.id} data-testid="chat-message">
              <div className="flex items-baseline gap-2">
                <span className="text-xs font-medium text-neutral-300">
                  {m.peerId === selfPeerId ? 'You' : m.displayName}
                </span>
                <span className="text-[10px] text-neutral-500">
                  {new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
              <p className="text-sm text-neutral-100 break-words">{m.body}</p>
            </div>
          ))
        )}
      </div>
      <form onSubmit={handleSubmit} className="flex gap-2 border-t border-white/10 p-3">
        <Input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Send a message"
          aria-label="Chat message"
          maxLength={2000}
        />
        <Button type="submit" size="icon" aria-label="Send message" disabled={!text.trim()}>
          <FiSend />
        </Button>
      </form>
    </div>
  );
}
