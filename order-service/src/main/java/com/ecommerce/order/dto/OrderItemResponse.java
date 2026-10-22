package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Item de un pedido")
public record OrderItemResponse(
        @Schema(description = "Identificador del producto en Inventory Service", example = "11111111-1111-4111-8b2d-111111111111")
        UUID productId,
        @Schema(description = "Cantidad solicitada del producto", example = "2")
        Integer quantity,
        @Schema(description = "Precio unitario aplicado", example = "89.90")
        BigDecimal unitPrice
) {
}
