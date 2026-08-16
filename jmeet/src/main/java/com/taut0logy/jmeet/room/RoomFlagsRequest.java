package com.taut0logy.jmeet.room;

public record RoomFlagsRequest(Boolean locked, Boolean muteAll, Boolean screenShareEnabled, String waitingRoom) {
}
