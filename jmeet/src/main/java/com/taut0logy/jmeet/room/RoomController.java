package com.taut0logy.jmeet.room;

import com.taut0logy.jmeet.auth.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/api/rooms/{code}/join")
    public RoomJoinResponse join(@PathVariable String code, @RequestBody RoomJoinRequest request) {
        return roomService.join(code, request.joinToken());
    }

    @GetMapping("/api/rooms/{id}/sync")
    public RoomSnapshot sync(@PathVariable String id, @RequestParam String peerId) {
        return roomService.sync(id, peerId);
    }

    @PostMapping("/api/rooms/{id}/admissions")
    public RoomSnapshot admissions(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @RequestBody AdmissionRequest request) {
        return roomService.admit(principal.userId(), id, request);
    }

    @PostMapping("/api/rooms/{id}/participants/{peerId}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mute(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @PathVariable String peerId, @RequestBody MuteRequest request) {
        roomService.mute(principal.userId(), id, peerId, request.mute());
    }

    @PostMapping("/api/rooms/{id}/participants/{peerId}/role")
    public RoleChangeResponse role(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @PathVariable String peerId, @RequestBody RoleChangeRequest request) {
        return roomService.changeRole(principal.userId(), id, peerId, request.role());
    }

    @DeleteMapping("/api/rooms/{id}/participants/{peerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @PathVariable String peerId) {
        roomService.removeParticipant(principal.userId(), id, peerId);
    }

    @PostMapping("/api/rooms/{id}/flags")
    public RoomSnapshot flags(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
            @RequestBody RoomFlagsRequest request) {
        return roomService.flags(principal.userId(), id, request);
    }

    @DeleteMapping("/api/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        roomService.endForAll(principal.userId(), id);
    }
}
