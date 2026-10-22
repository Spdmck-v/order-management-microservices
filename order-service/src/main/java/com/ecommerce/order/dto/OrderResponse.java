package com.ecommerce.order.dto;

import com.ecommerce.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Pedido con sus items")
public record OrderResponse(
        @Schema(description = "Identificador único del pedido", example = "0d6d5a4a-37ad-4e14-a089-64e08a8cd2b2")
        UUID id,
        @Schema(description = "Identificador del cliente", example = "customer-001")
        String customerId,
        @Schema(description = "Estado del pedido", allowableValues = {"PENDING", "CONFIRMED", "CANCELLED", "FAILED"},
                example = "CONFIRMED")
        OrderStatus status,
        @Schema(description = "Monto total del pedido", example = "214.30")
        BigDecimal totalAmount,
        @Schema(description = "Fecha de creación (ISO-8601 UTC)", example = "2026-10-22T12:00:00Z")
        Instant createdAt,
        @Schema(description = "Fecha de la última actualización (ISO-8601 UTC)", example = "2026-10-22T12:00:01Z")
        Instant updatedAt,
        @Schema(description = "Items del pedido")
        List<OrderItemResponse> items
) {
}
