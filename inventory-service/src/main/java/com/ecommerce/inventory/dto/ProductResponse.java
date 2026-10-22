package com.ecommerce.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        boolean active,
        Integer availableQuantity,
        Integer reservedQuantity
) {
}
