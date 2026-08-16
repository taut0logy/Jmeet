package com.taut0logy.jmeet.meeting.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meeting_member")
public class MeetingMember {

    @Id
    private String id;

    private String meetingId;
    private String userId;

    @Column(columnDefinition = "citext")
    private String email;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    private Instant createdAt;

    protected MeetingMember() {
    }

    public MeetingMember(String id, String meetingId, String userId, String email, MemberRole role) {
        this.id = id;
        this.meetingId = meetingId;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public MemberRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
