package com.taut0logy.jmeet.room.livekit;

import com.taut0logy.jmeet.recording.RecordingService;
import com.taut0logy.jmeet.room.RoomService;
import com.taut0logy.jmeet.room.RoomWebhookEvent;
import io.livekit.server.WebhookReceiver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import livekit.LivekitWebhook;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated by verifying the signed Authorization header; unsigned requests are
 * rejected before parsing. */
@RestController
class WebhookController {

    private final WebhookReceiver webhookReceiver;
    private final RoomService roomService;
    private final RecordingService recordingService;
    private final LiveKitEgressAdapter egressAdapter;

    WebhookController(WebhookReceiver webhookReceiver, RoomService roomService, RecordingService recordingService,
            LiveKitEgressAdapter egressAdapter) {
        this.webhookReceiver = webhookReceiver;
        this.roomService = roomService;
        this.recordingService = recordingService;
        this.egressAdapter = egressAdapter;
    }

    @PostMapping("/api/internal/livekit/webhook")
    ResponseEntity<Void> receive(@RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        String body = readBody(request);

        LivekitWebhook.WebhookEvent event;
        try {
            event = webhookReceiver.receive(body, authHeader);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MDC.put("livekitEvent", event.getEvent());
        try {
            if (event.getEvent().startsWith("egress_") && event.hasEgressInfo()) {
                MDC.put("egressId", event.getEgressInfo().getEgressId());
                recordingService.applyEgressStatus(event.getEgressInfo().getEgressId(), egressAdapter.toSnapshot(event.getEgressInfo()));
                return ResponseEntity.noContent().build();
            }

            roomService.handleWebhook(new RoomWebhookEvent(
                    event.getEvent(),
                    event.hasRoom() ? event.getRoom().getName() : null,
                    event.hasParticipant() ? event.getParticipant().getIdentity() : null));
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove("livekitEvent");
            MDC.remove("egressId");
        }
    }

    private String readBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
