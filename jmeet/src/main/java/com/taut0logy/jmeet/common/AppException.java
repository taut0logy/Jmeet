package com.taut0logy.jmeet.common;

public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final Object details;

    public AppException(ErrorCode code) {
        this(code, code.name(), null);
    }

    public AppException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public AppException(ErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Object details() {
        return details;
    }
}
