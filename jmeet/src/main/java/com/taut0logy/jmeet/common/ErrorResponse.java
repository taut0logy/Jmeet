package com.taut0logy.jmeet.common;

public record ErrorResponse(String error, String code, Object details) {

    public ErrorResponse(String error, String code) {
        this(error, code, null);
    }
}
