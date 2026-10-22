package com.ecommerce.order.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String traceId
) {

    public static ErrorResponse of(HttpStatus httpStatus, String code, String message, String traceId) {
        return new ErrorResponse(Instant.now(), httpStatus.value(), code, message, traceId);
    }
}
