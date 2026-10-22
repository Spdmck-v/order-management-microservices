package com.ecommerce.gateway.dto;

public record ErrorResponse(
        String timestamp,
        int status,
        String code,
        String message,
        String traceId
) {
}
