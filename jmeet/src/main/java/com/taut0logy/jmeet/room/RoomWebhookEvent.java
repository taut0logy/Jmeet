package com.taut0logy.jmeet.room;

/** SFU-agnostic translation of a LiveKit webhook payload — the rest of the app never sees a
 * LiveKit protobuf type. */
public record RoomWebhookEvent(String type, String roomName, String participantIdentity) {
}
