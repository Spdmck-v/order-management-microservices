package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Artículo a reservar")
public record ReserveItemRequest(
        @Schema(description = "Identificador del producto", requiredMode = RequiredMode.REQUIRED,
                example = "11111111-1111-4111-8b2d-111111111111")
        @NotNull UUID productId,
        @Schema(description = "Cantidad a reservar (>= 1)", requiredMode = RequiredMode.REQUIRED, example = "2")
        @NotNull @Min(1) Integer quantity
) {
}
