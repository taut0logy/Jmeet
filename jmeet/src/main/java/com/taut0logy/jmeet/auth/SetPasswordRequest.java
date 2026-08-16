package com.taut0logy.jmeet.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(@NotBlank @Size(min = 8, max = 200) String newPassword) {
}
