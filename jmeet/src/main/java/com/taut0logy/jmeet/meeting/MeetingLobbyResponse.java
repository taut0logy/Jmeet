package com.taut0logy.jmeet.meeting;

public record MeetingLobbyResponse(String title, String hostName, MeetingStatus status, MeetingAccess access,
        WaitingRoomPolicy waitingRoom, boolean allowGuests) {
}
