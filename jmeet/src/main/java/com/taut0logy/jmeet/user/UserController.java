package com.taut0logy.jmeet.user;

import com.taut0logy.jmeet.auth.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.me(principal.userId());
    }

    @PatchMapping("/api/users/me")
    public MeResponse updateProfile(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(principal.userId(), request);
    }

    @PostMapping("/api/users/me/avatar")
    public MeResponse updateAvatar(@AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        return userService.updateAvatar(principal.userId(), file);
    }
}
