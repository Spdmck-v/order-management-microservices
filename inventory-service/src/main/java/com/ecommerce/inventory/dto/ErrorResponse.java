package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

import java.time.Instant;

@Schema(description = "Formato único de error de todos los servicios: timestamp, status, code, message y traceId (X-Trace-Id)")
public record ErrorResponse(
        @Schema(description = "Momento del error en formato ISO-8601 UTC", requiredMode = RequiredMode.REQUIRED, example = "2026-10-22T12:00:00Z")
        Instant timestamp,
        @Schema(description = "Código HTTP numérico", requiredMode = RequiredMode.REQUIRED, example = "404")
        int status,
        @Schema(description = "Código de negocio del error", requiredMode = RequiredMode.REQUIRED,
                example = "PRODUCT_NOT_FOUND", allowableValues = {
                "PRODUCT_NOT_FOUND", "INVALID_REQUEST", "STOCK_INSUFFICIENT", "INVALID_STATE_TRANSITION",
                "INVALID_TOKEN", "INTERNAL_ERROR"})
        String code,
        @Schema(description = "Mensaje legible del error", requiredMode = RequiredMode.REQUIRED,
                example = "Product 11111111-1111-4111-8b2d-111111111111 not found")
        String message,
        @Schema(description = "Trace id propagado en el header X-Trace-Id", requiredMode = RequiredMode.REQUIRED,
                example = "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f")
        String traceId
) {

    public static ErrorResponse of(org.springframework.http.HttpStatus httpStatus, String code, String message, String traceId) {
        return new ErrorResponse(Instant.now(), httpStatus.value(), code, message, traceId);
    }
}
