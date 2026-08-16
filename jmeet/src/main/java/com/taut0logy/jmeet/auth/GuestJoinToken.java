package com.taut0logy.jmeet.auth;

public record GuestJoinToken(String meetingCode, String displayName, String guestId, String userId, String role,
        boolean requiresApproval) {
}
