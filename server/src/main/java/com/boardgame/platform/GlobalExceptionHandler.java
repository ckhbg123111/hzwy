package com.boardgame.platform;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    ResponseEntity<ErrorResponse> handlePlatformException(PlatformException exception, HttpServletRequest request) {
        logger.warn(
                "platform exception method={} path={} status={} code={} message={}",
                request.getMethod(),
                requestPath(request),
                exception.status().value(),
                exception.errorCode(),
                exception.getMessage(),
                exception);
        return ResponseEntity.status(exception.status())
                .body(new ErrorResponse(
                        Instant.now(),
                        exception.status().value(),
                        exception.status().getReasonPhrase(),
                        exception.errorCode(),
                        exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> handleValidationFailure(Exception exception, HttpServletRequest request) {
        logger.warn(
                "validation failure method={} path={} status={} message={}",
                request.getMethod(),
                requestPath(request),
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "VALIDATION_ERROR",
                        exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnknownFailure(Exception exception, HttpServletRequest request) {
        logger.error(
                "unexpected failure method={} path={} status={} message={}",
                request.getMethod(),
                requestPath(request),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                exception.getMessage(),
                exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        "INTERNAL_ERROR",
                        exception.getMessage()));
    }

    private static String requestPath(HttpServletRequest request) {
        if (request.getQueryString() == null || request.getQueryString().isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + request.getQueryString();
    }

    record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String code,
            String message) {
    }
}
