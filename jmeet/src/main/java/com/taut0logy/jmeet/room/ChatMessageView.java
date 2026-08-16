package com.taut0logy.jmeet.room;

import com.taut0logy.jmeet.meeting.session.ChatMessage;
import java.time.Instant;

public record ChatMessageView(String peerId, String displayName, String body, Instant createdAt) {

    public static ChatMessageView from(ChatMessage message) {
        return new ChatMessageView(message.getPeerId(), message.getDisplayName(), message.getBody(), message.getCreatedAt());
    }
}
