package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReserveItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) Integer quantity
) {
}
