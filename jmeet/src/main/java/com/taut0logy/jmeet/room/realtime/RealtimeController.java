package com.taut0logy.jmeet.room.realtime;

import com.taut0logy.jmeet.room.ChatSendRequest;
import com.taut0logy.jmeet.room.RaiseHandRequest;
import com.taut0logy.jmeet.room.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/** §12.1: durable participant-facing events (chat, raise hand) — STOMP over the RabbitMQ relay. */
@Controller
public class RealtimeController {

    private final RoomService roomService;

    public RealtimeController(RoomService roomService) {
        this.roomService = roomService;
    }

    @MessageMapping("/room/{sessionId}/chat")
    public void chat(@DestinationVariable String sessionId, ChatSendRequest request) {
        roomService.sendChat(sessionId, request.peerId(), request.body());
    }

    @MessageMapping("/room/{sessionId}/hand")
    public void raiseHand(@DestinationVariable String sessionId, RaiseHandRequest request) {
        roomService.raiseHand(sessionId, request.peerId(), request.raised());
    }
}
