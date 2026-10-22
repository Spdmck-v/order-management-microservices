package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Resultado de una operación de reserva o liberación")
public record StockOperationResponse(
        @Schema(description = "Identificador del pedido sobre el que se ejecutó la operación",
                example = "f96c3ab4-3f6a-4b3e-a2a0-3dfb1d914a93")
        UUID orderId,
        @Schema(description = "Resultado por artículo")
        List<OperationItemResponse> items
) {
}
