package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        Integer quantity,
        BigDecimal unitPrice
) {
}
