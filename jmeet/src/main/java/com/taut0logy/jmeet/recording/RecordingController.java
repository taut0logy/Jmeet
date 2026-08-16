package com.taut0logy.jmeet.recording;

import com.taut0logy.jmeet.auth.AuthPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecordingController {

    private final RecordingService recordingService;

    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/api/rooms/{id}/recording")
    @ResponseStatus(HttpStatus.CREATED)
    public RecordingResponse start(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return recordingService.start(principal.userId(), id);
    }

    @DeleteMapping("/api/rooms/{id}/recording")
    public RecordingResponse stop(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return recordingService.stop(principal.userId(), id);
    }

    @GetMapping("/api/meetings/{id}/recordings")
    public List<RecordingResponse> list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return recordingService.listForMeeting(principal.userId(), id);
    }
}
