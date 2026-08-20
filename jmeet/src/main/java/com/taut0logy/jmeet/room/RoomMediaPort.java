package com.taut0logy.jmeet.room;

import com.taut0logy.jmeet.meeting.ParticipantRole;
import java.util.List;

/** Only this package may import room.livekit: everything else, including
 * the rest of this package, talks to the SFU only through this port.
 */
public interface RoomMediaPort {

    String mintToken(String roomName, String identity, String displayName, ParticipantRole role, TokenMetadata metadata);

    void updateParticipantMetadata(String roomName, String identity, TokenMetadata metadata);

    void muteTrack(String roomName, String identity, String trackSid, boolean mute);

    void removeParticipant(String roomName, String identity);

    void deleteRoom(String roomName);

    List<ParticipantSnapshot> listParticipants(String roomName);
}
