package com.taut0logy.jmeet.meeting.session;

import com.taut0logy.jmeet.meeting.ParticipantRole;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "participation")
public class Participation {

    @Id
    private String id;

    private String sessionId;
    private String peerId;
    private String userId;
    private String guestId;
    private String displayName;

    @Enumerated(EnumType.STRING)
    private ParticipantRole role;

    private Instant joinedAt;
    private Instant leftAt;
    private String admittedBy;

    protected Participation() {
    }

    public Participation(String id, String sessionId, String peerId, String userId, String guestId,
            String displayName, ParticipantRole role, String admittedBy) {
        this.id = id;
        this.sessionId = sessionId;
        this.peerId = peerId;
        this.userId = userId;
        this.guestId = guestId;
        this.displayName = displayName;
        this.role = role;
        this.admittedBy = admittedBy;
        this.joinedAt = Instant.now();
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

    public String getGuestId() {
        return guestId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ParticipantRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public void setRole(ParticipantRole role) {
        this.role = role;
    }

    public void leave() {
        if (leftAt == null) this.leftAt = Instant.now();
    }
}
