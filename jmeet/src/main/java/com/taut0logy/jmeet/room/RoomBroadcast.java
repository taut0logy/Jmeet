package com.taut0logy.jmeet.room;

/** Generic STOMP envelope: `type` discriminates what `data` holds on the client. */
public record RoomBroadcast(String type, long rev, Object data) {
}
