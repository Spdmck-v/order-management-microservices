package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Artículo reservado o liberado")
public record OperationItemResponse(
        @Schema(description = "Identificador del producto", example = "11111111-1111-4111-8b2d-111111111111")
        UUID productId,
        @Schema(description = "Cantidad de reserva o liberación", example = "2")
        Integer quantity,
        @Schema(description = "Resultado de la operación sobre el artículo",
                allowableValues = {"RESERVED", "ALREADY_RESERVED", "RELEASED", "NOT_RESERVED"}, example = "RESERVED")
        String status
) {
}
