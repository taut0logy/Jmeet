package com.taut0logy.jmeet.room;

/**
 * token/participant metadata shape, JSON-serialized by the room.livekit
 * adapter.
 */
public record TokenMetadata(String role, String userId, String guestId, String avatarUrl) {
}
