package com.taut0logy.jmeet.room.livekit;

import com.taut0logy.jmeet.config.LiveKitProperties;
import com.taut0logy.jmeet.meeting.ParticipantRole;
import com.taut0logy.jmeet.room.TokenMetadata;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.Hidden;
import io.livekit.server.Recorder;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** The only place an AccessToken is built. */
@Component
class TokenService {

    private final LiveKitProperties properties;
    private final ObjectMapper json;

    TokenService(LiveKitProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    String mint(String roomName, String identity, String displayName, ParticipantRole role, TokenMetadata metadata) {
        AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(identity);
        token.setName(displayName);
        token.setMetadata(json.writeValueAsString(metadata));
        token.setTtl(TimeUnit.HOURS.toMillis(6));

        boolean admin = role == ParticipantRole.HOST || role == ParticipantRole.COHOST;
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(true),
                new CanSubscribe(true),
                new CanPublishData(true),
                new RoomAdmin(admin));
        return token.toJwt();
    }

    /** Short-lived, subscribe-only, hidden from other participants, Egress's own view
     * into the room, not a participant. */
    String mintRecorderToken(String roomName, String identity) {
        AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(identity);
        token.setTtl(TimeUnit.MINUTES.toMillis(15));
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(false),
                new CanSubscribe(true),
                new Hidden(true),
                new Recorder(true));
        return token.toJwt();
    }
}
