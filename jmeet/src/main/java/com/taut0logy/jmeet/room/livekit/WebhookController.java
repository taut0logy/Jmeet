package com.taut0logy.jmeet.room.livekit;

import com.taut0logy.jmeet.room.RoomService;
import com.taut0logy.jmeet.room.RoomWebhookEvent;
import io.livekit.server.WebhookReceiver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import livekit.LivekitWebhook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** §11.3: authenticated by verifying the signed Authorization header; unsigned requests are
 * rejected before parsing. */
@RestController
class WebhookController {

    private final WebhookReceiver webhookReceiver;
    private final RoomService roomService;

    WebhookController(WebhookReceiver webhookReceiver, RoomService roomService) {
        this.webhookReceiver = webhookReceiver;
        this.roomService = roomService;
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

        roomService.handleWebhook(new RoomWebhookEvent(
                event.getEvent(),
                event.hasRoom() ? event.getRoom().getName() : null,
                event.hasParticipant() ? event.getParticipant().getIdentity() : null));
        return ResponseEntity.noContent().build();
    }

    private String readBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
