package com.taut0logy.jmeet.room;

import java.util.List;

public record ParticipantSnapshot(String identity, String name, List<TrackSnapshot> tracks) {
}
