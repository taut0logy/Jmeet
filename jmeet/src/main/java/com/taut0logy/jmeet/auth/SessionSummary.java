package com.taut0logy.jmeet.auth;

public record SessionSummary(String id, boolean current, String createdAt, String lastAccessedAt) {
}
