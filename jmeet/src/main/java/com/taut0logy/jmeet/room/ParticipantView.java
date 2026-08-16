package com.taut0logy.jmeet.room;

import com.taut0logy.jmeet.meeting.ParticipantRole;
import com.taut0logy.jmeet.meeting.session.Participation;

public record ParticipantView(String peerId, String displayName, ParticipantRole role, boolean handRaised) {

    public static ParticipantView from(Participation participation, boolean handRaised) {
        return new ParticipantView(participation.getPeerId(), participation.getDisplayName(), participation.getRole(),
                handRaised);
    }
}
