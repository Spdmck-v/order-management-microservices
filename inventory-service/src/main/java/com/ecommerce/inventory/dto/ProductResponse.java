package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Producto del catálogo con su stock")
public record ProductResponse(
        @Schema(description = "Identificador único del producto", example = "11111111-1111-4111-8b2d-111111111111")
        UUID id,
        @Schema(description = "SKU del producto", example = "SKU-001")
        String sku,
        @Schema(description = "Nombre del producto", example = "Mechanical Keyboard")
        String name,
        @Schema(description = "Precio del producto", example = "89.90")
        BigDecimal price,
        @Schema(description = "Indica si el producto está activo para la venta", example = "true")
        boolean active,
        @Schema(description = "Cantidad disponible en stock", example = "100")
        Integer availableQuantity,
        @Schema(description = "Cantidad reservada para pedidos", example = "0")
        Integer reservedQuantity
) {
}
