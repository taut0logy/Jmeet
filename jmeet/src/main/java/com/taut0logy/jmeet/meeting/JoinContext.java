package com.taut0logy.jmeet.meeting;

import com.taut0logy.jmeet.meeting.member.MemberRole;

public record JoinContext(MeetingStatus status, boolean locked, MeetingAccess access, boolean allowGuests,
        WaitingRoomPolicy waitingRoom, boolean hasSession, boolean isOwner, MemberRole memberRole,
        String guestDisplayName) {

    public boolean isGuest() {
        return !hasSession;
    }
}
