package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ecommerce.order.domain.OrderStatus;

import java.time.Instant;

@Schema(description = "Registro de una transición de estado del pedido")
public record OrderHistoryItem(
        @Schema(description = "Estado de origen (null para la creación)", example = "PENDING")
        OrderStatus fromStatus,
        @Schema(description = "Estado de destino", example = "CONFIRMED")
        OrderStatus toStatus,
        @Schema(description = "Motivo de la transición", example = "Stock reserved")
        String reason,
        @Schema(description = "Trace id con el que se ejecutó la transición", example = "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f")
        String traceId,
        @Schema(description = "Fecha de la transición (ISO-8601 UTC)", example = "2026-10-22T12:00:01Z")
        Instant changedAt
) {
}
