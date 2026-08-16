package com.taut0logy.jmeet.room;

import java.time.Instant;
import java.util.List;

public record RoomSnapshot(String sessionId, String meetingId, String title, boolean locked, String waitingRoom,
        boolean muteOnEntry, boolean cameraOffOnEntry, boolean screenShareEnabled, int screenShareMaxConcurrent,
        List<ParticipantView> participants, List<ChatMessageView> recentChat,
        List<PendingAdmissionView> pendingAdmissions, boolean recordingActive, Instant recordingStartedAt,
        String recordingStartedBy, long rev) {

    public record PendingAdmissionView(String peerId, String displayName) {
    }
}
