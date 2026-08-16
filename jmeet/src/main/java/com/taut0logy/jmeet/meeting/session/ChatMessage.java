package com.taut0logy.jmeet.meeting.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    private String id;

    private String sessionId;
    private String peerId;
    private String userId;
    private String displayName;
    private String body;
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(String id, String sessionId, String peerId, String userId, String displayName, String body) {
        this.id = id;
        this.sessionId = sessionId;
        this.peerId = peerId;
        this.userId = userId;
        this.displayName = displayName;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
