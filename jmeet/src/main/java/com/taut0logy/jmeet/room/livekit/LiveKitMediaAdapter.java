package com.taut0logy.jmeet.room.livekit;

import com.taut0logy.jmeet.meeting.ParticipantRole;
import com.taut0logy.jmeet.room.ParticipantSnapshot;
import com.taut0logy.jmeet.room.RoomMediaPort;
import com.taut0logy.jmeet.room.TokenMetadata;
import com.taut0logy.jmeet.room.TrackSnapshot;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import java.util.List;
import livekit.LivekitModels;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;
import tools.jackson.databind.ObjectMapper;

@Component
class LiveKitMediaAdapter implements RoomMediaPort {

    private final RoomServiceClient client;
    private final TokenService tokenService;
    private final ObjectMapper json;

    LiveKitMediaAdapter(RoomServiceClient client, TokenService tokenService, ObjectMapper json) {
        this.client = client;
        this.tokenService = tokenService;
        this.json = json;
    }

    @Override
    public String mintToken(String roomName, String identity, String displayName, ParticipantRole role, TokenMetadata metadata) {
        return tokenService.mint(roomName, identity, displayName, role, metadata);
    }

    @Override
    public void updateParticipantMetadata(String roomName, String identity, TokenMetadata metadata) {
        execute(client.updateParticipant(roomName, identity, null, json.writeValueAsString(metadata), null, null));
    }

    @Override
    public void muteTrack(String roomName, String identity, String trackSid, boolean mute) {
        execute(client.mutePublishedTrack(roomName, identity, trackSid, mute));
    }

    @Override
    public void removeParticipant(String roomName, String identity) {
        execute(client.removeParticipant(roomName, identity));
    }

    @Override
    public void deleteRoom(String roomName) {
        execute(client.deleteRoom(roomName));
    }

    @Override
    public List<ParticipantSnapshot> listParticipants(String roomName) {
        List<LivekitModels.ParticipantInfo> participants = execute(client.listParticipants(roomName));
        return participants.stream().map(this::toSnapshot).toList();
    }

    private ParticipantSnapshot toSnapshot(LivekitModels.ParticipantInfo participant) {
        List<TrackSnapshot> tracks = participant.getTracksList().stream()
                .map(t -> new TrackSnapshot(t.getSid(), t.getSource().name(), t.getMuted()))
                .toList();
        return new ParticipantSnapshot(participant.getIdentity(), participant.getName(), tracks);
    }

    private <T> T execute(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("LiveKit call failed: " + response.code() + " " + response.message());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("failed to reach LiveKit server", e);
        }
    }
}
