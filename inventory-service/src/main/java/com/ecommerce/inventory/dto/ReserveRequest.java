package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Solicitud de reserva de stock, idempotente por orderId")
public record ReserveRequest(
        @Schema(description = "Identificador del pedido que reserva el stock", requiredMode = RequiredMode.REQUIRED,
                example = "f96c3ab4-3f6a-4b3e-a2a0-3dfb1d914a93")
        @NotNull UUID orderId,
        @Schema(description = "Artículos a reservar (al menos uno)", requiredMode = RequiredMode.REQUIRED)
        @NotEmpty @Valid List<ReserveItemRequest> items
) {
}
