package com.boardgame.platform;

import org.springframework.http.HttpStatus;

class PlatformException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    PlatformException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    HttpStatus status() {
        return status;
    }

    String errorCode() {
        return errorCode;
    }

    static PlatformException badRequest(String code, String message) {
        return new PlatformException(HttpStatus.BAD_REQUEST, code, message);
    }

    static PlatformException unauthorized(String code, String message) {
        return new PlatformException(HttpStatus.UNAUTHORIZED, code, message);
    }

    static PlatformException forbidden(String code, String message) {
        return new PlatformException(HttpStatus.FORBIDDEN, code, message);
    }

    static PlatformException notFound(String code, String message) {
        return new PlatformException(HttpStatus.NOT_FOUND, code, message);
    }

    static PlatformException conflict(String code, String message) {
        return new PlatformException(HttpStatus.CONFLICT, code, message);
    }
}
