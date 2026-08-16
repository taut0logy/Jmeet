package com.taut0logy.jmeet.room;

public record RoomJoinResponse(String status, String peerId, String token, RoomSnapshot snapshot) {
}
