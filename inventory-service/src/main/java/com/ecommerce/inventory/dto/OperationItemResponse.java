package com.ecommerce.inventory.dto;

import java.util.UUID;

public record OperationItemResponse(
        UUID productId,
        Integer quantity,
        String status
) {
}
