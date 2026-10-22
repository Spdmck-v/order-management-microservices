package com.ecommerce.inventory.dto;

import java.util.List;
import java.util.UUID;

public record StockOperationResponse(
        UUID orderId,
        List<OperationItemResponse> items
) {
}
