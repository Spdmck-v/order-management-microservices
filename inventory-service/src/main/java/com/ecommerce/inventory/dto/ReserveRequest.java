package com.ecommerce.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReserveRequest(
        @NotNull UUID orderId,
        @NotEmpty @Valid List<ReserveItemRequest> items
) {
}
