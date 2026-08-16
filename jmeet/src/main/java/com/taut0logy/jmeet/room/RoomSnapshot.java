package com.taut0logy.jmeet.room;

import java.util.List;

public record RoomSnapshot(String sessionId, String meetingId, String title, boolean locked, boolean muteOnEntry,
        boolean cameraOffOnEntry, boolean screenShareEnabled, int screenShareMaxConcurrent,
        List<ParticipantView> participants, List<ChatMessageView> recentChat, long rev) {
}
