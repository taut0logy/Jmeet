package com.taut0logy.jmeet.auth;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank String token) {
}
