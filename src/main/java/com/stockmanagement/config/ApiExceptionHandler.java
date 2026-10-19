package com.stockmanagement.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String reason = exception.getReason();
        String code = switch (status) {
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "Security not found".equals(reason) ? "SECURITY_NOT_FOUND" : "WATCHLIST_ITEM_NOT_FOUND";
            case CONFLICT -> "Email already exists".equals(reason) ? "EMAIL_ALREADY_EXISTS" : "WATCHLIST_ALREADY_EXISTS";
            default -> "INVALID_REQUEST";
        };
        return ResponseEntity.status(status).body(new ErrorResponse(new Error(code, reason, requestId())));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                new Error("INVALID_REQUEST", "Invalid request", requestId())));
    }

    private static String requestId() { return "req-" + UUID.randomUUID(); }

    public record ErrorResponse(Error error) { }
    public record Error(String code, String message, String requestId) { }
}
