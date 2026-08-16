package com.taut0logy.jmeet.recording;

import java.util.Optional;

/** Same boundary as room.RoomMediaPort: the only thing this package knows about LiveKit Egress
 * is this interface. room.livekit provides the implementation. */
public interface EgressPort {

    String startRoomCompositeEgress(String roomName, String storageKey);

    void stopEgress(String egressId);

    Optional<EgressStatusSnapshot> getEgress(String egressId);
}
