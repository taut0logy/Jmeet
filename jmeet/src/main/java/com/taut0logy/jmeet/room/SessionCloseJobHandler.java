package com.taut0logy.jmeet.room;

import com.taut0logy.jmeet.job.JobHandler;
import com.taut0logy.jmeet.job.JobType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SessionCloseJobHandler implements JobHandler {

    private final RoomService roomService;
    private final ObjectMapper json;

    public SessionCloseJobHandler(RoomService roomService, ObjectMapper json) {
        this.roomService = roomService;
        this.json = json;
    }

    @Override
    public JobType type() {
        return JobType.SESSION_CLOSE;
    }

    @Override
    public void handle(String payload) {
        SessionClosePayload data = json.readValue(payload, SessionClosePayload.class);
        roomService.autoEndSession(data.sessionId());
    }
}
